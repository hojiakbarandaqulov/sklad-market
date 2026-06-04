package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.PagedResponse;
import org.example.dto.favorite.FavoriteCountResponse;
import org.example.dto.favorite.FavoriteResponse;
import org.example.dto.product.ProductResponse;
import org.example.enums.AppLanguage;
import org.example.service.FavoriteService;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/product-favorites")
public class FavoriteController {
    private final FavoriteService favoriteService;

    @GetMapping
    public ApiResponse<PageImpl<ProductResponse>> getFavorites(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int perPage,
                                                               @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(favoriteService.getFavorites(page, perPage-1, language));
    }

    @GetMapping("/count")
    public ApiResponse<FavoriteCountResponse> getCount(@RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(favoriteService.getCount(language));
    }

    @PostMapping("/{productId}")
    public ApiResponse<FavoriteResponse> add(@PathVariable Long productId,
                                             @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(favoriteService.add(productId, language));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<FavoriteResponse> remove(@PathVariable Long productId,
                                                @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(favoriteService.remove(productId, language));
    }
}
