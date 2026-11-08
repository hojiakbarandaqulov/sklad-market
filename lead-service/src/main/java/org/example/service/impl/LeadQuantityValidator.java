package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.internal.ProductInternalSummaryResponse;
import org.example.enums.AppLanguage;
import org.example.exp.AppBadException;
import org.example.service.ResourceBundleService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class LeadQuantityValidator {
    private final ResourceBundleService messages;

    public void validate(ProductInternalSummaryResponse product, BigDecimal quantity, AppLanguage language) {
        if (product.getMinimumQuantity() != null
                && quantity.compareTo(BigDecimal.valueOf(product.getMinimumQuantity())) < 0) {
            throw new AppBadException(messages.getMessage("lead.quantity.minimum", language));
        }
    }
}
