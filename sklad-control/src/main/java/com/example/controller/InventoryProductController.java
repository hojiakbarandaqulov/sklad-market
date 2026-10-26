package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.product.*;
import com.example.enums.AppLanguage;
import com.example.service.InventoryProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Sotuvchining ombor mahsulotlari. Front yaratish tugmasidan faqat shu controller'ga murojaat qiladi.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/control/products")
@PreAuthorize("hasRole('SELLER')")
public class InventoryProductController {
    private final InventoryProductService service;

    @PostMapping
    public ApiResponse<InventoryProductDTO> create(@Valid @RequestBody InventoryProductCreateRequest request,
                                                   @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.create(request, language));
    }

    @GetMapping
    public ApiResponse<PageImpl<InventoryProductDTO>> list(@Valid @ModelAttribute InventoryProductFilter filter,
                                                           @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.list(filter, language));
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryProductDTO> get(@PathVariable Long id,
                                                @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.get(id, language));
    }

    @PatchMapping("/{id}")
    public ApiResponse<InventoryProductDTO> update(@PathVariable Long id,
                                                   @Valid @RequestBody InventoryProductUpdateRequest request,
                                                   @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.update(id, request, language));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<InventoryProductDTO> archive(@PathVariable Long id,
                                                    @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.archive(id, language));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<InventoryProductDTO> restore(@PathVariable Long id,
                                                    @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.restore(id, language));
    }
}
