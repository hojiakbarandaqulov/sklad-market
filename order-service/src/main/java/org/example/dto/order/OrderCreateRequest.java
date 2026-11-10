package org.example.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreateRequest(

        @Schema(description = "Sotuvchi kompaniyasining ID'si. Barcha mahsulotlar shu kompaniyaga tegishli bo'lsin", example = "1")
        @NotNull
        @Positive
        Long companyId,

        @Schema(description = "Xaridor ichida yagona so'rov kaliti. Retry paytida o'zgartirmang", example = "checkout-20261102-001")
        @NotBlank
        @Size(max = 128)
        String idempotencyKey,

        @Schema(description = "Yetkazib berish manzili, ixtiyoriy", example = "Toshkent, Yunusobod tumani")
        @Size(max = 2000)
        String deliveryAddress,

        @Schema(description = "Geografik uzunlik, -180 dan 180 gacha", example = "69.240073", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal longitude,

        @Schema(description = "Geografik kenglik, -90 dan 90 gacha", example = "41.299496", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal latitude,

        @Schema(description = "Buyurtmaga izoh, ixtiyoriy", example = "Yetkazishdan oldin qo'ng'iroq qiling")
        @Size(max = 2000)
        String note,

        @Schema(description = "1–100 ta mahsulot pozitsiyasi. Bir productId takrorlanmasin")
        @NotEmpty
        @Size(max = 100)
        List<@NotNull @Valid Item> items
) {

    @Schema(name = "OrderCreateItem")
    public record Item(

            @Schema(description = "Market mahsulot ID'si; Control inventoryProductId emas", example = "100")
            @NotNull
            @Positive
            Long productId,

            @Schema(description = "Musbat miqdor, ko'pi bilan 3 kasr xonasi. Minimal buyurtma miqdoriga rioya qiling", example = "2.125")
            @NotNull
            @Positive
            @Digits(integer = 16, fraction = 3)
            BigDecimal quantity
    ) {
    }
}
