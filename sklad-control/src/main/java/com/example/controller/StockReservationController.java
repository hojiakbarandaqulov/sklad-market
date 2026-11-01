package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.reservation.ReservationCreateRequest;
import com.example.dto.reservation.ReservationDTO;
import com.example.dto.reservation.ReservationFilter;
import com.example.enums.AppLanguage;
import com.example.service.StockReservationService;
import com.example.service.ControlQueryService;
import org.springframework.data.domain.PageImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/control/reservations")
@PreAuthorize("hasRole('SELLER')")
public class StockReservationController {
    private final StockReservationService service;
    private final ControlQueryService queryService;

    @GetMapping
    public ApiResponse<PageImpl<ReservationDTO>> list(@Valid @ModelAttribute ReservationFilter filter,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(queryService.reservations(filter, language));
    }

    @PostMapping
    public ApiResponse<ReservationDTO> create(@Valid @RequestBody ReservationCreateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.create(request, language));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReservationDTO> get(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.get(id, language));
    }

    @PostMapping("/{id}/release")
    public ApiResponse<ReservationDTO> release(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.release(id, language));
    }

    @PostMapping("/{id}/commit")
    public ApiResponse<ReservationDTO> commit(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.commit(id, language));
    }
}
