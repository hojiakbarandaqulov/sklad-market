package org.example.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.dto.PageMeta;
import org.example.dto.PagedResponse;
import org.example.dto.notification.*;
import org.example.entity.Notification;
import org.example.entity.NotificationPreference;
import org.example.entity.PushToken;
import org.example.entity.UserNotification;
import org.example.exp.AppBadException;
import org.example.repository.NotificationPreferenceRepository;
import org.example.repository.NotificationRepository;
import org.example.repository.PushTokenRepository;
import org.example.repository.UserNotificationRepository;
import org.example.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.example.utils.SpringSecurityUtil.getProfileId;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PushTokenRepository pushTokenRepository;
    private final ObjectMapper objectMapper;

    @Override
    public PagedResponse<NotificationResponse> getNotifications(Boolean isRead, int page, int perPage) {
        Long userId = requireCurrentUserId();
        validatePage(page, perPage);

        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), perPage, Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<Notification> notificationPage;

        if (isRead == null) {
            notificationPage = notificationRepository.findByUserIdAndDeletedFalse(userId, pageRequest);
        } else if (isRead) {
            notificationPage = notificationRepository.findByUserIdAndReadAtIsNotNullAndDeletedFalse(userId, pageRequest);
        } else {
            notificationPage = notificationRepository.findByUserIdAndReadAtIsNullAndDeletedFalse(userId, pageRequest);
        }
        List<NotificationResponse> items = notificationPage.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PagedResponse<>(items, new PageMeta(notificationPage.getTotalElements(), page, perPage, notificationPage.getTotalPages()));
    }

    @Override
    public UnreadCountResponse getUnreadCount() {
        Long userId = requireCurrentUserId();
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNullAndDeletedFalse(userId));
    }

    @Override
    @Transactional
    public void markRead(MarkReadRequest request) {
        Long userId = requireCurrentUserId();

        if (Boolean.TRUE.equals(request.getMarkAll())) {
            List<Notification> unreadNotifications = notificationRepository.findByUserIdAndReadAtIsNullAndDeletedFalse(userId);
            LocalDateTime now = LocalDateTime.now();
            unreadNotifications.forEach(notification -> notification.setReadAt(now));
            notificationRepository.saveAll(unreadNotifications);
            return;
        }

        if (request.getNotificationIds() == null || request.getNotificationIds().isEmpty()) {
            throw new AppBadException("notification_ids is required when mark_all is false");
        }

        List<Notification> notifications = notificationRepository.findByUserIdAndIdInAndDeletedFalse(userId, request.getNotificationIds());
        LocalDateTime now = LocalDateTime.now();
        notifications.forEach(notification -> notification.setReadAt(now));
        notificationRepository.saveAll(notifications);
    }

    @Override
    public NotificationPreferencesResponse getPreferences() {
        Long userId = requireCurrentUserId();
        return toPreferencesResponse(getOrCreatePreference(userId));
    }

    @Override
    @Transactional
    public NotificationPreferencesResponse updatePreferences(NotificationPreferencesRequest request) {
        Long userId = requireCurrentUserId();
        NotificationPreference preference = getOrCreatePreference(userId);
        preference.setInAppEnabled(request.getInApp());
        preference.setPushEnabled(request.getPush());
        preference.setEmailEnabled(request.getEmail());
        return toPreferencesResponse(preferenceRepository.save(preference));
    }

    @Override
    @Transactional
    public PushTokenResponse registerPushToken(PushTokenRequest request) {
        Long userId = requireCurrentUserId();
        PushToken pushToken = pushTokenRepository.findByTokenAndDeletedFalse(request.getToken())
                .orElseGet(PushToken::new);
        pushToken.setUserId(userId);
        pushToken.setToken(request.getToken());
        pushToken.setPlatform(request.getPlatform());
        PushToken saved = pushTokenRepository.save(pushToken);

        return PushTokenResponse.builder()
                .token(saved.getToken())
                .platform(saved.getPlatform().name().toLowerCase())
                .build();
    }

    @Override
    @Transactional
    public NotificationResponse createInternal(CreateNotificationRequest request) {
        try {
            Notification notification = new Notification();
            notification.setUserId(request.getUserId());
            notification.setType(request.getType());
            notification.setPayloadJson(objectMapper.writeValueAsString(request.getPayload()));
            notification.setSentAt(LocalDateTime.now());
            UserNotification byUserIdAndDeletedFalse = userNotificationRepository.findByUserIdAndDeletedFalse(requireCurrentUserId());
            if (byUserIdAndDeletedFalse==null) {
                List<Notification> list = new LinkedList<>();
                list.add(notification);
                UserNotification userNotification = new UserNotification();
                userNotification.setUserId(requireCurrentUserId());
                userNotification.setNotifications(list);
            }else {
                throw new AppBadException("notification_id.is.already.in.use");
            }
            return toResponse(notificationRepository.save(notification));
        } catch (Exception e) {
            throw new AppBadException("Failed to create notification");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        try {
            Map<String, Object> payload = objectMapper.readValue(notification.getPayloadJson(), new TypeReference<>() {
            });
            return NotificationResponse.builder()
                    .id(notification.getId())
                    .type(notification.getType())
                    .payload(payload)
                    .sentAt(notification.getSentAt())
                    .readAt(notification.getReadAt())
                    .build();
        } catch (Exception e) {
            throw new AppBadException("Failed to parse notification payload");
        }
    }

    private NotificationPreferencesResponse toPreferencesResponse(NotificationPreference preference) {
        return NotificationPreferencesResponse.builder()
                .inApp(preference.getInAppEnabled())
                .push(preference.getPushEnabled())
                .email(preference.getEmailEnabled())
                .build();
    }

    private NotificationPreference getOrCreatePreference(Long userId) {
        return preferenceRepository.findByUserIdAndDeletedFalse(userId)
                .orElseGet(() -> {
                    NotificationPreference preference = new NotificationPreference();
                    preference.setUserId(userId);
                    return preferenceRepository.save(preference);
                });
    }

    private Long requireCurrentUserId() {
        Long userId = getProfileId();
        if (userId == null) {
            throw new AppBadException("Unauthorized");
        }
        return userId;
    }

    private void validatePage(int page, int perPage) {
        if (page < 1) {
            throw new AppBadException("page must be greater than or equal to 1");
        }
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException("per_page must be between 1 and 100");
        }
    }
}
