package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.CompanyResponseDTO;
import org.example.dto.favorite.FavoriteCountResponse;
import org.example.enums.AppLanguage;
import org.example.service.FavoriteService;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/company-favorites")
public class FavoriteController {
    private final FavoriteService favoriteService;

    @PostMapping("create/{companyId}")
    public ApiResponse<Boolean> createFavorite(@PathVariable Long companyId,
                                               @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        Boolean result = favoriteService.createFavorite(companyId, language);
        return ApiResponse.successResponse(result);
    }

    @GetMapping
    public ApiResponse<PageImpl<CompanyResponseDTO>> getFavorites(@RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int perPage,
                                                                  @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(favoriteService.getFavorites(page, perPage, language));
    }

    @GetMapping("count")
    public ApiResponse<FavoriteCountResponse> countFavorites(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        FavoriteCountResponse favoriteCountResponse = favoriteService.getCount(language);
        return ApiResponse.successResponse(favoriteCountResponse);
    }

    @DeleteMapping("delete/{companyId}")
    public ApiResponse<Boolean> removeFavorite(@PathVariable Long companyId,
                                               @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language){
       Boolean result= favoriteService.remove(companyId, language);
       return ApiResponse.successResponse(result);
    }
}
