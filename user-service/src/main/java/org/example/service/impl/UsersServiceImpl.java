package org.example.service.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.config.clent.CompanyClient;
import org.example.dto.ApiResponse;
import org.example.dto.internal.CompanyInternalSummaryResponse;
import org.example.dto.internal.CompanySummary;
import org.example.dto.kafka.UserUpdateRole;
import org.example.dto.kafka.UserUpdateStatus;
import org.example.dto.users.AdminUserDetailResponse;
import org.example.dto.users.UserContextResponse;
import org.example.dto.users.UsersDTO;
import org.example.dto.users.UsersResponse;
import org.example.dto.users.UsersUpdatePhoto;
import org.example.dto.users.UsersUpdateRequestDTO;
import org.example.entity.Attach;
import org.example.entity.Profile;
import org.example.enums.AppLanguage;
import org.example.enums.GeneralStatus;
import org.example.enums.Roles;
import org.example.exp.AppBadException;
import org.example.repository.UsersRepository;
import org.example.service.AttachService;
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
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersServiceImpl implements UsersService {

    private final UsersRepository profileRepository;
    private final ModelMapper modelMapper;
    private final ResourceBundleService messageService;
    private final AttachService attachService;
    protected final KeycloakService keycloakService;
    private final KafkaProducerService kafkaProducerService;
    private final RestTemplate restTemplate;
    private final UsersRepository usersRepository;
    private final CompanyClient companyClient;

    @Value("${aws.url}")
    private String awsUrl;

    @Override
    public ApiResponse<UsersDTO> getProfile(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        Profile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        UsersDTO map = modelMapper.map(profile, UsersDTO.class);
        return new ApiResponse<>(map);
    }

    @Override
    public ApiResponse<UserContextResponse> getContext(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        Profile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }

        UserContextResponse response = new UserContextResponse();
        response.setId(profile.getUserId());
        response.setFirstName(profile.getFirstName());
        response.setLastName(profile.getLastName());
        response.setUsername(profile.getUsername());
        response.setRole(profile.getRoles());
        response.setPhotoUrl(profile.getPhoto() == null ? null : awsUrl + profile.getPhoto().getPath());
        response.setSellerPanel(profile.getRoles() == Roles.SELLER);
        response.setModeratorPanel(profile.getRoles() == Roles.ADMIN || profile.getRoles() == Roles.SUPER_ADMIN);

        if (profile.getRoles() == Roles.SELLER) {
            try {
             /*   Long[] companyIds = restTemplate.getForObject(
                        "http://localhost:8083/internal/companies/owned?sellerId={sellerId}",
                        Long[].class,
                        profileId
                );*/
                List<Long> companyIds=companyClient.ownedCompany(profileId);
                if (companyIds != null) {
                 /*   CompanySummary companySummary = restTemplate.getForObject(
                            "http://localhost:8083/internal/companies/{companyId}/summary",
                            CompanySummary.class,
                            companyIds[0]
                    );*/
                    CompanyInternalSummaryResponse companySummary=companyClient.summary(companyIds.get(0));
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
        Profile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        modelMapper.map(profileDTO, profile);
        profileRepository.save(profile);
        UsersUpdateRequestDTO updateRequestDTO = modelMapper.map(profile, UsersUpdateRequestDTO.class);
        return new ApiResponse<>(updateRequestDTO);
    }

    @Override
    public ApiResponse<String> updatePhoto(UsersUpdatePhoto photoId, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        Profile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        if (profile.getPhoto() != null && profile.getPhoto().getId().equals(photoId.getPhotoId())) {
            attachService.delete(profile.getPhoto().getId(), language);
        }
        Attach attach = attachService.get(photoId.getPhotoId(), language);
        profileRepository.updatePhoto(profile.getId(), attach);
        return ApiResponse.successResponse(messageService.getMessage("photo.photo.update.success", language));
    }

    @Override
    public ApiResponse<UsersUpdatePhoto> getProfilePhoto(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        Profile profile = profileRepository.findByUserIdAndDeletedFalse(profileId);
        if (profile == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        if (profile.getPhoto() == null) {
            throw new AppBadException(messageService.getMessage("user.photo.not.found", language));
        }
        UsersUpdatePhoto photoUpdatePhoto = new UsersUpdatePhoto();
        photoUpdatePhoto.setPhotoId(String.valueOf(profile.getPhoto().getId()));
        return ApiResponse.successResponse(photoUpdatePhoto);
    }

    @Override
    public PageImpl<UsersResponse> getUsers(String q, GeneralStatus status, Roles roles, int page, int perPage, AppLanguage language) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page, language) - 1, normalizePerPage(perPage, language), Sort.Direction.DESC, "createdDate");
        Specification<Profile> spec = Specification.where(notDeleted());
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
        Page<Profile> result = profileRepository.findAll(spec, pageRequest);
        List<UsersResponse> items = result.getContent()
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(items, pageRequest, result.getTotalElements());
    }

    @Override
    public ApiResponse<String> setAdmin(Long userId, Roles role, AppLanguage language) {
        Profile profile = getByUserId(userId, language);
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
        Profile profile = getByUserId(userId, language);
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
        Profile profile = getByUserId(userId, language);
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
        Profile profile = getByUserId(userId, language);
        profile.setStatus(GeneralStatus.ACTIVE);
        profileRepository.save(profile);
        kafkaProducerService.sendUserStatusUpdate(new UserUpdateStatus(profile.getUserId(), profile.getStatus()));
        keycloakService.setUserEnabled(profile.getKeycloakId(), true);
        return ApiResponse.successResponse("user unblocked");
    }

    @Override
    public ApiResponse<String> revokeUserSessions(Long userId, AppLanguage language) {
        Profile profile = getByUserId(userId, language);
        keycloakService.revokeUserSessions(profile.getKeycloakId());
        return ApiResponse.successResponse("user sessions revoked");
    }

    @Override
    public Profile findByUserIdAndDeletedFalse(Long userId) {
        return usersRepository.findByUserIdAndDeletedFalse(userId);
    }

    @Override
    public Long countByStatusAndDeletedFalse(GeneralStatus generalStatus) {
        return usersRepository.countByStatusAndDeletedFalse(generalStatus);
    }

    @Override
    public void save(Profile profile) {
        usersRepository.save(profile);
    }

    private UsersResponse toResponse(Profile profile) {
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

    private Specification<Profile> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    private Profile getByUserId(Long userId, AppLanguage language) {
        Profile profile = profileRepository.findByUserIdAndDeletedFalse(userId);
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

    private String buildFullName(Profile profile) {
        String firstName = profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile.getLastName() == null ? "" : profile.getLastName().trim();
        return (firstName + " " + lastName).trim();
    }

}
