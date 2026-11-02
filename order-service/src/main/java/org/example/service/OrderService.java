package org.example.service;

import org.example.dto.order.OrderCreateRequest;
import org.example.dto.order.OrderFilter;
import org.example.dto.order.OrderResponse;
import org.springframework.data.domain.Page;

public interface OrderService {
    OrderResponse create(OrderCreateRequest request);
    Page<OrderResponse> list(OrderFilter filter, boolean seller);
    OrderResponse get(Long id);
    OrderResponse ship(Long id);
    OrderResponse cancel(Long id);
    OrderResponse resume(Long id);
    void recover();
}
