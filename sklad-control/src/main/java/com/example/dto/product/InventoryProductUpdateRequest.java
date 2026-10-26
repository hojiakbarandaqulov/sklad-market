package com.example.dto.product;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

/** Faqat yuborilgan karta yoki ko'rinish yangilanadi. SKU, kompaniya va qoldiq o'zgarmaydi. */
@Getter @Setter
public class InventoryProductUpdateRequest {
    // Karta berilsa, uning barcha majburiy maydonlari yuboriladi.
    @Valid private ProductCardRequest product;
    private Boolean showInMarket;
}
