package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.internal.AttachDto;
import org.example.dto.users.UserContextResponse;
import org.example.dto.users.UsersDTO;
import org.example.dto.users.UsersUpdatePhoto;
import org.example.dto.users.UsersUpdateRequestDTO;
import org.example.enums.AppLanguage;
import org.example.service.UsersService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UsersController {
    private final UsersService userService;

    @GetMapping
    public ApiResponse<UsersDTO> getProfile(@RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return userService.getProfile(language);
    }

    @GetMapping("/me/context")
    public ApiResponse<UserContextResponse> getContext(@RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return userService.getContext(language);
    }

    @PutMapping
    public ApiResponse<UsersUpdateRequestDTO> updateProfile(@RequestBody @Valid UsersUpdateRequestDTO profileDTO,
                                                            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return userService.updateProfile(profileDTO, language);
    }

    @PostMapping(value = "/upload/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachDto> upload(@RequestParam("file") MultipartFile file,
                                         @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return userService.uploadFile(file, language);
    }

    @PutMapping("/update/photo")
    public ApiResponse<String> updateProfilePhoto(@RequestBody @Valid UsersUpdatePhoto photoId,
                                                  @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return userService.updatePhoto(photoId, language);
    }

    @GetMapping("/photo")
    public ApiResponse<UsersUpdatePhoto> getProfilePhoto(@RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return userService.getProfilePhoto(language);
    }
}
