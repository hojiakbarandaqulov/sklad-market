package org.example.service.impl;
import org.example.exp.AppBadException;
import org.example.exp.AppNotFoundException;
import org.example.exp.AppConflictException;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.example.client.*;
import org.example.dto.order.*;
import org.example.entity.*;
import org.example.enums.OrderStatus;
import org.example.enums.AppLanguage;
import org.example.service.ResourceBundleService;
import org.example.service.OrderSaleService;
import org.example.service.OrderService;
import org.example.exp.AppServiceUnavailableException;
import org.example.exp.OrderException;
import org.example.repository.*;
import org.example.utils.SpringSecurityUtil;
import org.example.utils.OrderRequestUtils;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

@Service
@Slf4j
public class OrderSaleServiceImpl implements OrderSaleService {
    private static final int RECOVERY_BATCH_SIZE = 100;
    private final OrderRepository orders;
    private final OrderInvoiceRepository invoices;
    private final OrderService orderService;
    private final CompanyClient companies;
    private final SaleCustomerClient customers;
    private final SellerWorkflowClient leads;
    private final SaleFileClient files;
    private final ControlClient control;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final ResourceBundleService messageService;

    public OrderSaleServiceImpl(OrderRepository orders, OrderInvoiceRepository invoices, OrderService orderService,
                            CompanyClient companies, SaleCustomerClient customers, SellerWorkflowClient leads, SaleFileClient files,
                            ControlClient control, ObjectMapper json, Clock clock, PlatformTransactionManager transactions, ResourceBundleService messageService) {
        this.orders = orders;
        this.invoices = invoices;
        this.orderService = orderService;
        this.companies = companies;
        this.customers = customers;
        this.leads = leads;
        this.files = files;
        this.control = control;
        this.json = json;
        this.clock = clock;
        this.messageService = messageService;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public CheckoutResponse checkout(Long id, Long customerId, String auth, AppLanguage language) {
        Order order = authorized(id, true, language);
        OrderResponse response = orderService.get(id, language);
        if (response.status() != OrderStatus.RESERVED && response.status() != OrderStatus.FINALIZING) {
            throw new AppConflictException(messageService.getMessage("sale.state.invalid", language));
        }
        List<ControlClient.Availability> available;
        try {
            available = control.availability(id, order.getCompanyId(), language.name());
        } catch (FeignException ex) {
            throw unavailable("control.availability", ex, language);
        }
        if (available == null || available.size() != response.items().size()
                || !new HashSet<>(available.stream().map(ControlClient.Availability::productId).toList())
                .equals(new HashSet<>(response.items().stream().map(OrderResponse.Item::productId).toList())))
            throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=control.availability failure=invalid_response");
        SaleCustomerClient.Customer customer = customerId == null ? null : customer(customerId, order.getCompanyId(), auth, language);
        return new CheckoutResponse(response, available, List.of(SaleRequest.PaymentMethod.values()), customer);
    }

    @Override
    public InvoiceResponse complete(Long id, SaleRequest request, String auth, AppLanguage language) {
        Order order = authorized(id, true, language);
        validateDelivery(request, language);
        String requestHash = createRequestHash(request);
        if (needsSalePlan(id, request.idempotencyKey(), requestHash, language)) {
            OrderResponse current = orderService.get(id, language);
            if (current.status() == OrderStatus.RESERVED) {
                InvoiceResponse invoice = buildInvoice(order, request, auth, language);
                saveSalePlan(id, request.idempotencyKey(), requestHash, invoice, language);
            } else if (needsSalePlan(id, request.idempotencyKey(), requestHash, language)) {
                throw new AppConflictException(messageService.getMessage("sale.state.invalid", language));
            }
        }
        InvoiceResponse result = finish(id, language);
        if (result == null) {
            throw new AppConflictException(messageService.getMessage("sale.stock.changed", language));
        }
        return result;
    }

    private boolean needsSalePlan(Long orderId, String idempotencyKey, String requestHash, AppLanguage language) {
        OrderInvoice existing = invoices.findById(orderId).orElse(null);
        if (existing == null) {
            return true;
        }
        boolean sameKey = existing.getIdempotencyKey().equals(idempotencyKey.strip());
        if ("FAILED".equals(existing.getStatus())) {
            if (sameKey) {
                throw new AppConflictException(messageService.getMessage("sale.attempt.failed", language));
            }
            return true;
        }
        if (!sameKey || !existing.getRequestHash().equals(requestHash)) {
            throw new AppConflictException(messageService.getMessage("order.idempotency.conflict", language));
        }
        return false;
    }

    private void saveSalePlan(Long orderId, String idempotencyKey, String requestHash, InvoiceResponse invoice,
                              AppLanguage language) {
        tx.executeWithoutResult(transaction -> {
            Order order = lock(orderId, language);
            if (!needsSalePlan(orderId, idempotencyKey, requestHash, language)) {
                return;
            }
            if (order.getStatus() != OrderStatus.RESERVED) {
                throw new AppConflictException(messageService.getMessage("sale.state.invalid", language));
            }
            OrderInvoice plan = new OrderInvoice();
            plan.setOrderId(orderId);
            plan.setIdempotencyKey(idempotencyKey.strip());
            plan.setRequestHash(requestHash);
            plan.setPayload(write(invoice));
            plan.setStatus("PENDING");
            invoices.saveAndFlush(plan);
            order.setStatus(OrderStatus.FINALIZING);
            order.setUpdatedAt(clock.instant());
        });
    }

    @Override
    public InvoiceResponse invoice(Long id, AppLanguage language) {
        authorized(id, false, language);
        OrderInvoice invoice = invoices.findById(id).filter(i -> "ISSUED".equals(i.getStatus()))
                .orElseThrow(() -> new AppNotFoundException(messageService.getMessage("sale.invoice.missing", language)));
        InvoiceResponse response = read(invoice.getPayload());
        if (response.companyName() == null || response.companyName().isBlank()) {
            return response.withCompanyName(companyName(response.companyId()));
        }
        return response;
    }

    // Invoked on the caller's retry and by the recovery job. No caller identity is
    // needed here: the immutable, authorized intent has already been persisted.
    private InvoiceResponse finish(Long id, AppLanguage language) {
        return tx.execute(status -> {
            Order order = lock(id, language);
            OrderInvoice plan = invoices.findById(id).orElseThrow(() -> new AppNotFoundException(messageService.getMessage("sale.invoice.missing", language)));
            if ("ISSUED".equals(plan.getStatus())) return read(plan.getPayload());
            if (!"PENDING".equals(plan.getStatus()) || order.getStatus() != OrderStatus.FINALIZING)
                throw new AppConflictException(messageService.getMessage("sale.state.invalid", language));
            InvoiceResponse invoice = read(plan.getPayload());
            ControlClient.Envelope response;
            try {
                response = control.complete(id, new ControlClient.SaleReservationRequest(order.getCompanyId(),
                        invoice.items().stream().map(i -> new ControlClient.Item(i.productId(), i.quantity())).toList()), language.name());
            } catch (FeignException ex) {
                // Transport/server failures leave PENDING intact, so retry can
                // confirm a remote commit whose HTTP response was lost.
                if (ex.status() == 400 || ex.status() == 409) {
                    plan.setStatus("FAILED");
                    order.setStatus(OrderStatus.RESERVED);
                    return null;
                }
                throw unavailable("control.complete", ex, language);
            }
            if (response == null || !response.success() || response.data() == null
                    || !id.equals(response.data().orderId()) || !order.getCompanyId().equals(response.data().companyId()))
                throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=control.complete failure=invalid_response");
            if (!"COMMITTED".equals(response.data().status())) {
                plan.setStatus("FAILED");
                order.setStatus("EXPIRED".equals(response.data().status()) ? OrderStatus.EXPIRED : OrderStatus.CANCELLED);
                return null;
            }
            applyCompletedSale(order, invoice, response.data().id());
            plan.setStatus("ISSUED");
            return invoice;
        });
    }

    private void applyCompletedSale(Order order, InvoiceResponse invoice, Long reservationId) {
        Map<Long, InvoiceResponse.Item> soldItems = new HashMap<>();
        for (InvoiceResponse.Item item : invoice.items()) {
            soldItems.put(item.productId(), item);
        }
        for (OrderItem item : order.getItems()) {
            InvoiceResponse.Item sold = soldItems.get(item.getProductId());
            item.setQuantity(sold.quantity());
            item.setUnitPrice(sold.unitPrice());
            item.setLineAmount(sold.lineAmount());
        }
        Instant completedAt = clock.instant();
        order.setTotalAmount(invoice.totalAmount());
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedAt(completedAt);
        order.setUpdatedAt(completedAt);
        applyDelivery(order, invoice);
    }

    @Override
    public void recover(AppLanguage language) {
        List<Long> pendingOrderIds = orders.pending(
                List.of(OrderStatus.FINALIZING), PageRequest.of(0, RECOVERY_BATCH_SIZE));
        for (Long id : pendingOrderIds) {
            try {
                finish(id, language);
            } catch (RuntimeException ex) {
                log.warn("Sale recovery deferred, orderId={}, failure={}", id, ex.getClass().getSimpleName());
                tx.executeWithoutResult(status -> lock(id, language).setUpdatedAt(clock.instant()));
            }
        }
    }

    private void validateDelivery(SaleRequest request, AppLanguage language) {
        if (request.deliveryMethod() != SaleRequest.DeliveryMethod.DELIVERY) {
            return;
        }
        if (blank(request.buyerPhone()) || blank(request.deliveryAddress())) {
            throw new AppBadException(messageService.getMessage("sale.delivery.required", language));
        }
        if (request.longitude() == null && request.latitude() == null) {
            return;
        }
        if (request.longitude() == null || request.latitude() == null) {
            throw new AppBadException(messageService.getMessage("order.coordinates.required", language));
        }
        if (!OrderRequestUtils.areCoordinatesValid(request.longitude(), request.latitude())) {
            throw new AppBadException(messageService.getMessage("order.coordinates.invalid", language));
        }
    }

    private void applyDelivery(Order order, InvoiceResponse invoice) {
        String address = OrderRequestUtils.normalizeText(invoice.deliveryAddress());
        if (invoice.longitude() != null && invoice.latitude() != null) {
            order.setLongitude(invoice.longitude());
            order.setLatitude(invoice.latitude());
        } else if (address == null || !Objects.equals(order.getDeliveryAddress(), address)) {
            order.setLongitude(null);
            order.setLatitude(null);
        }
        order.setDeliveryAddress(address);
    }

    private InvoiceResponse buildInvoice(Order order, SaleRequest request, String auth, AppLanguage language) {
        SaleCustomerClient.Customer customer = request.customerId() == null ? null : customer(request.customerId(), order.getCompanyId(), auth, language);
        if (request.paymentMethod() == SaleRequest.PaymentMethod.CREDIT) {
            if (customer == null || blank(customer.contractNumber()) || customer.paymentTermDays() == null || customer.paymentTermDays() < 1)
                throw new AppConflictException(messageService.getMessage("sale.contract.required", language));
            if (order.getLeadId() != null) {
                SellerWorkflowClient.LeadEnvelope lead;
                try {
                    lead = leads.lead(order.getLeadId(), auth);
                } catch (FeignException ex) {
                    throw unavailable("lead.details", ex, language);
                }
                if (lead == null || lead.data() == null || !order.getBuyerId().equals(lead.data().buyerId()))
                    throw new AppConflictException(messageService.getMessage("sale.customer.mismatch", language));
                boolean sameName = customer.name() != null && customer.name().strip().equalsIgnoreCase(Objects.toString(lead.data().contactName(), "").strip());
                boolean samePhone = !blank(customer.phone()) && customer.phone().replaceAll("\\D", "")
                        .equals(Objects.toString(lead.data().contactPhone(), "").replaceAll("\\D", ""));
                if (!sameName && !samePhone) throw new AppConflictException(messageService.getMessage("sale.customer.mismatch", language));
            }
        }
        if (!blank(request.attachmentId())) {
            SaleFileClient.Info info;
            try {
                info = files.info(request.attachmentId(), auth);
            } catch (FeignException ex) {
                throw unavailable("file.sale-info", ex, language);
            }
            if (info == null || !request.attachmentId().equals(info.id()) || info.size() == null || info.size() > 2097152
                    || !Set.of("application/pdf", "image/jpeg", "image/png", "image/webp").contains(Objects.toString(info.mimeType(), "")))
                throw new AppBadException(messageService.getMessage("sale.attachment.invalid", language));
        }
        List<InvoiceResponse.Item> snapshots = tx.execute(status -> {
            Order saved = lock(order.getId(), language);
            Map<Long, OrderItem> original = new HashMap<Long, OrderItem>();
            saved.getItems().forEach(item -> original.put(item.getProductId(), item));
            Set<Long> seen = new HashSet<Long>();
            List<InvoiceResponse.Item> lines = new ArrayList<InvoiceResponse.Item>();
            for (SaleRequest.Item item : request.items()) {
                OrderItem source = original.get(item.productId());
                if (source == null || !seen.add(item.productId())) throw new AppBadException(messageService.getMessage("sale.quantity.invalid", language));
                lines.add(new InvoiceResponse.Item(item.productId(), source.getProductName(), item.quantity(), item.unitPrice(),
                        item.quantity().multiply(item.unitPrice())));
            }
            if (!seen.equals(original.keySet())) throw new AppBadException(messageService.getMessage("sale.quantity.invalid", language));
            lines.sort(Comparator.comparing(InvoiceResponse.Item::productId));
            return lines;
        });
        BigDecimal total = snapshots.stream().map(InvoiceResponse.Item::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate due = request.paymentMethod() == SaleRequest.PaymentMethod.CREDIT
                ? LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Tashkent")).plusDays(customer.paymentTermDays()) : null;
        boolean delivery = request.deliveryMethod() == SaleRequest.DeliveryMethod.DELIVERY;
        String address = delivery ? OrderRequestUtils.normalizeText(request.deliveryAddress()) : null;
        BigDecimal longitude = delivery ? request.longitude() : null;
        BigDecimal latitude = delivery ? request.latitude() : null;
        if (address != null && address.equals(order.getDeliveryAddress()) && longitude == null && latitude == null) {
            longitude = order.getLongitude();
            latitude = order.getLatitude();
        }
        return new InvoiceResponse("INV-" + LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).getYear() + "-" + order.getId(),
                order.getId(), order.getCompanyId(), order.getBuyerId(), order.getLeadId(), order.getCurrency(),
                request.buyerName().strip(), request.buyerPhone(), request.deliveryMethod(),
                address, longitude, latitude,
                request.paymentMethod(), request.paymentMethod() == SaleRequest.PaymentMethod.CREDIT ? "UNPAID" : "DECLARED_PAID",
                customer == null ? null : customer.id(), companyName(order.getCompanyId()),
                customer == null ? null : customer.contractNumber(), due, request.attachmentId(),
                total, clock.instant(), snapshots);
    }

    private String companyName(Long companyId) {
        try {
            CompanyClient.Summary company = companies.summary(companyId);
            return company != null && companyId.equals(company.id()) ? company.name() : null;
        } catch (FeignException ex) {
            log.warn("Invoice company lookup failed, companyId={}, status={}", companyId, ex.status());
            return null;
        } catch (AppServiceUnavailableException ex) {
            log.warn("Invoice company lookup unavailable, companyId={}", companyId);
            return null;
        }
    }

    private SaleCustomerClient.Customer customer(Long id, Long companyId, String auth, AppLanguage language) {
        SaleCustomerClient.Envelope result;
        try {
            result = customers.get(id, auth);
        } catch (FeignException ex) {
            throw unavailable("customer.details", ex, language);
        }
        if (result == null || !Boolean.TRUE.equals(result.success()) || result.data() == null
                || !id.equals(result.data().id()) || !companyId.equals(result.data().companyId()))
            throw new AccessDeniedException(messageService.getMessage("sale.customer.mismatch", language));
        return result.data();
    }

    private Order authorized(Long id, boolean sellerOnly, AppLanguage language) {
        Order order = orders.findById(id).orElseThrow(() -> new AppNotFoundException(messageService.getMessage("order.order.not.found", language)));
        Long profile = SpringSecurityUtil.getProfileId();
        if (!sellerOnly && profile.equals(order.getBuyerId())) return order;
        if (!SpringSecurityUtil.isSeller()) throw new AccessDeniedException(messageService.getMessage("order.access.denied", language));
        CompanyClient.Ownership owner;
        try {
            owner = companies.ownership(order.getCompanyId(), profile);
        } catch (FeignException ex) {
            throw unavailable("company.ownership", ex, language);
        }
        if (owner == null) throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=company.ownership failure=invalid_response");
        if (!order.getCompanyId().equals(owner.companyId()) || !owner.owner() || !owner.exists() || !owner.active())
            throw new AccessDeniedException(messageService.getMessage("order.access.denied", language));
        return order;
    }

    private Order lock(Long id, AppLanguage language) {
        return orders.lock(id).orElseThrow(() -> new AppNotFoundException(messageService.getMessage("order.order.not.found", language)));
    }

    private String createRequestHash(SaleRequest request) {
        boolean delivery = request.deliveryMethod() == SaleRequest.DeliveryMethod.DELIVERY;
        BigDecimal longitude = delivery && request.longitude() != null ? request.longitude().stripTrailingZeros() : null;
        BigDecimal latitude = delivery && request.latitude() != null ? request.latitude().stripTrailingZeros() : null;
        SaleRequest canonical = new SaleRequest("", request.paymentMethod(), request.customerId(), request.buyerName().strip(),
                request.buyerPhone(), request.deliveryMethod(), request.deliveryAddress(), longitude, latitude, request.attachmentId(),
                request.items().stream().map(i -> new SaleRequest.Item(i.productId(), i.quantity().stripTrailingZeros(), i.unitPrice().stripTrailingZeros()))
                        .sorted(Comparator.comparing(SaleRequest.Item::productId)).toList());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(canonical).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Sale serialization failed", ex);
        }
    }

    private InvoiceResponse read(String value) {
        try {
            return json.readValue(value, InvoiceResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Sale snapshot invalid", ex);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }


    private OrderException unavailable(String operation, FeignException ex, AppLanguage language) {
        return new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=" + operation + " upstream_status=" + ex.status());
    }
}
