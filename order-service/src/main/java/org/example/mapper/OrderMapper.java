package org.example.mapper;

import org.example.dto.order.OrderResponse;
import org.example.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
    public OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getBuyerId(), order.getCompanyId(), order.getStatus(),
                order.getCurrency(), order.getTotalAmount(), order.getDeliveryAddress(), order.getNote(),
                order.getReservationId(), order.getExpiresAt(), order.getCreatedAt(), order.getUpdatedAt(),
                order.getShippedAt(), order.getCancelledAt(), order.getFailureReason(), order.getItems().stream()
                .map(item -> new OrderResponse.Item(item.getProductId(), item.getProductName(), item.getQuantity(),
                        item.getUnitPrice(), item.getLineAmount())).toList());
    }
}
