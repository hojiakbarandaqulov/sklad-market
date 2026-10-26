package com.example.dto.product;
import com.example.enums.InventoryProductStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** Qidiruv artikul bo'yicha; kategoriya va nom filtrlari product-service orqali qo'llanadi. */
@Getter @Setter
public class InventoryProductFilter {
    @NotNull @Positive private Long companyId;
    @Positive private Long warehouseId;
    @Positive private Long categoryId;
    @Size(max=160) private String q;
    private InventoryProductStatus status;
    // ALL, LOW, OUT, AVAILABLE qoldiq holatlari.
    @Pattern(regexp="ALL|LOW|OUT|AVAILABLE") private String stockState = "ALL";
    @Min(1) private int page=1;
    @Min(1) @Max(100) private int perPage=20;
}
