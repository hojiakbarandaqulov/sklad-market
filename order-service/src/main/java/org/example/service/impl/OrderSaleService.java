package org.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.example.client.*;
import org.example.dto.order.*;
import org.example.entity.*;
import org.example.enums.OrderStatus;
import org.example.exp.OrderException;
import org.example.repository.*;
import org.example.utils.SpringSecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@lombok.extern.slf4j.Slf4j
public class OrderSaleService {
    private final OrderRepository orders;
    private final OrderInvoiceRepository invoices;
    private final OrderServiceImpl workflow;
    private final CompanyClient companies;
    private final SaleCustomerClient customers;
    private final SellerWorkflowClient leads;
    private final SaleFileClient files;
    private final ControlClient control;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;

    public OrderSaleService(OrderRepository orders, OrderInvoiceRepository invoices, OrderServiceImpl workflow,
                            CompanyClient companies, SaleCustomerClient customers, SellerWorkflowClient leads, SaleFileClient files,
                            ControlClient control, ObjectMapper json, Clock clock, PlatformTransactionManager transactions) {
        this.orders = orders;
        this.invoices = invoices;
        this.workflow = workflow;
        this.companies = companies;
        this.customers = customers;
        this.leads = leads;
        this.files = files;
        this.control = control;
        this.json = json;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    public record Checkout(OrderResponse order, List<ControlClient.Availability> availability,
                           List<SaleRequest.PaymentMethod> paymentMethods, SaleCustomerClient.Customer customer) {
    }

    public Checkout checkout(Long id, Long customerId, String auth) {
        var order = authorized(id, true);
        var response = workflow.get(id);
        if (response.status() != OrderStatus.RESERVED && response.status() != OrderStatus.FINALIZING)
            throw error(409, "sale.state.invalid");
        List<ControlClient.Availability> available;
        try {
            available = control.availability(id, order.getCompanyId());
        } catch (FeignException ex) {
            throw unavailable("control.availability", ex);
        }
        if (available == null || available.size() != response.items().size()
                || !new HashSet<>(available.stream().map(ControlClient.Availability::productId).toList())
                .equals(new HashSet<>(response.items().stream().map(OrderResponse.Item::productId).toList())))
            throw OrderException.unavailable("operation=control.availability failure=invalid_response");
        var customer = customerId == null ? null : customer(customerId, order.getCompanyId(), auth);
        return new Checkout(response, available, List.of(SaleRequest.PaymentMethod.values()), customer);
    }

    public InvoiceResponse complete(Long id, SaleRequest request, String auth) {
        var order = authorized(id, true);
        String hash = hash(request);
        var existing = invoices.findById(id);
        if (existing.isPresent() && !"FAILED".equals(existing.get().getStatus())) {
            same(existing.get(), request, hash);
            return finish(id);
        }
        if (existing.isPresent() && existing.get().getIdempotencyKey().equals(request.idempotencyKey().strip()))
            throw error(409, "sale.attempt.failed");
        // Get fresh order state before persisting the irreversible sale intent.
        var current = workflow.get(id);
        if (current.status() != OrderStatus.RESERVED) throw error(409, "sale.state.invalid");
        var invoice = build(order, request, auth);
        tx.executeWithoutResult(status -> {
            var locked = lock(id);
            var saved = invoices.findById(id);
            if (saved.isPresent() && !"FAILED".equals(saved.get().getStatus())) {
                same(saved.get(), request, hash);
                return;
            }
            if (locked.getStatus() != OrderStatus.RESERVED) throw error(409, "sale.state.invalid");
            var plan = new OrderInvoice();
            plan.setOrderId(id);
            plan.setIdempotencyKey(request.idempotencyKey().strip());
            plan.setRequestHash(hash);
            plan.setPayload(write(invoice));
            plan.setStatus("PENDING");
            invoices.saveAndFlush(plan);
            locked.setStatus(OrderStatus.FINALIZING);
            locked.setUpdatedAt(clock.instant());
        });
        return finish(id);
    }

    public InvoiceResponse invoice(Long id) {
        authorized(id, false);
        var invoice = invoices.findById(id).filter(i -> "ISSUED".equals(i.getStatus()))
                .orElseThrow(() -> error(404, "sale.invoice.missing"));
        return read(invoice.getPayload());
    }

    // Invoked on the caller's retry and by the recovery job. No caller identity is
    // needed here: the immutable, authorized intent has already been persisted.
    public InvoiceResponse finish(Long id) {
        return tx.execute(status -> {
            var order = lock(id);
            var plan = invoices.findById(id).orElseThrow(() -> error(404, "sale.invoice.missing"));
            if ("ISSUED".equals(plan.getStatus())) return read(plan.getPayload());
            if (!"PENDING".equals(plan.getStatus()) || order.getStatus() != OrderStatus.FINALIZING)
                throw error(409, "sale.state.invalid");
            var invoice = read(plan.getPayload());
            ControlClient.Envelope response;
            try {
                response = control.complete(id, new ControlClient.SaleReservationRequest(order.getCompanyId(),
                        invoice.items().stream().map(i -> new ControlClient.Item(i.productId(), i.quantity())).toList()));
            } catch (FeignException ex) {
                // Transport/server failures leave PENDING intact, so retry can
                // confirm a remote commit whose HTTP response was lost.
                if (ex.status() == 400 || ex.status() == 409) {
                    plan.setStatus("FAILED");
                    order.setStatus(OrderStatus.RESERVED);
                    return null;
                }
                throw unavailable("control.complete", ex);
            }
            if (response == null || !response.success() || response.data() == null
                    || !id.equals(response.data().orderId()) || !order.getCompanyId().equals(response.data().companyId()))
                throw OrderException.unavailable("operation=control.complete failure=invalid_response");
            if (!"COMMITTED".equals(response.data().status())) {
                plan.setStatus("FAILED");
                order.setStatus("EXPIRED".equals(response.data().status()) ? OrderStatus.EXPIRED : OrderStatus.CANCELLED);
                return null;
            }
            var finalItems = new HashMap<Long, InvoiceResponse.Item>();
            invoice.items().forEach(item -> finalItems.put(item.productId(), item));
            for (var item : order.getItems()) {
                var sold = finalItems.get(item.getProductId());
                item.setQuantity(sold.quantity());
                item.setUnitPrice(sold.unitPrice());
                item.setLineAmount(sold.lineAmount());
            }
            order.setTotalAmount(invoice.totalAmount());
            order.setReservationId(response.data().id());
            order.setStatus(OrderStatus.SHIPPED);
            order.setShippedAt(clock.instant());
            order.setUpdatedAt(clock.instant());
            order.setDeliveryAddress(invoice.deliveryAddress());
            plan.setStatus("ISSUED");
            return invoice;
        });
    }

    public void recover() {
        for (Long id : orders.pending(List.of(OrderStatus.FINALIZING), org.springframework.data.domain.PageRequest.of(0, 100))) {
            try {
                finish(id);
            } catch (RuntimeException ex) {
                log.warn("Sale recovery deferred, orderId={}, failure={}",id,ex.getClass().getSimpleName());
                tx.executeWithoutResult(status -> lock(id).setUpdatedAt(clock.instant()));
            }
        }
    }

    private InvoiceResponse build(Order order, SaleRequest request, String auth) {
        if (request.deliveryMethod() == SaleRequest.DeliveryMethod.DELIVERY
                && (blank(request.buyerPhone()) || blank(request.deliveryAddress())))
            throw error(400, "sale.delivery.required");
        SaleCustomerClient.Customer customer = request.customerId() == null ? null : customer(request.customerId(), order.getCompanyId(), auth);
        if (request.paymentMethod() == SaleRequest.PaymentMethod.CREDIT) {
            if (customer == null || blank(customer.contractNumber()) || customer.paymentTermDays() == null || customer.paymentTermDays() < 1)
                throw error(409, "sale.contract.required");
            if (order.getLeadId() != null) {
                SellerWorkflowClient.LeadEnvelope lead;
                try {
                    lead = leads.lead(order.getLeadId(), auth);
                } catch (FeignException ex) {
                    throw unavailable("lead.details", ex);
                }
                if (lead == null || lead.data() == null || !order.getBuyerId().equals(lead.data().buyerId()))
                    throw error(409, "sale.customer.mismatch");
                boolean sameName = customer.name() != null && customer.name().strip().equalsIgnoreCase(Objects.toString(lead.data().contactName(), "").strip());
                boolean samePhone = !blank(customer.phone()) && customer.phone().replaceAll("\\D", "")
                        .equals(Objects.toString(lead.data().contactPhone(), "").replaceAll("\\D", ""));
                if (!sameName && !samePhone) throw error(409, "sale.customer.mismatch");
            }
        }
        if (!blank(request.attachmentId())) {
            SaleFileClient.Info info;
            try {
                info = files.info(request.attachmentId(), auth);
            } catch (FeignException ex) {
                throw unavailable("file.sale-info", ex);
            }
            if (info == null || !request.attachmentId().equals(info.id()) || info.size() == null || info.size() > 2097152
                    || !Set.of("application/pdf", "image/jpeg", "image/png", "image/webp").contains(Objects.toString(info.mimeType(), "")))
                throw error(400, "sale.attachment.invalid");
        }
        var snapshots = tx.execute(status -> {
            var saved = lock(order.getId());
            var original = new HashMap<Long, OrderItem>();
            saved.getItems().forEach(item -> original.put(item.getProductId(), item));
            var seen = new HashSet<Long>();
            var lines = new ArrayList<InvoiceResponse.Item>();
            for (var item : request.items()) {
                var source = original.get(item.productId());
                if (source == null || !seen.add(item.productId())) throw error(400, "sale.quantity.invalid");
                lines.add(new InvoiceResponse.Item(item.productId(), source.getProductName(), item.quantity(), item.unitPrice(),
                        item.quantity().multiply(item.unitPrice())));
            }
            if (!seen.equals(original.keySet())) throw error(400, "sale.quantity.invalid");
            lines.sort(Comparator.comparing(InvoiceResponse.Item::productId));
            return lines;
        });
        BigDecimal total = snapshots.stream().map(InvoiceResponse.Item::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate due = request.paymentMethod() == SaleRequest.PaymentMethod.CREDIT
                ? LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Tashkent")).plusDays(customer.paymentTermDays()) : null;
        return new InvoiceResponse("INV-" + LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).getYear() + "-" + order.getId(),
                order.getId(), order.getCompanyId(), order.getBuyerId(), order.getLeadId(), order.getCurrency(),
                request.buyerName().strip(), request.buyerPhone(), request.deliveryMethod(),
                request.deliveryMethod() == SaleRequest.DeliveryMethod.DELIVERY ? request.deliveryAddress() : null,
                request.paymentMethod(), request.paymentMethod() == SaleRequest.PaymentMethod.CREDIT ? "UNPAID" : "DECLARED_PAID",
                customer == null ? null : customer.id(), customer == null ? null : customer.contractNumber(), due, request.attachmentId(),
                total, clock.instant(), snapshots);
    }

    private SaleCustomerClient.Customer customer(Long id, Long companyId, String auth) {
        SaleCustomerClient.Envelope result;
        try {
            result = customers.get(id, auth);
        } catch (FeignException ex) {
            throw unavailable("customer.details", ex);
        }
        if (result == null || !Boolean.TRUE.equals(result.success()) || result.data() == null
                || !id.equals(result.data().id()) || !companyId.equals(result.data().companyId()))
            throw error(403, "sale.customer.mismatch");
        return result.data();
    }

    private Order authorized(Long id, boolean sellerOnly) {
        var order = orders.findById(id).orElseThrow(() -> error(404, "order.order.not.found"));
        Long profile = SpringSecurityUtil.getProfileId();
        if (!sellerOnly && profile.equals(order.getBuyerId())) return order;
        if (!SpringSecurityUtil.isSeller()) throw error(403, "order.access.denied");
        CompanyClient.Ownership owner;
        try {
            owner = companies.ownership(order.getCompanyId(), profile);
        } catch (FeignException ex) {
            throw unavailable("company.ownership", ex);
        }
        if (owner == null) throw OrderException.unavailable("operation=company.ownership failure=invalid_response");
        if (!order.getCompanyId().equals(owner.companyId()) || !owner.owner() || !owner.exists() || !owner.active())
            throw error(403, "order.access.denied");
        return order;
    }

    private Order lock(Long id) {
        return orders.lock(id).orElseThrow(() -> error(404, "order.order.not.found"));
    }

    private void same(OrderInvoice invoice, SaleRequest request, String hash) {
        if (!invoice.getIdempotencyKey().equals(request.idempotencyKey().strip()) || !invoice.getRequestHash().equals(hash))
            throw error(409, "order.idempotency.conflict");
    }

    private String hash(SaleRequest request) {
        var canonical = new SaleRequest("", request.paymentMethod(), request.customerId(), request.buyerName().strip(),
                request.buyerPhone(), request.deliveryMethod(), request.deliveryAddress(), request.attachmentId(),
                request.items().stream().map(i -> new SaleRequest.Item(i.productId(), i.quantity().stripTrailingZeros(), i.unitPrice().stripTrailingZeros()))
                        .sorted(Comparator.comparing(SaleRequest.Item::productId)).toList());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(canonical).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Sale serialization failed", ex);
        }
    }

    private InvoiceResponse read(String value) {
        try {
            return json.readValue(value, InvoiceResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Sale snapshot invalid", ex);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private OrderException error(int status, String key) {
        return new OrderException(status, key);
    }

    private OrderException unavailable(String operation, FeignException ex) {
        return OrderException.unavailable("operation=" + operation + " upstream_status=" + ex.status());
    }
}
