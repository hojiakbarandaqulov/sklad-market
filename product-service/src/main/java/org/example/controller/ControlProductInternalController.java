package org.example.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.product.CreateProductRequest;
import org.example.dto.product.ProductResponse;
import org.example.enums.AppLanguage;
import org.example.service.ProductService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Control uchun karta amallari. Ichki URL ham SELLER JWT va kompaniya egaligini talab qiladi. */
@RestController
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('SELLER')")
@RequestMapping("/internal/control/products")
public class ControlProductInternalController {
    private final ProductService service;

    @GetMapping("/matching")
    public ApiResponse<List<Long>> matching(@RequestParam Long companyId,
            @RequestParam(required=false) String q, @RequestParam(required=false) Long categoryId,
            @RequestHeader(value="Accept-Language",defaultValue="UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.matchingControlCards(companyId,q,categoryId,language));
    }

    @PostMapping
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request,
            @RequestParam @NotBlank @Size(max = 100) String key,
            @RequestParam(defaultValue = "false") boolean visible,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.createForControl(request, key, visible, language));
    }

    // Ichki PUT to'liq kartani oladi. Front esa Control'dagi PATCH orqali murojaat qiladi.
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
            @Valid @RequestBody CreateProductRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.updateForControl(id, request, language));
    }

    @PostMapping("/{id}/visibility")
    public ApiResponse<ProductResponse> visibility(@PathVariable Long id, @RequestParam boolean visible,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.visibilityForControl(id, visible, language));
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> cards(@RequestParam Long companyId, @RequestParam List<Long> ids,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.cardsForControl(companyId, ids, language));
    }
}
