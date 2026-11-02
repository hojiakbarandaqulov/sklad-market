package org.example.service.impl;

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

    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductClient productClient,
            CompanyClient companyClient,
            ControlClient controlClient,
            Clock clock,
            PlatformTransactionManager transactionManager,
            OrderMapper orderMapper,
            @Value("${order.reservation-ttl}") Duration reservationDuration
    ) {
        validateReservationDuration(reservationDuration);

        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.companyClient = companyClient;
        this.controlClient = controlClient;
        this.clock = clock;
        this.orderMapper = orderMapper;
        this.reservationDuration = reservationDuration;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public OrderResponse create(OrderCreateRequest request) {
        Long buyerId = SpringSecurityUtil.getProfileId();
        String idempotencyKey = request.idempotencyKey().strip();
        String requestHash = OrderRequestUtils.createRequestHash(request);

        Long orderId = findOrCreateOrder(request, buyerId, idempotencyKey, requestHash);

        // Lokal buyurtma oldin saqlanadi. Control javobi yo'qolsa, shu ID bilan qayta uriniladi.
        OrderResponse response = resume(orderId);

        if (response.status() == OrderStatus.REJECTED) {
            throw conflict(response.failureReason());
        }

        return response;
    }

    @Override
    public Page<OrderResponse> getBuyerOrders(OrderFilter filter) {
        Long buyerId = SpringSecurityUtil.getProfileId();
        Specification<Order> specification = OrderSpecification.forBuyer(buyerId, filter);

        return findOrders(specification, filter);
    }

    @Override
    public Page<OrderResponse> getSellerOrders(OrderFilter filter) {
        Long sellerId = SpringSecurityUtil.getProfileId();

        if (filter.getCompanyId() == null) {
            throw new OrderException(400, "companyId majburiy");
        }

        requireActiveCompanyOwner(filter.getCompanyId(), sellerId);
        Specification<Order> specification = OrderSpecification.forSeller(filter);

        return findOrders(specification, filter);
    }

    @Override
    public OrderResponse get(Long orderId) {
        checkOrderAccess(orderId, false);
        return resume(orderId);
    }

    @Override
    public OrderResponse ship(Long orderId) {
        checkOrderAccess(orderId, true);
        resume(orderId);
        markOrderAsShipping(orderId);

        OrderResponse response = resume(orderId);

        if (response.status() != OrderStatus.SHIPPED) {
            throw conflict("Rezerv muddati tugagan yoki buyurtma bekor qilingan");
        }

        return response;
    }

    @Override
    public OrderResponse cancel(Long orderId) {
        checkOrderAccess(orderId, false);
        resume(orderId);
        markOrderAsCancelling(orderId);

        return resume(orderId);
    }

    @Override
    public OrderResponse resume(Long orderId) {
        // Bir buyurtmani jo'natish va bekor qilish qulf sabab bir vaqtda bajarilmaydi.
        return transactionTemplate.execute(transactionStatus -> synchronizeWithControl(orderId));
    }

    @Override
    public void recover() {
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
                resume(orderId);
            } catch (RuntimeException exception) {
                log.warn("Buyurtma sinxronlash kechiktirildi, orderId={}", orderId);
                postponeRecovery(orderId);
            }
        }
    }

    private Long findOrCreateOrder(
            OrderCreateRequest request,
            Long buyerId,
            String idempotencyKey,
            String requestHash
    ) {
        Optional<Order> existingOrder = orderRepository.findByBuyerIdAndIdempotencyKey(buyerId, idempotencyKey);

        if (existingOrder.isPresent()) {
            validateSameRequest(existingOrder.get(), requestHash);
            return existingOrder.get().getId();
        }

        Order newOrder = prepareOrder(request, buyerId, idempotencyKey, requestHash);

        try {
            return transactionTemplate.execute(transactionStatus -> orderRepository.saveAndFlush(newOrder).getId());
        } catch (DataIntegrityViolationException exception) {
            // Ikki bir xil so'rov parallel kelsa, birinchi saqlangan buyurtmadan foydalanamiz.
            Order savedOrder = orderRepository.findByBuyerIdAndIdempotencyKey(buyerId, idempotencyKey)
                    .orElseThrow(() -> exception);

            validateSameRequest(savedOrder, requestHash);
            return savedOrder.getId();
        }
    }

    private Order prepareOrder(
            OrderCreateRequest request,
            Long buyerId,
            String idempotencyKey,
            String requestHash
    ) {
        if (idempotencyKey.isEmpty()) {
            throw new OrderException(400, "idempotencyKey bo'sh bo'lmasin");
        }

        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Order order = new Order();

        order.setBuyerId(buyerId);
        order.setCompanyId(request.companyId());
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestHash(requestHash);
        order.setStatus(OrderStatus.RESERVING);
        order.setDeliveryAddress(OrderRequestUtils.normalizeText(request.deliveryAddress()));
        order.setNote(OrderRequestUtils.normalizeText(request.note()));
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt);
        order.setExpiresAt(createdAt.plus(reservationDuration));

        addOrderItems(order, request.items());
        return order;
    }

    private void addOrderItems(Order order, List<OrderCreateRequest.Item> requestedItems) {
        List<OrderCreateRequest.Item> sortedItems = new ArrayList<>(requestedItems);
        sortedItems.sort(Comparator.comparing(OrderCreateRequest.Item::productId));

        Set<Long> addedProductIds = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Long companySellerId = null;

        for (OrderCreateRequest.Item requestedItem : sortedItems) {
            if (!addedProductIds.add(requestedItem.productId())) {
                throw new OrderException(400, "Bitta productId ni takror yubormang");
            }

            ProductClient.ProductSnapshot product = callRemoteService(
                    () -> productClient.get(requestedItem.productId())
            );

            validateProduct(requestedItem, product, order.getCompanyId());
            validateSameSellerAndCurrency(order, companySellerId, product);

            companySellerId = product.sellerId();
            order.setCurrency(product.currency());

            OrderItem orderItem = createOrderItem(order, requestedItem, product);
            order.getItems().add(orderItem);
            totalAmount = totalAmount.add(orderItem.getLineAmount());
        }

        requireActiveCompanyOwner(order.getCompanyId(), companySellerId);
        order.setTotalAmount(totalAmount);
    }

    private void validateProduct(
            OrderCreateRequest.Item requestedItem,
            ProductClient.ProductSnapshot product,
            Long companyId
    ) {
        if (product == null || !requestedItem.productId().equals(product.id())
                || product.companyId() == null || product.sellerId() == null
                || product.name() == null || product.name().isBlank()) {
            throw OrderException.unavailable();
        }

        if (!companyId.equals(product.companyId())) {
            throw new OrderException(400, "Bitta buyurtmada faqat bitta kompaniya mahsulotlari bo'lsin");
        }

        if (!product.orderable() || product.price() == null
                || product.price().signum() <= 0 || product.currency() == null) {
            throw conflict("Mahsulot sotuvda emas yoki qat'iy sotuv narxi belgilanmagan");
        }

        BigDecimal price = product.price();
        boolean tooManyDecimalPlaces = price.stripTrailingZeros().scale() > 3;
        boolean tooManyIntegerDigits = price.precision() - price.scale() > 16;

        if (tooManyDecimalPlaces || tooManyIntegerDigits) {
            throw conflict("Mahsulot narxi ruxsat etilgan aniqlikdan oshgan");
        }

        if (product.minimumQuantity() != null) {
            BigDecimal minimumQuantity = BigDecimal.valueOf(product.minimumQuantity());

            if (requestedItem.quantity().compareTo(minimumQuantity) < 0) {
                throw new OrderException(400, "Mahsulotning minimal buyurtma miqdoriga rioya qiling");
            }
        }
    }

    private void validateSameSellerAndCurrency(
            Order order,
            Long companySellerId,
            ProductClient.ProductSnapshot product
    ) {
        if (order.getCurrency() != null && !order.getCurrency().equals(product.currency())) {
            throw new OrderException(400, "Bitta buyurtmada valyuta bir xil bo'lsin");
        }

        if (companySellerId != null && !companySellerId.equals(product.sellerId())) {
            throw OrderException.unavailable();
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

    private Page<OrderResponse> findOrders(Specification<Order> specification, OrderFilter filter) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Pageable pageable = PageRequest.of(filter.getPage() - 1, filter.getPerPage(), sort);

        return transactionTemplate.execute(transactionStatus -> {
            Page<Order> orders = orderRepository.findAll(specification, pageable);
            return orders.map(orderMapper::toResponse);
        });
    }

    private void checkOrderAccess(Long orderId, boolean sellerOnly) {
        Long profileId = SpringSecurityUtil.getProfileId();

        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Order order = findOrder(orderId);
            boolean isOrderBuyer = profileId.equals(order.getBuyerId());

            if (!sellerOnly && isOrderBuyer) {
                return;
            }

            if (!SpringSecurityUtil.isSeller()) {
                throw new OrderException(403, "Buyurtma sizga tegishli emas");
            }

            requireActiveCompanyOwner(order.getCompanyId(), profileId);
        });
    }

    private void markOrderAsShipping(Long orderId) {
        // Niyat alohida tranzaksiyada saqlanadi. Control ishlamasa, fon vazifasi shu joydan davom etadi.
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Order order = findOrderForUpdate(orderId);

            if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.SHIPPING) {
                return;
            }

            if (order.getStatus() != OrderStatus.RESERVED) {
                throw conflict("Bu holatdagi buyurtmani jo'natib bo'lmaydi");
            }

            order.setStatus(OrderStatus.SHIPPING);
            order.setUpdatedAt(clock.instant());
        });
    }

    private void markOrderAsCancelling(Long orderId) {
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Order order = findOrderForUpdate(orderId);
            OrderStatus currentStatus = order.getStatus();

            if (currentStatus == OrderStatus.CANCELLING || currentStatus == OrderStatus.CANCELLED
                    || currentStatus == OrderStatus.EXPIRED || currentStatus == OrderStatus.REJECTED) {
                return;
            }

            if (currentStatus != OrderStatus.RESERVED) {
                throw conflict("Jo'natilgan yoki jo'natilayotgan buyurtmani bekor qilib bo'lmaydi");
            }

            order.setStatus(OrderStatus.CANCELLING);
            order.setUpdatedAt(clock.instant());
        });
    }

    private OrderResponse synchronizeWithControl(Long orderId) {
        Order order = findOrderForUpdate(orderId);

        if (isFinalStatus(order.getStatus())) {
            return orderMapper.toResponse(order);
        }

        ControlClient.Envelope controlResponse;

        try {
            controlResponse = sendControlRequest(order);
        } catch (FeignException exception) {
            if (isReservationRejected(order, exception)) {
                markReservationRejected(order);
                return orderMapper.toResponse(order);
            }

            // Tarmoq xatosida yakuniy holatni taxmin qilmaymiz; saqlangan niyat keyin qayta tekshiriladi.
            throw OrderException.unavailable();
        }

        validateControlResponse(order, controlResponse);
        applyReservationStatus(order, controlResponse.data());

        return orderMapper.toResponse(order);
    }

    private ControlClient.Envelope sendControlRequest(Order order) {
        Long orderId = order.getId();
        Long companyId = order.getCompanyId();

        return switch (order.getStatus()) {
            case RESERVING -> controlClient.reserve(orderId, createReservationRequest(order));
            case SHIPPING -> controlClient.commit(orderId, companyId);
            case CANCELLING -> controlClient.release(orderId, companyId);
            case RESERVED -> controlClient.get(orderId, companyId);
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

    private void validateControlResponse(Order order, ControlClient.Envelope response) {
        if (response == null || !response.success() || response.data() == null) {
            throw OrderException.unavailable();
        }

        ControlClient.Reservation reservation = response.data();

        if (reservation.id() == null || reservation.id() <= 0
                || !order.getCompanyId().equals(reservation.companyId())
                || !order.getId().equals(reservation.orderId())) {
            throw OrderException.unavailable();
        }
    }

    private void applyReservationStatus(Order order, ControlClient.Reservation reservation) {
        OrderStatus newStatus = toOrderStatus(reservation.status());
        boolean isFinishingOrder = order.getStatus() == OrderStatus.SHIPPING
                || order.getStatus() == OrderStatus.CANCELLING;

        if (isFinishingOrder && newStatus == OrderStatus.RESERVED) {
            throw OrderException.unavailable();
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

    private OrderStatus toOrderStatus(String reservationStatus) {
        return switch (String.valueOf(reservationStatus)) {
            case "ACTIVE" -> OrderStatus.RESERVED;
            case "COMMITTED" -> OrderStatus.SHIPPED;
            case "RELEASED" -> OrderStatus.CANCELLED;
            case "EXPIRED" -> OrderStatus.EXPIRED;
            default -> throw OrderException.unavailable();
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
        order.setFailureReason("Mahsulot qoldig'i yetarli emas yoki rezerv yaratib bo'lmadi");
        order.setUpdatedAt(clock.instant());
    }

    private boolean isFinalStatus(OrderStatus status) {
        return status == OrderStatus.SHIPPED || status == OrderStatus.CANCELLED
                || status == OrderStatus.EXPIRED || status == OrderStatus.REJECTED;
    }

    private void postponeRecovery(Long orderId) {
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            Order order = findOrderForUpdate(orderId);
            order.setUpdatedAt(clock.instant());
        });
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(404, "Buyurtma topilmadi"));
    }

    private Order findOrderForUpdate(Long orderId) {
        return orderRepository.lock(orderId)
                .orElseThrow(() -> new OrderException(404, "Buyurtma topilmadi"));
    }

    private void requireActiveCompanyOwner(Long companyId, Long profileId) {
        CompanyClient.Ownership ownership = callRemoteService(
                () -> companyClient.ownership(companyId, profileId)
        );

        if (ownership == null || !companyId.equals(ownership.companyId())) {
            throw OrderException.unavailable();
        }

        if (!ownership.exists()) {
            throw new OrderException(404, "Kompaniya topilmadi");
        }

        if (!ownership.owner() || !ownership.active()) {
            throw new OrderException(403, "Faol kompaniya egasigagina ruxsat beriladi");
        }
    }

    private <T> T callRemoteService(Supplier<T> remoteCall) {
        try {
            return remoteCall.get();
        } catch (FeignException exception) {
            if (exception.status() == 404) {
                throw new OrderException(404, "Mahsulot yoki kompaniya topilmadi");
            }

            throw OrderException.unavailable();
        }
    }

    private void validateSameRequest(Order order, String requestHash) {
        if (!requestHash.equals(order.getRequestHash())) {
            throw conflict("Kalit boshqa buyurtma uchun ishlatilgan");
        }
    }

    private void validateReservationDuration(Duration duration) {
        if (duration.isNegative() || duration.isZero() || duration.compareTo(MAX_RESERVATION_DURATION) > 0) {
            throw new IllegalArgumentException("order.reservation-ttl 0 dan katta va 30 kundan oshmasin");
        }
    }

    private OrderException conflict(String message) {
        return new OrderException(409, message);
    }
}
