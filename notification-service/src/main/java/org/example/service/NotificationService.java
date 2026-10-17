package org.example.service;

import org.example.dto.PagedResponse;
import org.example.dto.notification.*;

public interface NotificationService {
    PagedResponse<NotificationResponse> getNotifications(Boolean isRead, int page, int perPage);

    UnreadCountResponse getUnreadCount();

    void markRead(MarkReadRequest request);

    NotificationPreferencesResponse getPreferences();

    NotificationPreferencesResponse updatePreferences(NotificationPreferencesRequest request);

    PushTokenResponse registerPushToken(PushTokenRequest request);

    void createInternal(CreateNotificationRequest request);
}
