package com.example.controller;

import com.example.enums.AppLanguage;
import com.example.service.InventoryProductService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/internal/control/product-links")
@PreAuthorize("hasAnyRole('SELLER','ADMIN','SUPER_ADMIN')")
public class InventoryProductLinkInternalController {
    private final InventoryProductService service;

    @GetMapping("/{productId}")
    public boolean linked(@PathVariable @Positive Long productId,
            @RequestParam @Positive Long companyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return service.hasCatalogLink(companyId, productId, language);
    }
}
