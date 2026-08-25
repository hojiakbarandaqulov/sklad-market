package org.example.ai.intent.service;

import org.example.ai.intent.dto.BuyingIntentMatchResponse;
import org.example.ai.intent.dto.BuyingIntentRequest;
import org.example.ai.intent.entity.BuyingIntent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class BuyingIntentPrivacyTest {

    @Test
    void persistenceAndPublicContracts_haveNoContactOrOwnerIdentityFields() {
        assertThat(fieldNames(BuyingIntent.class))
                .noneMatch(this::isRawContactField);
        assertThat(recordNames(BuyingIntentRequest.class))
                .noneMatch(this::isRawContactField);
        assertThat(recordNames(BuyingIntentMatchResponse.class))
                .noneMatch(name -> isRawContactField(name) || name.equalsIgnoreCase("ownerSub"));
    }

    private java.util.List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(java.lang.reflect.Field::getName).toList();
    }

    private java.util.List<String> recordNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private boolean isRawContactField(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("contact") || normalized.contains("phone") || normalized.contains("email")
                || normalized.contains("contactname") || normalized.contains("address");
    }
}
