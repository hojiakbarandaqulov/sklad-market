package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.banner.BannerCreate;
import org.example.dto.banner.BannerCreateResponse;
import org.example.dto.banner.BannerResponse;
import org.example.dto.banner.BannerUpdate;
import org.example.enums.AppLanguage;
import org.example.enums.PlacementCode;
import org.example.service.BannerService;
import org.springframework.cloud.client.loadbalancer.reactive.RetryableLoadBalancerExchangeFilterFunction;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/banners")
public class BannerController {
    private final BannerService bannerService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<BannerCreateResponse> addBanner(@RequestBody @Valid BannerCreate banner) {
        BannerCreateResponse bannerCreateResponse = bannerService.createBanner(banner);
        return ApiResponse.successResponse(bannerCreateResponse);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BannerResponse> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                              @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        BannerResponse bannerResponse = bannerService.upload(id, file, language);
        return ApiResponse.successResponse(bannerResponse);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<BannerCreateResponse> updateBanner(@PathVariable Long id, @RequestBody @Valid BannerUpdate update,
                                                          @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        BannerCreateResponse response = bannerService.updateBanner(id, update, language);
        return ApiResponse.successResponse(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteBanner(@PathVariable Long id,
                                             @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        bannerService.delete(id, language);
        return ApiResponse.successResponse(Boolean.TRUE);
    }

    @GetMapping("/getAll")
    public ApiResponse<List<BannerResponse>> getBanners(@RequestParam PlacementCode placementCode,
                                                        @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        List<BannerResponse> bannerResponse = bannerService.getBanners(placementCode, language);
        return ApiResponse.successResponse(bannerResponse);
    }
}
