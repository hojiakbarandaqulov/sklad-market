package com.example.service.impl;

import com.example.config.client.CompanyClient;
import com.example.dto.internal.CompanyOwnershipResponse;
import com.example.dto.statistics.ControlStatisticsDTO;
import com.example.entity.StockOperation;
import com.example.enums.AppLanguage;
import com.example.exp.AppBadException;
import com.example.exp.AppNotFoundException;
import com.example.exp.AppServiceUnavailableException;
import com.example.repository.InventoryProductRepository;
import com.example.repository.StockOperationRepository;
import com.example.repository.WarehouseStockRepository;
import com.example.service.ControlStatisticsService;
import com.example.service.ResourceBundleService;
import com.example.utils.SpringSecurityUtil;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ControlStatisticsServiceImpl implements ControlStatisticsService {
    private final InventoryProductRepository products;
    private final WarehouseStockRepository stocks;
    private final StockOperationRepository operations;
    private final CompanyClient companyClient;
    private final ResourceBundleService messages;

    @Override
    public ControlStatisticsDTO getStatistics(Long companyId, AppLanguage language) {
        // Requestdagi kompaniya aynan token egasiga tegishli ekanini tekshiramiz.
        validateCompanyAccess(companyId, language);
        var balances = stocks.getCompanyStockBalances(companyId);
        long lowStockCount = 0;
        long outOfStockCount = 0;
        long reservedProductsCount = 0;
        // Query har bir mahsulot uchun bitta yig'indi qaytaradi; omborlar soni hisobni oshirmaydi.
        for (var balance : balances) {
            if (balance.getAvailable().signum() == 0) outOfStockCount++;
            else if (balance.getAvailable().compareTo(balance.getMinimumStock()) <= 0) lowStockCount++;
            if (balance.getReserved().signum() > 0) reservedProductsCount++;
        }
        var recentOperations = operations.findRecentByCompanyId(companyId, PageRequest.of(0, 10))
                .stream().map(this::toOperationDTO).toList();
        return new ControlStatisticsDTO(companyId, products.findByCountDeletedFalse(companyId),
                lowStockCount, outOfStockCount, reservedProductsCount,
                recentOperations);
    }

    private void validateCompanyAccess(Long companyId, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null)
            throw new AccessDeniedException(messages.getMessage("auth.profile.missing", language));
        if (companyId == null || companyId <= 0)
            throw new AppBadException(messages.getMessage("warehouse.company.invalid", language));
        CompanyOwnershipResponse company;
        try {
            company = companyClient.ownershipCheck(companyId, profileId);
        } catch (FeignException exception) {
            if (exception.status() == 404)
                throw new AppNotFoundException(messages.getMessage("warehouse.company.not.found", language));
            if (exception.status() == 401 || exception.status() == 403)
                throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
            throw unavailable(language);
        }
        if (company == null) throw unavailable(language);
        if (!company.isExists())
            throw new AppNotFoundException(messages.getMessage("warehouse.company.not.found", language));
        if (!Objects.equals(company.getCompanyId(), companyId)) throw unavailable(language);
        if (!company.isOwner())
            throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        if (!company.isActive())
            throw new AccessDeniedException(messages.getMessage("warehouse.company.inactive", language));
    }

    private AppServiceUnavailableException unavailable(AppLanguage language) {
        return new AppServiceUnavailableException(messages.getMessage("warehouse.company.service.unavailable", language));
    }

    private ControlStatisticsDTO.RecentOperationDTO toOperationDTO(StockOperation operation) {
        var stock = operation.getStock();
        var product = stock.getInventoryProduct();
        return new ControlStatisticsDTO.RecentOperationDTO(operation.getId(), stock.getWarehouse().getId(),
                product.getId(), product.getSku(), operation.getType(), operation.getQuantityBefore(),
                operation.getQuantityChange(), operation.getQuantityAfter(), operation.getReason(),
                operation.getCreatedBy(), operation.getCreatedDate());
    }
}
