package org.example.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InvoiceResponse(String number, Long orderId, Long companyId, Long buyerId,
                              Long leadId, String currency, String buyerName, String buyerPhone,
                              SaleRequest.DeliveryMethod deliveryMethod, String deliveryAddress,
                              SaleRequest.PaymentMethod paymentMethod, String paymentStatus, Long customerId,
                              String contractNumber, LocalDate paymentDueDate, String attachmentId,
                              BigDecimal totalAmount, Instant issuedAt, List<Item> items) {
    public record Item(Long productId, String productName, BigDecimal quantity,
                       BigDecimal unitPrice, BigDecimal lineAmount) {
    }
}
