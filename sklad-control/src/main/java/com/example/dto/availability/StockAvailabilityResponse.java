package com.example.dto.availability;

import java.math.BigDecimal;

/** Market uchun faqat sotuvga mavjud miqdor; xususiy rezerv va umumiy qoldiq chiqarilmaydi. */
public record StockAvailabilityResponse(
        Long companyId, Long productId, boolean tracked, BigDecimal availableQuantity, boolean inStock
) { }
