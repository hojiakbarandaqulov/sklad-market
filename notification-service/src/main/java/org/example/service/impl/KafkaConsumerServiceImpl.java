package org.example.service.impl;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.example.dto.event.CompanyCreateEvent;
import org.example.dto.event.ProductCreatedEvent;
import org.example.dto.notification.CreateNotificationRequest;
import org.example.enums.NotificationCreateType;
import org.example.service.KafkaConsumerService;
import org.example.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@AllArgsConstructor
public class KafkaConsumerServiceImpl implements KafkaConsumerService {
    private final NotificationService notificationService;


    @KafkaListener(
            topics = "${app.kafka.topic.product-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            properties = {"spring.json.value.default.type=org.example.dto.event.ProductCreatedEvent"}
    )
    public void onProductCreated(ProductCreatedEvent event) {
        if (event == null || event.getSellerId() == null) return;

        CreateNotificationRequest req = new CreateNotificationRequest();
        req.setUserId(event.getSellerId()); // kimga notification boradi
        req.setType(NotificationCreateType.PRODUCT_CREATED);

        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", event.getProductId());
        payload.put("companyId", event.getCompanyId());
        payload.put("categoryId", event.getCategoryId());
        payload.put("name", event.getName());
        payload.put("slug", event.getSlug());
        payload.put("price", event.getPrice());
        payload.put("currency", event.getCurrency());
        payload.put("moderationStatus", event.getModerationStatus());
        payload.put("createdAt", event.getCreatedAt());
        req.setPayload(payload);

        notificationService.createInternal(req);
    }

    @KafkaListener(
            topics = "company.created",
            groupId = "${spring.kafka.consumer.group-id}",
            properties = {"spring.json.value.default.type=org.example.dto.event.CompanyCreateEvent"}
    )
    @Override
    public void onCompanyCreated(CompanyCreateEvent event) {
        if (event == null || event.getOwnerUserId() == null) return;

        CreateNotificationRequest req = new CreateNotificationRequest();
        req.setUserId(event.getOwnerUserId());
        req.setType(NotificationCreateType.COMPANY_CREATED);

        Map<String, Object> payload = new HashMap<>();
        payload.put("companyId", event.getCompanyId());
        payload.put("ownerUserId", event.getOwnerUserId());
        payload.put("companyName", event.getCompanyName());
        payload.put("companySlug", event.getCompanySlug());
        payload.put("verificationStatus", event.getVerificationStatus());
        payload.put("createdDate", event.getCreatedDate());
        req.setPayload(payload);
        notificationService.createInternal(req);

    }
}
