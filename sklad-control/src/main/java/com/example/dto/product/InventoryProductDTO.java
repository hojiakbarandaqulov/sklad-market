package com.example.dto.product;
import com.example.enums.InventoryProductStatus;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;

/** Karta ma'lumotlari Market'dan, qoldiqlar Control bazasidan yig'iladi. */
@Getter @Builder
public class InventoryProductDTO {
    private Long id;
    private Long companyId;
    private Long productId;
    private String sku;
    private InventoryProductStatus status;
    private JsonNode product;
    private List<StockDTO> stocks;

    @Getter @Builder
    public static class StockDTO {
        private Long warehouseId;
        private String warehouseName;
        private BigDecimal onHand;
        private BigDecimal reserved;
        private BigDecimal available;
        private BigDecimal minimumStock;
    }
}
