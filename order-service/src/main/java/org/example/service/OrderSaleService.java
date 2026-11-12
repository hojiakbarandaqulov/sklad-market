package org.example.service;

import org.example.dto.order.CheckoutResponse;
import org.example.dto.order.InvoiceResponse;
import org.example.dto.order.SaleRequest;
import org.example.enums.AppLanguage;

public interface OrderSaleService {
    CheckoutResponse checkout(Long orderId, Long customerId, String authorization, AppLanguage language);

    InvoiceResponse complete(Long orderId, SaleRequest request, String authorization, AppLanguage language);

    InvoiceResponse invoice(Long orderId, AppLanguage language);

    void recover(AppLanguage language);
}
