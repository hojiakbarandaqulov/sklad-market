package org.example.service.impl;
import org.example.exp.AppBadException;
import org.example.exp.AppNotFoundException;
import org.example.exp.AppConflictException;
import org.springframework.security.access.AccessDeniedException;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.example.client.CompanyClient;
import org.example.client.ControlClient;
import org.example.client.ProductClient;
import org.example.dto.order.OrderCreateRequest;
import org.example.dto.order.OrderFilter;
import org.example.dto.order.OrderResponse;
import org.example.entity.Order;
import org.example.entity.OrderItem;
import org.example.enums.OrderStatus;
import org.example.enums.AppLanguage;
import org.example.service.ResourceBundleService;
import org.example.client.SellerWorkflowClient;
import org.example.dto.order.LeadAcceptRequest;
import org.example.exp.AppServiceUnavailableException;
import java.util.Map;
import java.util.TreeMap;
import org.example.exp.OrderException;
import org.example.mapper.OrderMapper;
import org.example.repository.OrderRepository;
import org.example.repository.OrderSpecification;
import org.example.service.OrderService;
import org.example.utils.OrderRequestUtils;
import org.example.utils.SpringSecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final int RECOVERY_BATCH_SIZE = 100;
    private static final Duration MAX_RESERVATION_DURATION = Duration.ofDays(30);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final CompanyClient companyClient;
    private final ControlClient controlClient;
    private final Clock clock;
    private final Duration reservationDuration;
    private final TransactionTemplate transactionTemplate;
    private final OrderMapper orderMapper;
    private final ResourceBundleService messageService;
    private final SellerWorkflowClient leads;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductClient productClient,
            CompanyClient companyClient,
            ControlClient controlClient,
            Clock clock,
            PlatformTransactionManager transactionManager,
            OrderMapper orderMapper,
            ResourceBundleService messageService,
            SellerWorkflowClient leads,
            @Value("${order.reservation-ttl}") Duration reservationDuration
    ) {
        validateReservationDuration(reservationDuration);

        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.companyClient = companyClient;
        this.controlClient = controlClient;
        this.clock = clock;
        this.orderMapper = orderMapper;
        this.messageService = messageService;
        this.leads = leads;
        this.reservationDuration = reservationDuration;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public OrderResponse create(OrderCreateRequest request, AppLanguage language) {
        Long buyerId = SpringSecurityUtil.getProfileId();
        validateDeliveryCoordinates(request.longitude(), request.latitude(), language);
        String idempotencyKey = request.idempotencyKey().strip();
        String requestHash = OrderRequestUtils.createRequestHash(request);

        Long orderId = findOrCreateOrder(request, buyerId, idempotencyKey, requestHash, language);

        // Lokal buyurtma oldin saqlanadi. Control javobi yo'qolsa, shu ID bilan qayta uriniladi.
        OrderResponse response = resume(orderId, language);

        if (response.status() == OrderStatus.REJECTED) {
            throw new AppConflictException(response.failureReason());
        }

        return response;
    }

    private void validateDeliveryCoordinates(BigDecimal longitude, BigDecimal latitude, AppLanguage language) {
        if (longitude == null || latitude == null) {
            throw new AppBadException(messageService.getMessage("order.coordinates.required", language));
        }
        if (!OrderRequestUtils.areCoordinatesValid(longitude, latitude)) {
            throw new AppBadException(messageService.getMessage("order.coordinates.invalid", language));
        }
    }

    @Override
    public Page<OrderResponse> getBuyerOrders(OrderFilter filter, AppLanguage language) {
        Long buyerId = SpringSecurityUtil.getProfileId();
        Specification<Order> specification = OrderSpecification.forBuyer(buyerId, filter);

        return findOrders(specification, filter, language);
    }

    @Override
    public Page<OrderResponse> getSellerOrders(OrderFilter filter, AppLanguage language) {
        Long sellerId = SpringSecurityUtil.getProfileId();

        if (filter.getCompanyId() == null) {
            throw new AppBadException(messageService.getMessage("order.company.required", language));
        }

        requireActiveCompanyOwner(filter.getCompanyId(), sellerId, language);
        Specification<Order> specification = OrderSpecification.forSeller(filter);

        return findOrders(specification, filter, language);
    }

    @Override
    public OrderResponse get(Long orderId, AppLanguage language) {
        checkOrderAccess(orderId, false, language);
        return resume(orderId, language);
    }

    @Override
    public OrderResponse ship(Long orderId, AppLanguage language) {
        checkOrderAccess(orderId, true, language);
        resume(orderId, language);
        markOrderAsShipping(orderId, language);

        OrderResponse response = resume(orderId, language);

        if (response.status() != OrderStatus.SHIPPED) {
            throw new AppConflictException(messageService.getMessage("order.reservation.expired", language));
        }

        return response;
    }

    @Override
    public OrderResponse cancel(Long orderId, AppLanguage language) {
        checkOrderAccess(orderId, false, language);
        resume(orderId, language);
        markOrderAsCancelling(orderId, language);

        return resume(orderId, language);
    }

    @Override
    public OrderResponse resume(Long orderId, AppLanguage language) {
        // Bir buyurtmani jo'natish va bekor qilish qulf sabab bir vaqtda bajarilmaydi.
        return transactionTemplate.execute(transactionStatus -> synchronizeWithControl(orderId, language));
    }

    @Override
    public void recover(AppLanguage language) {
        List<OrderStatus> pendingStatuses = List.of(
                OrderStatus.RESERVING,
                OrderStatus.RESERVED,
                OrderStatus.SHIPPING,
                OrderStatus.CANCELLING
        );

        Pageable batch = PageRequest.of(0, RECOVERY_BATCH_SIZE);
        List<Long> orderIds = orderRepository.pending(pendingStatuses, batch);

        for (Long orderId : orderIds) {
            try {
                resume(orderId, language);
            } catch (RuntimeException exception) {
                String diagnostic = exception instanceof OrderException orderException
                        ? orderException.getDiagnostic() : exception.getClass().getSimpleName();
                log.warn("Buyurtma sinxronlash kechiktirildi, orderId={}, diagnostic={}", orderId, diagnostic);
                postponeRecovery(orderId, language);
            }
        }
    }

    private Long findOrCreateOrder(
            OrderCreateRequest request,
            Long buyerId,
            String idempotencyKey,
            String requestHash,
            AppLanguage language
    ) {
        Optional<Order> existingOrder = orderRepository.findByBuyerIdAndIdempotencyKey(buyerId, idempotencyKey);

        if (existingOrder.isPresent()) {
            validateSameRequest(existingOrder.get(), requestHash, language);
            return existingOrder.get().getId();
        }

        Order newOrder = prepareOrder(request, buyerId, idempotencyKey, requestHash, language);

        try {
            return transactionTemplate.execute(transactionStatus -> orderRepository.saveAndFlush(newOrder).getId());
        } catch (DataIntegrityViolationException exception) {
            // Ikki bir xil so'rov parallel kelsa, birinchi saqlangan buyurtmadan foydalanamiz.
            Order savedOrder = orderRepository.findByBuyerIdAndIdempotencyKey(buyerId, idempotencyKey)
                    .orElseThrow(() -> exception);

            validateSameRequest(savedOrder, requestHash, language);
            return savedOrder.getId();
        }
    }

    private Order prepareOrder(
            OrderCreateRequest request,
            Long buyerId,
            String idempotencyKey,
            String requestHash,
            AppLanguage language
    ) {
        if (idempotencyKey.isEmpty()) {
            throw new AppBadException(messageService.getMessage("order.idempotency.required", language));
        }

        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Order order = new Order();

        order.setBuyerId(buyerId);
        order.setCompanyId(request.companyId());
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestHash(requestHash);
        order.setStatus(OrderStatus.RESERVING);
        order.setDeliveryAddress(OrderRequestUtils.normalizeText(request.deliveryAddress()));
        order.setLongitude(request.longitude());
        order.setLatitude(request.latitude());
        order.setNote(OrderRequestUtils.normalizeText(request.note()));
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt);
        order.setExpiresAt(createdAt.plus(reservationDuration));

        addOrderItems(order, request.items(), language);
        return order;
    }

    private void addOrderItems(Order order, List<OrderCreateRequest.Item> requestedItems, AppLanguage language) {
        List<OrderCreateRequest.Item> sortedItems = new ArrayList<>(requestedItems);
        sortedItems.sort(Comparator.comparing(OrderCreateRequest.Item::productId));

        Set<Long> addedProductIds = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Long companySellerId = null;

        for (OrderCreateRequest.Item requestedItem : sortedItems) {
            if (!addedProductIds.add(requestedItem.productId())) {
                throw new AppBadException(messageService.getMessage("order.product.duplicate", language));
            }

            ProductClient.ProductSnapshot product = callRemoteService(
                    "product.order-summary",
                    () -> productClient.get(requestedItem.productId()),
                    language
            );

            validateProduct(requestedItem, product, order.getCompanyId(), language);
            validateSameSellerAndCurrency(order, companySellerId, product, language);

            companySellerId = product.sellerId();
            order.setCurrency(product.currency());

            OrderItem orderItem = createOrderItem(order, requestedItem, product);
            order.getItems().add(orderItem);
            totalAmount = totalAmount.add(orderItem.getLineAmount());
        }

        requireActiveCompanyOwner(order.getCompanyId(), companySellerId, language);
        order.setTotalAmount(totalAmount);
    }

    private void validateProduct(
            OrderCreateRequest.Item requestedItem,
            ProductClient.ProductSnapshot product,
            Long companyId,
            AppLanguage language
    ) {
        if (product == null || !requestedItem.productId().equals(product.id())
                || product.companyId() == null || product.sellerId() == null
                || product.name() == null || product.name().isBlank()) {
            throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=product.order-summary failure=invalid_response");
        }

        if (!companyId.equals(product.companyId())) {
            throw new AppBadException(messageService.getMessage("order.company.mixed", language));
        }

        if (!product.orderable() || product.price() == null
                || product.price().signum() <= 0 || product.currency() == null) {
            throw new AppConflictException(messageService.getMessage("order.product.unavailable", language));
        }

        BigDecimal price = product.price();
        boolean tooManyDecimalPlaces = price.stripTrailingZeros().scale() > 3;
        boolean tooManyIntegerDigits = price.precision() - price.scale() > 16;

        if (tooManyDecimalPlaces || tooManyIntegerDigits) {
            throw new AppConflictException(messageService.getMessage("order.price.precision", language));
        }

        if (product.minimumQuantity() != null) {
            BigDecimal minimumQuantity = BigDecimal.valueOf(product.minimumQuantity());

            if (requestedItem.quantity().compareTo(minimumQuantity) < 0) {
                throw new AppBadException(messageService.getMessage("order.quantity.minimum", language),
                        "operation=product.quantity-check productId=" + product.id()
                                + " quantity=" + requestedItem.quantity() + " minimum=" + minimumQuantity);
            }
        }
    }

    private void validateSameSellerAndCurrency(
            Order order,
            Long companySellerId,
            ProductClient.ProductSnapshot product,
            AppLanguage language
    ) {
        if (order.getCurrency() != null && !order.getCurrency().equals(product.currency())) {
            throw new AppBadException(messageService.getMessage("order.currency.mixed", language));
        }

        if (companySellerId != null && !companySellerId.equals(product.sellerId())) {
            throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=product.order-summary failure=seller_mismatch");
        }
    }

    private OrderItem createOrderItem(
            Order order,
            OrderCreateRequest.Item requestedItem,
            ProductClient.ProductSnapshot product
    ) {
        // Narx va nom frontenddan olinmaydi; buyurtmada Product-service bergan nusxa saqlanadi.
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProductId(product.id());
        orderItem.setProductName(product.name());
        orderItem.setQuantity(requestedItem.quantity());
        orderItem.setUnitPrice(product.price());
        orderItem.setLineAmount(product.price().multiply(requestedItem.quantity()));

        return orderItem;
    }

    private Page<OrderResponse> findOrders(Specification<Order> specification, OrderFilter filter, AppLanguage language) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getPerPage(), sort);

        return transactionTemplate.execute(transactionStatus -> {
            Page<Order> orders = orderRepository.findAll(specification, pageable);
            return orders.map(order -> orderMapper.toResponse(order, language));
        });
    }

    private void checkOrderAccess(Long orderId, boolean sellerOnly, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();

        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Order order = findOrder(orderId, language);
            boolean isOrderBuyer = profileId.equals(order.getBuyerId());

            if (!sellerOnly && isOrderBuyer) {
                return;
            }

            if (!SpringSecurityUtil.isSeller()) {
                throw new AccessDeniedException(messageService.getMessage("order.order.not.owned", language));
            }

            requireActiveCompanyOwner(order.getCompanyId(), profileId, language);
        });
    }

    private void markOrderAsShipping(Long orderId, AppLanguage language) {
        // Niyat alohida tranzaksiyada saqlanadi. Control ishlamasa, fon vazifasi shu joydan davom etadi.
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Order order = findOrderForUpdate(orderId, language);

            if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.SHIPPING) {
                return;
            }

            if (order.getStatus() != OrderStatus.RESERVED) {
                throw new AppConflictException(messageService.getMessage("order.order.ship.invalid", language));
            }

            order.setStatus(OrderStatus.SHIPPING);
            order.setUpdatedAt(clock.instant());
        });
    }

    private void markOrderAsCancelling(Long orderId, AppLanguage language) {
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Order order = findOrderForUpdate(orderId, language);
            OrderStatus currentStatus = order.getStatus();

            if (currentStatus == OrderStatus.CANCELLING || currentStatus == OrderStatus.CANCELLED
                    || currentStatus == OrderStatus.EXPIRED || currentStatus == OrderStatus.REJECTED) {
                return;
            }

            if (currentStatus != OrderStatus.RESERVED) {
                throw new AppConflictException(messageService.getMessage("order.order.cancel.invalid", language));
            }

            order.setStatus(OrderStatus.CANCELLING);
            order.setUpdatedAt(clock.instant());
        });
    }

    private OrderResponse synchronizeWithControl(Long orderId, AppLanguage language) {
        Order order = findOrderForUpdate(orderId, language);
        if (order.getStatus() == OrderStatus.FINALIZING) return orderMapper.toResponse(order, language);

        if (isFinalStatus(order.getStatus())) {
            return orderMapper.toResponse(order, language);
        }

        ControlClient.Envelope controlResponse;

        try {
            controlResponse = sendControlRequest(order, language);
        } catch (FeignException exception) {
            if (isReservationRejected(order, exception)) {
                markReservationRejected(order);
                return orderMapper.toResponse(order, language);
            }

            // Tarmoq xatosida yakuniy holatni taxmin qilmaymiz; saqlangan niyat keyin qayta tekshiriladi.
            throw dependencyUnavailable("control.reservation." + order.getStatus(), exception, language);
        }

        validateControlResponse(order, controlResponse, language);
        applyReservationStatus(order, controlResponse.data(), language);

        return orderMapper.toResponse(order, language);
    }

    private ControlClient.Envelope sendControlRequest(Order order, AppLanguage language) {
        Long orderId = order.getId();
        Long companyId = order.getCompanyId();

        return switch (order.getStatus()) {
            case RESERVING -> controlClient.reserve(orderId, createReservationRequest(order), language.name());
            case SHIPPING -> controlClient.commit(orderId, companyId, language.name());
            case CANCELLING -> controlClient.release(orderId, companyId, language.name());
            case RESERVED -> controlClient.get(orderId, companyId, language.name());
            default -> throw new IllegalStateException("Kutilmagan order holati");
        };
    }

    private ControlClient.ReserveRequest createReservationRequest(Order order) {
        List<ControlClient.Item> reservationItems = new ArrayList<>();

        for (OrderItem orderItem : order.getItems()) {
            ControlClient.Item reservationItem = new ControlClient.Item(
                    orderItem.getProductId(),
                    orderItem.getQuantity()
            );
            reservationItems.add(reservationItem);
        }

        return new ControlClient.ReserveRequest(
                order.getCompanyId(),
                order.getBuyerId(),
                order.getExpiresAt(),
                reservationItems
        );
    }

    private void validateControlResponse(Order order, ControlClient.Envelope response, AppLanguage language) {
        if (response == null || !response.success() || response.data() == null) {
            throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=control.reservation failure=invalid_response");
        }

        ControlClient.Reservation reservation = response.data();

        if (reservation.id() == null || reservation.id() <= 0
                || !order.getCompanyId().equals(reservation.companyId())
                || !order.getId().equals(reservation.orderId())) {
            throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=control.reservation failure=identity_mismatch");
        }
    }

    private void applyReservationStatus(Order order, ControlClient.Reservation reservation, AppLanguage language) {
        OrderStatus newStatus = toOrderStatus(reservation.status(), language);
        boolean isFinishingOrder = order.getStatus() == OrderStatus.SHIPPING
                || order.getStatus() == OrderStatus.CANCELLING;

        if (isFinishingOrder && newStatus == OrderStatus.RESERVED) {
            throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=control.reservation failure=unfinished_transition");
        }

        order.setReservationId(reservation.id());
        order.setStatus(newStatus);
        order.setUpdatedAt(clock.instant());

        if (newStatus == OrderStatus.SHIPPED && order.getShippedAt() == null) {
            order.setShippedAt(clock.instant());
        }

        if (newStatus == OrderStatus.CANCELLED && order.getCancelledAt() == null) {
            order.setCancelledAt(clock.instant());
        }
    }

    private OrderStatus toOrderStatus(String reservationStatus, AppLanguage language) {
        return switch (String.valueOf(reservationStatus)) {
            case "ACTIVE" -> OrderStatus.RESERVED;
            case "COMMITTED" -> OrderStatus.SHIPPED;
            case "RELEASED" -> OrderStatus.CANCELLED;
            case "EXPIRED" -> OrderStatus.EXPIRED;
            default -> throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=control.reservation failure=unknown_status");
        };
    }

    private boolean isReservationRejected(Order order, FeignException exception) {
        int status = exception.status();
        boolean isBusinessError = status == 400 || status == 404 || status == 409;

        return order.getStatus() == OrderStatus.RESERVING && isBusinessError;
    }

    private void markReservationRejected(Order order) {
        boolean isExpired = !order.getExpiresAt().isAfter(clock.instant());

        order.setStatus(isExpired ? OrderStatus.EXPIRED : OrderStatus.REJECTED);
        order.setFailureReason("order.stock.unavailable");
        order.setUpdatedAt(clock.instant());
    }

    private boolean isFinalStatus(OrderStatus status) {
        return status == OrderStatus.SHIPPED || status == OrderStatus.CANCELLED
                || status == OrderStatus.EXPIRED || status == OrderStatus.REJECTED;
    }

    private void postponeRecovery(Long orderId, AppLanguage language) {
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Order order = findOrderForUpdate(orderId, language);
            order.setUpdatedAt(clock.instant());
        });
    }

    private Order findOrder(Long orderId, AppLanguage language) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new AppNotFoundException(messageService.getMessage("order.order.not.found", language)));
    }

    private Order findOrderForUpdate(Long orderId, AppLanguage language) {
        return orderRepository.lock(orderId)
                .orElseThrow(() -> new AppNotFoundException(messageService.getMessage("order.order.not.found", language)));
    }

    private void requireActiveCompanyOwner(Long companyId, Long profileId, AppLanguage language) {
        CompanyClient.Ownership ownership = callRemoteService(
                "company.ownership-check",
                () -> companyClient.ownership(companyId, profileId),
                language
        );

        if (ownership == null || !companyId.equals(ownership.companyId())) {
            throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=company.ownership-check failure=invalid_response");
        }

        if (!ownership.exists()) {
            throw new AppNotFoundException(messageService.getMessage("order.company.not.found", language));
        }

        if (!ownership.owner() || !ownership.active()) {
            throw new AccessDeniedException(messageService.getMessage("order.company.owner.required", language));
        }
    }

    private <T> T callRemoteService(String operation, Supplier<T> remoteCall, AppLanguage language) {
        try {
            return remoteCall.get();
        } catch (FeignException exception) {
            if (exception.status() == 404) {
                throw new AppNotFoundException(messageService.getMessage("order.resource.not.found", language));
            }

            throw dependencyUnavailable(operation, exception, language);
        }
    }

    private OrderException dependencyUnavailable(String operation, FeignException exception, AppLanguage language) {
        // -1 odatda HTTP javobi kelmagan tarmoq xatosini bildiradi.
        return new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language), "operation=" + operation
                + " upstream_status=" + exception.status()
                + " failure=" + exception.getClass().getSimpleName());
    }

    private void validateSameRequest(Order order, String requestHash, AppLanguage language) {
        if (!requestHash.equals(order.getRequestHash())) {
            throw new AppConflictException(messageService.getMessage("order.idempotency.conflict", language));
        }
    }

    private void validateReservationDuration(Duration duration) {
        if (duration.isNegative() || duration.isZero() || duration.compareTo(MAX_RESERVATION_DURATION) > 0) {
            throw new IllegalArgumentException("order.reservation-ttl 0 dan katta va 30 kundan oshmasin");
        }
    }


    @Override
    public OrderResponse acceptLead(Long leadId, LeadAcceptRequest request,
            String authorization, AppLanguage language) {
        SellerWorkflowClient.LeadEnvelope envelope = callRemoteService(
                "lead.details", () -> leads.lead(leadId, authorization), language);
        if (envelope == null || !Boolean.TRUE.equals(envelope.success()) || envelope.data() == null
                || !leadId.equals(envelope.data().id()) || envelope.data().buyerId() == null
                || envelope.data().companyId() == null) {
            throw new AppServiceUnavailableException(messageService.getMessage("order.dependency.unavailable", language),
                    "operation=lead.details failure=invalid_response");
        }
        SellerWorkflowClient.Lead lead = envelope.data();
        Long sellerId = SpringSecurityUtil.getProfileId();
        if (!sellerId.equals(lead.sellerId())) {
            throw new AccessDeniedException(messageService.getMessage("order.access.denied", language));
        }
        requireActiveCompanyOwner(lead.companyId(), sellerId, language);
        validateLeadItems(lead, request, language);
        OrderCreateRequest createRequest = new OrderCreateRequest(lead.companyId(), "lead:" + leadId,
                lead.deliveryAddress(), null, null, lead.comment(), request.items());
        Long orderId = findOrCreateLeadOrder(lead, createRequest, language);
        OrderResponse response = resume(orderId, language);
        if (!Set.of(OrderStatus.RESERVED, OrderStatus.FINALIZING, OrderStatus.SHIPPED).contains(response.status())) {
            throw new AppConflictException(messageService.getMessage("sale.lead.unreserved", language));
        }
        return response;
    }

    private void validateLeadItems(SellerWorkflowClient.Lead lead, LeadAcceptRequest request, AppLanguage language) {
        if (lead.items() == null || lead.items().isEmpty()) {
            throw new AppConflictException(messageService.getMessage("sale.lead.invalid", language));
        }
        Map<Long, BigDecimal> requestedQuantities = new TreeMap<>();
        for (SellerWorkflowClient.Item item : lead.items()) {
            if (item.productId() == null || item.quantity() == null || item.quantity().signum() <= 0
                    || requestedQuantities.putIfAbsent(item.productId(), item.quantity()) != null) {
                throw new AppConflictException(messageService.getMessage("sale.lead.invalid", language));
            }
        }
        Set<Long> acceptedProductIds = new HashSet<>();
        for (OrderCreateRequest.Item item : request.items()) {
            if (!acceptedProductIds.add(item.productId())) {
                throw new AppConflictException(messageService.getMessage("order.product.duplicate", language));
            }
            BigDecimal requestedQuantity = requestedQuantities.get(item.productId());
            if (requestedQuantity == null || item.quantity().compareTo(requestedQuantity) > 0) {
                throw new AppConflictException(messageService.getMessage("sale.quantity.invalid", language));
            }
            if (item.quantity().compareTo(requestedQuantity) < 0 && !request.buyerAgreementConfirmed()) {
                throw new AppConflictException(messageService.getMessage("sale.agreement.required", language));
            }
        }
        if (!acceptedProductIds.equals(requestedQuantities.keySet())) {
            throw new AppConflictException(messageService.getMessage("sale.quantity.invalid", language));
        }
    }

    private Long findOrCreateLeadOrder(SellerWorkflowClient.Lead lead, OrderCreateRequest request, AppLanguage language) {
        Long leadId = lead.id();
        String hash = OrderRequestUtils.createRequestHash(request);
        Optional<Order> existing = orderRepository.findByLeadId(leadId);
        if (existing.isPresent()) {
            validateSameRequest(existing.get(), hash, language);
            return existing.get().getId();
        }
        if (!Set.of("NEW", "VIEWED", "CONTACTED").contains(lead.status())) {
            throw new AppConflictException(messageService.getMessage("sale.lead.invalid", language));
        }
        Order order = prepareOrder(request, lead.buyerId(), request.idempotencyKey(), hash, language);
        order.setLeadId(leadId);
        try {
            return transactionTemplate.execute(transaction -> orderRepository.saveAndFlush(order).getId());
        } catch (DataIntegrityViolationException exception) {
            Order saved = orderRepository.findByLeadId(leadId).orElseThrow(() -> exception);
            validateSameRequest(saved, hash, language);
            return saved.getId();
        }
    }
}
