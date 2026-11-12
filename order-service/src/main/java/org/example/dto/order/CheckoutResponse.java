package org.example.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.client.ControlClient;
import org.example.client.SaleCustomerClient;

import java.util.List;

@Schema(name = "Checkout")
public record CheckoutResponse(
        OrderResponse order,
        List<ControlClient.Availability> availability,
        List<SaleRequest.PaymentMethod> paymentMethods,
        SaleCustomerClient.Customer customer
) {
}
