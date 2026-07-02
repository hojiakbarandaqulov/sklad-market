package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.config.clent.FileClient;
import org.example.dto.internal.AttachInfoDto;
import org.example.dto.internal.UserProfileSummaryResponse;
import org.example.entity.UsersProfile;
import org.example.enums.GeneralStatus;
import org.example.exp.AppBadException;
import org.example.service.UsersService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/profiles")
public class UserInternalController {
    private final UsersService usersService;
    private final FileClient fileClient;

    @Value("${aws.url}")
    private String awsUrl;

    @GetMapping("/{userId}/summary")
    public UserProfileSummaryResponse summary(@PathVariable Long userId) {
        UsersProfile profile = usersService.findByUserIdAndDeletedFalse(userId);
        if (profile == null) {
            throw new AppBadException("profile not found");
        }

        AttachInfoDto attachInfoDto = fileClient.getById(profile.getPhotoId());

        String photoUrl = attachInfoDto.getId() == null ? null : awsUrl + "/" + attachInfoDto.getPath();
        return UserProfileSummaryResponse.builder()
                .id(profile.getUserId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .username(profile.getUsername())
                .photoUrl(photoUrl)
                .build();
    }

    @PostMapping("/{userId}/warning")
    public void increaseWarning(@PathVariable Long userId) {
        UsersProfile profile = usersService.findByUserIdAndDeletedFalse(userId);
        if (profile == null) {
            throw new AppBadException("profile not found");
        }
        if (profile.getWarningCount() == null) {
            profile.setWarningCount(1);
        } else {
            profile.setWarningCount(profile.getWarningCount() + 1);
        }
        usersService.save(profile);
    }

    @GetMapping("/stats/blocked-count")
    public Long blockedCount() {
        return usersService.countByStatusAndDeletedFalse(GeneralStatus.BLOCK);
    }
}
