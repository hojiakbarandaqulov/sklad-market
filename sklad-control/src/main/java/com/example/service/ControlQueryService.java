package com.example.service;

import com.example.dto.availability.StockAvailabilityRequest;
import com.example.dto.availability.StockAvailabilityResponse;
import com.example.dto.reservation.ReservationDTO;
import com.example.dto.reservation.ReservationFilter;
import com.example.dto.stockOperation.StockOperationDTO;
import com.example.dto.stockOperation.StockOperationFilter;
import com.example.enums.AppLanguage;
import org.springframework.data.domain.PageImpl;

import java.util.List;

public interface ControlQueryService {
    PageImpl<StockOperationDTO> operations(StockOperationFilter filter, AppLanguage language);
    PageImpl<ReservationDTO> reservations(ReservationFilter filter, AppLanguage language);
    List<StockAvailabilityResponse> availability(StockAvailabilityRequest request);
}
