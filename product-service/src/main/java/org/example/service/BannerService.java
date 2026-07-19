package org.example.service;

import org.example.dto.banner.BannerCreate;
import org.example.dto.banner.BannerCreateResponse;
import org.example.dto.banner.BannerResponse;
import org.example.dto.banner.BannerUpdate;
import org.example.enums.AppLanguage;
import org.example.enums.PlacementCode;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BannerService {

    BannerCreateResponse createBanner(BannerCreate banner);

    BannerResponse upload(Long id, MultipartFile file, AppLanguage language);

    BannerCreateResponse updateBanner(Long id, BannerUpdate update, AppLanguage language);

    void delete(Long id, AppLanguage language);

    List<BannerResponse> getBanners(PlacementCode placementCode, AppLanguage language);

    List<BannerResponse> getAllBanners(AppLanguage language);

}
