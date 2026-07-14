package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.*;
import org.example.enums.AppLanguage;
import org.example.service.CartService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasAnyRole('BUYER','SELLER')")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ApiResponse<CartResponse> getCart(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(cartService.getCart(language));
    }

    @GetMapping("/count")
    public ApiResponse<CartCountResponse> getCartCount(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(cartService.getCartCount(language));
    }

    @PostMapping("/items")
    public ApiResponse<CartItemResponse> addItem(
            @Valid @RequestBody CartItemCreateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(cartService.addItem(request, language));
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<CartItemResponse> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestBody CartItemUpdateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(cartService.updateItem(itemId, request, language));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Boolean> deleteItem(
            @PathVariable Long itemId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(cartService.deleteItem(itemId, language));
    }

    @DeleteMapping
    public ApiResponse<Boolean> clear(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(cartService.clear(language));
    }

    @PostMapping("/checkout-rfq")
    public ApiResponse<CartCheckoutResponse> checkout(
            @Valid @RequestBody CartCheckoutRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(cartService.checkout(request, language));
    }
}
