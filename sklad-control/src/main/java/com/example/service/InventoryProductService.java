package com.example.service;
import com.example.dto.product.*;
import com.example.enums.AppLanguage;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageImpl;

public interface InventoryProductService {
    boolean hasCatalogLink(Long companyId, Long productId, AppLanguage language);
    InventoryProductDTO create(InventoryProductCreateRequest request, AppLanguage language);
    PageImpl<InventoryProductDTO> list(InventoryProductFilter filter, AppLanguage language);
    InventoryProductDTO get(Long id, AppLanguage language);
    InventoryProductDTO update(Long id, InventoryProductUpdateRequest request, AppLanguage language);
    InventoryProductDTO archive(Long id, AppLanguage language);
    InventoryProductDTO restore(Long id, AppLanguage language);

    InventoryProductSaleSettingsResponse saleSettings(Long id, InventoryProductSaleSettings inventoryProductSaleSettings, AppLanguage language);
}
