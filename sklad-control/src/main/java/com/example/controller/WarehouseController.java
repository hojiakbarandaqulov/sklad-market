package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.warehouse.*;
import com.example.enums.AppLanguage;
import com.example.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/control/warehouses")
@PreAuthorize("hasRole('SELLER')")
public class WarehouseController {
    private final WarehouseService warehouseService;

    @PostMapping
    public ApiResponse<WarehouseDTO> create(@Valid @RequestBody WarehouseCreateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(warehouseService.create(request, language));
    }

    @GetMapping
    public ApiResponse<PageImpl<WarehouseDTO>> list(@Valid @ModelAttribute WarehouseFilter filter,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(warehouseService.list(filter, language));
    }

    @GetMapping("/{id}")
    public ApiResponse<WarehouseDTO> get(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(warehouseService.get(id, language));
    }

    @PatchMapping("/{id}")
    public ApiResponse<WarehouseDTO> update(@PathVariable Long id,
            @Valid @RequestBody WarehouseUpdateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(warehouseService.update(id, request, language));
    }

    @PostMapping("{id}/archive")
    public ApiResponse<WarehouseDTO> archive(@PathVariable Long id,
                                             @RequestHeader(value = "Accept-Language",defaultValue = "UZ")AppLanguage language){
        return ApiResponse.successResponse(warehouseService.archive(id,language));
    }

    @PostMapping("{id}/restore")
    public ApiResponse<WarehouseDTO> restore(@PathVariable Long id,
                                             @RequestHeader(value = "Accept-Language",defaultValue = "UZ")AppLanguage language){
        return ApiResponse.successResponse(warehouseService.restore(id,language));
    }
}
