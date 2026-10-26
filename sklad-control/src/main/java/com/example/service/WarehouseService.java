package com.example.service;

import com.example.dto.warehouse.*;
import com.example.enums.AppLanguage;
import org.springframework.data.domain.PageImpl;

public interface WarehouseService {
    WarehouseDTO create(WarehouseCreateRequest request, AppLanguage language);

    PageImpl<WarehouseDTO> list(WarehouseFilter filter, AppLanguage language);

    WarehouseDTO get(Long id, AppLanguage language);

    WarehouseDTO update(Long id, WarehouseUpdateRequest request, AppLanguage language);

    WarehouseDTO archive(Long id, AppLanguage language);

    WarehouseDTO restore(Long id, AppLanguage language);
}
