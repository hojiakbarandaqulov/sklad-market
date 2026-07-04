package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.clent.CompanyClient;
import org.example.config.clent.FileClient;
import org.example.dto.ApiResponse;
import org.example.dto.AttachDto;
import org.example.dto.internal.AttachInfoDto;
import org.example.dto.internal.CompanyInternalSummaryResponse;
import org.example.dto.kafka.UserUpdateRole;
import org.example.dto.kafka.UserUpdateStatus;
import org.example.dto.users.AdminUserDetailResponse;
import org.example.dto.users.UserContextResponse;
import org.example.dto.users.UsersDTO;
import org.example.dto.users.UsersResponse;
import org.example.dto.users.UsersUpdatePhoto;
import org.example.dto.users.UsersUpdateRequestDTO;
import org.example.entity.UsersProfile;
import org.example.enums.AppLanguage;
import org.example.enums.GeneralStatus;
import org.example.enums.Roles;
import org.example.exp.AppBadException;
import org.example.repository.UserProfileRepository;
import org.example.service.KafkaProducerService;
import org.example.service.KeycloakService;
import org.example.service.ResourceBundleService;
import org.example.service.UsersService;
import org.example.utils.SpringSecurityUtil;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersServiceImpl implements UsersService {

    private final UserProfileRepository profileRepository;
    private final ModelMapper modelMapper;
    private final ResourceBundleService messageService;
    private final FileClient fileClient;
    protected final KeycloakService keycloakService;
    private final KafkaProducerService kafkaProducerService;
    private final UserProfileRepository usersRepository;
    private final CompanyClient companyClient;

    @Value("${spring.media.base-url}")
    private String baseUrl;

    @Override
    public ApiResponse<UsersDTO> getProfile(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        UsersProfile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        UsersDTO map = modelMapper.map(profile, UsersDTO.class);
        return new ApiResponse<>(map);
    }

    @Override
    public ApiResponse<UserContextResponse> getContext(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        UsersProfile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }

        UserContextResponse response = new UserContextResponse();
        response.setId(profile.getUserId());
        response.setFirstName(profile.getFirstName());
        response.setLastName(profile.getLastName());
        response.setUsername(profile.getUsername());
        response.setRole(profile.getRoles());
        response.setSellerPanel(profile.getRoles() == Roles.SELLER);
        response.setModeratorPanel(profile.getRoles() == Roles.ADMIN || profile.getRoles() == Roles.SUPER_ADMIN);
        if (profile.getPhotoId() != null) {
            try {
                AttachInfoDto attachInfoDto = fileClient.getById(profile.getPhotoId());
                response.setPhotoUrl(attachInfoDto != null ? baseUrl + attachInfoDto.getPath() : null);
            } catch (Exception e) {
                log.warn("Photo olinmadi profileId={}", profileId);
                response.setPhotoUrl(null);
            }
        }
        if (profile.getRoles() == Roles.SELLER) {
            try {
                List<Long> companyIds = companyClient.ownedCompany(profileId);
                if (companyIds != null) {
                    CompanyInternalSummaryResponse companySummary = companyClient.summary(companyIds.get(0));
                    if (companySummary != null) {
                        response.setCompanyId(companySummary.getId());
                        response.setCompanyName(companySummary.getName());
                        response.setCompanyLogoUrl(companySummary.getLogoPath());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve company context for profileId={}", profileId, e);
            }
        }
        response.setCompanyProfile(response.getCompanyId() != null);
        return ApiResponse.successResponse(response);
    }

    @Override
    public ApiResponse<UsersUpdateRequestDTO> updateProfile(UsersUpdateRequestDTO profileDTO, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        UsersProfile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        modelMapper.map(profileDTO, profile);
        profileRepository.save(profile);
        UsersUpdateRequestDTO updateRequestDTO = modelMapper.map(profile, UsersUpdateRequestDTO.class);
        return new ApiResponse<>(updateRequestDTO);
    }

    @Override
    public ApiResponse<String> updatePhoto(UsersUpdatePhoto photo, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        UsersProfile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }

        if (profile.getPhotoId() != null && !profile.getPhotoId().equals(photo.getPhotoId())) {
            fileClient.delete(profile.getPhotoId(), language.name());
        }
        AttachInfoDto attach = fileClient.getById(photo.getPhotoId());
        profileRepository.updatePhoto(profile.getId(), attach.getId());
        return ApiResponse.successResponse(messageService.getMessage("photo.photo.update.success", language));
    }

    @Override
    public ApiResponse<UsersUpdatePhoto> getProfilePhoto(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        UsersProfile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        if (profile.getPhotoId() == null) {
            throw new AppBadException(messageService.getMessage("user.photo.not.found", language));
        }
        UsersUpdatePhoto photoUpdatePhoto = new UsersUpdatePhoto();
        photoUpdatePhoto.setPhotoId(String.valueOf(profile.getPhotoId()));
        return ApiResponse.successResponse(photoUpdatePhoto);
    }

    @Override
    public PageImpl<UsersResponse> getUsers(String q, GeneralStatus status, Roles roles, int page, int perPage, AppLanguage language) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page, language) - 1, normalizePerPage(perPage, language), Sort.Direction.DESC, "createdDate");
        Specification<UsersProfile> spec = Specification.where(notDeleted());
        if (q != null && !q.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstName")), "%" + q.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("lastName")), "%" + q.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("username")), "%" + q.toLowerCase() + "%")
            ));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (roles != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("roles"), roles));
        }
        Page<UsersProfile> result = profileRepository.findAll(spec, pageRequest);
        List<UsersResponse> items = result.getContent()
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(items, pageRequest, result.getTotalElements());
    }

    @Override
    public ApiResponse<String> setAdmin(Long userId, Roles role, AppLanguage language) {
        UsersProfile profile = getByUserId(userId, language);
        keycloakService.removeRole(profile.getKeycloakId(), profile.getRoles());
        profile.setRoles(role);
        profileRepository.save(profile);
        keycloakService.assignRoleToUser(profile.getKeycloakId(), role);
        log.info("Keycloak da role ADMIN ga yangilandi");
        kafkaProducerService.sendUserRoleUpdate(new UserUpdateRole(userId, role));
        return ApiResponse.successResponse("success");
    }

    @Override
    public ApiResponse<AdminUserDetailResponse> getUserById(Long userId, AppLanguage language) {
        UsersProfile profile = getByUserId(userId, language);
        AdminUserDetailResponse response = new AdminUserDetailResponse();
        response.setId(profile.getUserId());
        response.setFirstName(profile.getFirstName());
        response.setLastName(profile.getLastName());
        response.setFullName(buildFullName(profile));
        response.setUsername(profile.getUsername());
        response.setPosition(profile.getPosition());
        response.setTelegram(profile.getTelegram());
        response.setExtraPhone(profile.getExtraPhone());
        response.setStatus(profile.getStatus());
        response.setRoles(profile.getRoles());
        response.setWarningCount(profile.getWarningCount());
        response.setCreatedDate(profile.getCreatedDate());
        response.setModifiedDate(profile.getModifiedDate());
        return ApiResponse.successResponse(response);
    }

    @Override
    public ApiResponse<String> blockUser(Long userId, String reason, AppLanguage language) {
        UsersProfile profile = getByUserId(userId, language);
        profile.setStatus(GeneralStatus.BLOCK);
        profileRepository.save(profile);
        kafkaProducerService.sendUserStatusUpdate(new UserUpdateStatus(profile.getUserId(), profile.getStatus()));
        keycloakService.setUserEnabled(profile.getKeycloakId(), false);
        keycloakService.revokeUserSessions(profile.getKeycloakId());
        log.info("User blocked: userId={}, reason={}", userId, reason);
        return ApiResponse.successResponse("user blocked");
    }

    @Override
    public ApiResponse<String> unblockUser(Long userId, AppLanguage language) {
        UsersProfile profile = getByUserId(userId, language);
        profile.setStatus(GeneralStatus.ACTIVE);
        profileRepository.save(profile);
        kafkaProducerService.sendUserStatusUpdate(new UserUpdateStatus(profile.getUserId(), profile.getStatus()));
        keycloakService.setUserEnabled(profile.getKeycloakId(), true);
        return ApiResponse.successResponse("user unblocked");
    }

    @Override
    public ApiResponse<String> revokeUserSessions(Long userId, AppLanguage language) {
        UsersProfile profile = getByUserId(userId, language);
        keycloakService.revokeUserSessions(profile.getKeycloakId());
        return ApiResponse.successResponse("user sessions revoked");
    }

    @Override
    public UsersProfile findByUserIdAndDeletedFalse(Long userId) {
        return usersRepository.findByUserIdAndDeletedFalse(userId);
    }

    @Override
    public Long countByStatusAndDeletedFalse(GeneralStatus generalStatus) {
        return usersRepository.countByStatusAndDeletedFalse(generalStatus);
    }

    @Override
    public ApiResponse<AttachDto> uploadFile(MultipartFile file, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        ApiResponse<AttachDto> upload = fileClient.upload(file, language.name());
        UsersProfile profile = getByUserId(profileId, language);
        profile.setPhotoId(upload.getData().getId());
        usersRepository.save(profile);
        return upload;
    }

    @Override
    public void save(UsersProfile profile) {
        usersRepository.save(profile);
    }

    private UsersResponse toResponse(UsersProfile profile) {
        UsersResponse usersResponse = new UsersResponse();
        usersResponse.setId(profile.getUserId());
        usersResponse.setName(buildFullName(profile));
        usersResponse.setUsername(profile.getUsername());
        usersResponse.setStatus(profile.getStatus());
        usersResponse.setRoles(profile.getRoles());
        usersResponse.setWarningCount(profile.getWarningCount());
        usersResponse.setCreatedDate(profile.getCreatedDate());
        return usersResponse;
    }

    private Specification<UsersProfile> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    private UsersProfile getByUserId(Long userId, AppLanguage language) {
        UsersProfile profile = profileRepository.findByUserIdAndDeletedFalse(userId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        return profile;
    }

    private int normalizePage(int page, AppLanguage language) {
        if (page < 1) {
            throw new AppBadException(messageService.getMessage("page.invalid", language));
        }
        return page;
    }

    private int normalizePerPage(int perPage, AppLanguage language) {
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("per.page.invalid", language));
        }
        return perPage;
    }

    private String buildFullName(UsersProfile profile) {
        String firstName = profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile.getLastName() == null ? "" : profile.getLastName().trim();
        return (firstName + " " + lastName).trim();
    }

}
