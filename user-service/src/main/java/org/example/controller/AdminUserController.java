package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.users.AdminUserBlockRequest;
import org.example.dto.users.AdminUserDetailResponse;
import org.example.dto.users.UsersResponse;
import org.example.enums.AppLanguage;
import org.example.enums.GeneralStatus;
import org.example.enums.Roles;
import org.example.service.UsersService;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final UsersService usersService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageImpl<UsersResponse>> getUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) GeneralStatus status,
            @RequestParam(required = false) Roles roles,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(
                usersService.getUsers(q, status, roles, page, perPage, language)
        );
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<AdminUserDetailResponse> getUser(@PathVariable Long userId,
                                                        @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return usersService.getUserById(userId, language);
    }

    @PutMapping("/{userId}/block")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<String> blockUser(@PathVariable Long userId,
                                         @RequestBody(required = false) @Valid AdminUserBlockRequest request,
                                         @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return usersService.blockUser(userId,  request.getReason(), language);
    }

    @PutMapping("/{userId}/unblock")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<String> unblockUser(@PathVariable Long userId,
                                           @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return usersService.unblockUser(userId, language);
    }

    @DeleteMapping("/{userId}/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<String> revokeSessions(@PathVariable Long userId,
                                              @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return usersService.revokeUserSessions(userId, language);
    }

    @PutMapping("/set-admin/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> setAdmin(@PathVariable Long userId,
                                        @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return usersService.setAdmin(userId, language);
    }

}
