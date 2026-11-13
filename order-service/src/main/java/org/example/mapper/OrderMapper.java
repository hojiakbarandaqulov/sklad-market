package org.example.mapper;

import org.example.service.ResourceBundleService;
import lombok.RequiredArgsConstructor;
import org.example.dto.order.OrderResponse;
import org.example.entity.Order;
import org.example.enums.AppLanguage;
import org.example.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMapper {
    private final ResourceBundleService messageService;

    public OrderResponse toResponse(Order order, AppLanguage language) {
        List<OrderResponse.Item> items = new ArrayList<>();

        for (OrderItem orderItem : order.getItems()) {
            items.add(toItemResponse(orderItem));
        }

        // So'rovdan yaratilgan buyurtma so'rovdagi raqamni saqlaydi.
        String orderNumber = order.getLeadId() == null
                ? "ORD-" + order.getId()
                : "RQ-" + order.getLeadId();

        return new OrderResponse(
                order.getId(),
                orderNumber,
                order.getBuyerId(),
                order.getCompanyId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getDeliveryAddress(),
                order.getLongitude(),
                order.getLatitude(),
                order.getNote(),
                order.getReservationId(),
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getShippedAt(),
                order.getCancelledAt(),
                order.getFailureReason() == null ? null : messageService.getMessage(order.getFailureReason(), language),
                items
        );
    }

    private OrderResponse.Item toItemResponse(OrderItem orderItem) {
        return new OrderResponse.Item(
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getQuantity(),
                orderItem.getUnitPrice(),
                orderItem.getLineAmount()
        );
    }
}
