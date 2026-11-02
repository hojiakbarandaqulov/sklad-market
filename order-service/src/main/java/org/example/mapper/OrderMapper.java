package org.example.mapper;

import org.example.dto.order.OrderResponse;
import org.example.entity.Order;
import org.example.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        List<OrderResponse.Item> items = new ArrayList<>();

        for (OrderItem orderItem : order.getItems()) {
            items.add(toItemResponse(orderItem));
        }

        return new OrderResponse(
                order.getId(),
                order.getBuyerId(),
                order.getCompanyId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getDeliveryAddress(),
                order.getNote(),
                order.getReservationId(),
                order.getExpiresAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getShippedAt(),
                order.getCancelledAt(),
                order.getFailureReason(),
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
