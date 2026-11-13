package org.example.service;

import org.example.dto.order.OrderCreateRequest;
import org.example.dto.order.LeadAcceptRequest;
import org.example.enums.AppLanguage;
import org.example.dto.order.OrderFilter;
import org.example.dto.order.OrderResponse;
import org.springframework.data.domain.Page;
import org.example.enums.OrderStatus;
import java.util.List;
import java.util.Map;

public interface OrderService {
    Map<Long, OrderStatus> getLeadOrderStatuses(List<Long> orderIds, AppLanguage language);

    OrderResponse create(OrderCreateRequest request, AppLanguage language);

    OrderResponse acceptLead(Long leadId, LeadAcceptRequest request, String authorization, AppLanguage language);

    Page<OrderResponse> getBuyerOrders(OrderFilter filter, AppLanguage language);

    Page<OrderResponse> getSellerOrders(OrderFilter filter, AppLanguage language);

    OrderResponse get(Long orderId, AppLanguage language);

    OrderResponse ship(Long orderId, AppLanguage language);

    OrderResponse cancel(Long orderId, AppLanguage language);

    // Lokal buyurtma holatini Control'dagi rezerv holati bilan moslashtiradi.
    OrderResponse resume(Long orderId, AppLanguage language);

    // Aloqa uzilishi sabab tugamagan buyurtmalarni fon vazifasi orqali davom ettiradi.
    void recover(AppLanguage language);
}
