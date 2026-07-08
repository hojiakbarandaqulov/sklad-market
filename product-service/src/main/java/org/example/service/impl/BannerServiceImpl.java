package org.example.service.impl;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.example.client.FileClient;
import org.example.client.dto.AttachDto;
import org.example.dto.ApiResponse;
import org.example.dto.banner.BannerCreate;
import org.example.dto.banner.BannerCreateResponse;
import org.example.dto.banner.BannerResponse;
import org.example.dto.banner.BannerUpdate;
import org.example.entity.Banners;
import org.example.enums.AppLanguage;
import org.example.enums.PlacementCode;
import org.example.exp.AppBadException;
import org.example.repository.BannerRepository;
import org.example.service.BannerService;
import org.example.service.ResourceBundleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class BannerServiceImpl implements BannerService {

    @Value("${app.media.base-url}")
    private String mediaBaseUrl;

    private final BannerRepository bannerRepository;
    private final FileClient fileClient;
    private final ResourceBundleService messageService;


    @Override
    public BannerCreateResponse createBanner(BannerCreate banner) {
        Banners bannerEntity = new Banners();
        bannerEntity.setPlacementCode(banner.getPlacementCode());
        bannerEntity.setTargetUrl(banner.getTargetUrl());
        bannerEntity.setStartsAt(banner.getStartsAt());
        bannerEntity.setEndsAt(banner.getEndsAt());
        bannerEntity.setIsActive(true);
        bannerRepository.save(bannerEntity);
        BannerCreateResponse bannerCreateResponse = new BannerCreateResponse();
        bannerCreateResponse.setId(bannerEntity.getId());
        bannerCreateResponse.setTargetUrl(banner.getTargetUrl());
        bannerCreateResponse.setPlacementCode(banner.getPlacementCode());
        bannerCreateResponse.setStartsAt(banner.getStartsAt());
        bannerCreateResponse.setEndsAt(banner.getEndsAt());
        bannerCreateResponse.setIsActive(bannerEntity.getIsActive());
        return bannerCreateResponse;
    }

    @Override
    public BannerResponse upload(Long id, MultipartFile file, AppLanguage language) {

        String originalName = file.getOriginalFilename();
        assert originalName != null;
//        String extension = originalName.substring(originalName.lastIndexOf('.') + 1);
        String storageKey = UUID.randomUUID().toString();
        try (InputStream inputStream = file.getInputStream()) {
          /*  minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );*/
            ApiResponse<AttachDto> upload = fileClient.upload(file, language.name());
        } catch (Exception e) {
            throw new AppBadException(messageService.getMessage("banner.image.upload.failed", language));
        }
        Optional<Banners> banners = bannerRepository.findById(id);
        if (banners.isEmpty()) {
            throw new AppBadException(messageService.getMessage("banner.not.found", language));
        }
        Banners bannerEntity = banners.get();
        bannerEntity.setImageKey(storageKey);
        bannerRepository.save(bannerEntity);
        BannerResponse bannerResponse = new BannerResponse();
        bannerResponse.setId(bannerEntity.getId());
        bannerResponse.setTargetUrl(bannerEntity.getTargetUrl());
        bannerResponse.setImageUrl(mediaBaseUrl + storageKey);
        return bannerResponse;
    }

    @Override
    public BannerCreateResponse updateBanner(Long id, BannerUpdate update, AppLanguage language) {
        Optional<Banners> banners = bannerRepository.findById(id);
        if (banners.isEmpty()) {
            throw new AppBadException(messageService.getMessage("banner.not.found", language));
        }
        Banners bannerEntity = banners.get();
        bannerEntity.setTargetUrl(update.getTargetUrl());
        bannerEntity.setEndsAt(update.getEndsAt());
        bannerEntity.setIsActive(update.getIsActive());
        bannerRepository.save(bannerEntity);
        return null;
    }

    @Override
    public void delete(Long id, AppLanguage language) {
        Optional<Banners> byId = bannerRepository.findById(id);
        if (byId.isEmpty()) {
            throw new AppBadException(messageService.getMessage("banner.not.found", language));
        }
        Banners bannerEntity = byId.get();
        try {
          /*  minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(bannerEntity.getImageKey())
                            .build()
            );*/
            fileClient.delete(bannerEntity.getImageKey(),language.name());
        } catch (Exception e) {
            throw new AppBadException(messageService.getMessage("banner.delete.failed", language));
        }
        bannerRepository.deleteById(id);
    }

    @Override
    public List<BannerResponse> getBanners(PlacementCode placementCode, AppLanguage language) {
        LocalDateTime now = LocalDateTime.now();
        List<Banners> banners = bannerRepository.findByPlacementCodeAndIsActiveTrueAndStartsAtBeforeAndEndsAtAfter(placementCode, now, now);
        return banners.stream().map(
                this::toDTO).toList();
    }

    @Override
    public List<BannerResponse> getAllBanners(AppLanguage language) {
        List<Banners> all = bannerRepository.findAll();
        return all.stream().map(
                this::toDTO
        ).toList();
    }

    private BannerResponse toDTO(Banners banners) {
        BannerResponse dto = new BannerResponse();
        dto.setId(banners.getId());
        dto.setTargetUrl(banners.getTargetUrl());
        dto.setImageUrl(mediaBaseUrl + banners.getImageKey());
        return dto;
    }
}
