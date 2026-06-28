package org.example.service;

import org.example.dto.ApiResponse;
import org.example.dto.AttachDto;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.web.multipart.MultipartFile;

public interface UsersService {
    ApiResponse<UsersDTO> getProfile(AppLanguage language);

    ApiResponse<UserContextResponse> getContext(AppLanguage language);

    ApiResponse<UsersUpdateRequestDTO> updateProfile(UsersUpdateRequestDTO profileDTO, AppLanguage language);

    ApiResponse<String> updatePhoto(UsersUpdatePhoto photoId, AppLanguage language);

    ApiResponse<UsersUpdatePhoto> getProfilePhoto(AppLanguage language);

    PageImpl<UsersResponse> getUsers(String q, GeneralStatus status, Roles roles, int page, int perPage, AppLanguage language);

    ApiResponse<String> setAdmin(Long userId, Roles role, AppLanguage language);

    ApiResponse<AdminUserDetailResponse> getUserById(Long userId, AppLanguage language);

    ApiResponse<String> blockUser(Long userId, String reason, AppLanguage language);

    ApiResponse<String> unblockUser(Long userId, AppLanguage language);

    ApiResponse<String> revokeUserSessions(Long userId, AppLanguage language);

    UsersProfile findByUserIdAndDeletedFalse(Long userId);

    Long countByStatusAndDeletedFalse(GeneralStatus generalStatus);

    void save(UsersProfile profile);

    ApiResponse<AttachDto> uploadFile(MultipartFile file, AppLanguage language);
}
