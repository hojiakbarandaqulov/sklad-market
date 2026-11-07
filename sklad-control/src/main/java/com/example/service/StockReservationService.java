package com.example.service;

import com.example.dto.reservation.ReservationCreateRequest;
import com.example.dto.reservation.ReservationDTO;
import com.example.dto.reservation.OrderReservationRequest;
import com.example.enums.AppLanguage;

public interface StockReservationService {
    ReservationDTO completeOrder(Long orderId, com.example.dto.reservation.OrderSaleRequest request);
    java.util.List<java.util.Map<String,Object>> orderAvailability(Long companyId, Long orderId);
    ReservationDTO reserveOrder(Long orderId, OrderReservationRequest request);
    ReservationDTO orderReservation(Long companyId, Long orderId, String action);
    ReservationDTO create(ReservationCreateRequest request, AppLanguage language);
    ReservationDTO get(Long id, AppLanguage language);
    ReservationDTO release(Long id, AppLanguage language);
    ReservationDTO commit(Long id, AppLanguage language);

    // Faqat ichki rejalashtiruvchi chaqiradi; tashqi API mavjud emas.
    void expire(Long id);
}
