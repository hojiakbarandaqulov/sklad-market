package org.example.service;

import org.example.dto.order.OrderCreateRequest;
import org.example.dto.order.OrderFilter;
import org.example.dto.order.OrderResponse;
import org.springframework.data.domain.Page;

public interface OrderService {

    OrderResponse create(OrderCreateRequest request);

    Page<OrderResponse> getBuyerOrders(OrderFilter filter);

    Page<OrderResponse> getSellerOrders(OrderFilter filter);

    OrderResponse get(Long orderId);

    OrderResponse ship(Long orderId);

    OrderResponse cancel(Long orderId);

    // Lokal buyurtma holatini Control'dagi rezerv holati bilan moslashtiradi.
    OrderResponse resume(Long orderId);

    // Aloqa uzilishi sabab tugamagan buyurtmalarni fon vazifasi orqali davom ettiradi.
    void recover();
}
