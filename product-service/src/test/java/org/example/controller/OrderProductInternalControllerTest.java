package org.example.controller;

import org.example.entity.Product;
import org.example.enums.Currency;
import org.example.enums.PriceType;
import org.example.enums.ProductModerationStatus;
import org.example.service.ProductService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderProductInternalControllerTest {
    ProductService products;
    OrderProductInternalController controller;
    Product product;
    @BeforeEach void setup() {
        products = mock(ProductService.class); controller = new OrderProductInternalController(products);
        product = new Product(); product.setId(100L); product.setCompanyId(1L); product.setSellerId(42L);
        product.setName("Steel"); product.setPrice(new BigDecimal("12500.125")); product.setCurrency(Currency.UZS);
        product.setMinProduct(2L); product.setModerationStatus(ProductModerationStatus.APPROVED); product.setPriceType(PriceType.FIXED);
        when(products.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(product));
    }
    @Test void approvedActiveFixedPriceProductSuppliesServerSideSnapshot() {
        var response = controller.summary(100L).getBody();
        assertThat(response).isNotNull(); assertThat(response.orderable()).isTrue();
        assertThat(response.price()).isEqualByComparingTo("12500.125"); assertThat(response.minimumQuantity()).isEqualTo(2L);
        assertThat(response.companyId()).isEqualTo(1L);
    }
    @Test void inactiveUnapprovedAndNegotiableProductsCannotBeOrdered() {
        product.setIsActive(false); assertThat(controller.summary(100L).getBody().orderable()).isFalse();
        product.setIsActive(true); product.setModerationStatus(ProductModerationStatus.PENDING);
        assertThat(controller.summary(100L).getBody().orderable()).isFalse();
        product.setModerationStatus(ProductModerationStatus.APPROVED);
        for (var type : new PriceType[]{PriceType.NEGOTIABLE, PriceType.FROM_PRICE}) {
            product.setPriceType(type); assertThat(controller.summary(100L).getBody().orderable()).isFalse();
        }
        product.setPriceType(PriceType.FIXED); product.setPrice(null);
        assertThat(controller.summary(100L).getBody().orderable()).isFalse();
        product.setPrice(BigDecimal.ZERO); assertThat(controller.summary(100L).getBody().orderable()).isFalse();
    }
    @Test void unknownOrDeletedCardIsNotFound() {
        when(products.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.empty());
        assertThat(controller.summary(100L).getStatusCode().value()).isEqualTo(404);
    }
}
