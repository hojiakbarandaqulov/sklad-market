package com.example.service;

import com.example.dto.reservation.ReservationCreateRequest;
import com.example.dto.reservation.ReservationDTO;
import com.example.enums.AppLanguage;

public interface StockReservationService {
    ReservationDTO create(ReservationCreateRequest request, AppLanguage language);
    ReservationDTO get(Long id, AppLanguage language);
    ReservationDTO release(Long id, AppLanguage language);
    ReservationDTO commit(Long id, AppLanguage language);

    // Faqat ichki rejalashtiruvchi chaqiradi; tashqi API mavjud emas.
    void expire(Long id);
}
