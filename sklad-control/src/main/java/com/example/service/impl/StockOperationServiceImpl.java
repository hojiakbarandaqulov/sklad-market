package com.example.service.impl;

import com.example.dto.reservation.ReservationCreateRequest;
import com.example.dto.stockOperation.RezervRequest;
import com.example.dto.stockOperation.RezervResponse;
import com.example.dto.stockOperation.AdjustmentRequest;
import com.example.dto.stockOperation.AdjustmentResponse;
import com.example.dto.stockOperation.WriteOffResponse;
import com.example.dto.warehouse.WarehouseDTO;
import com.example.entity.StockOperation;
import com.example.entity.StockReservation;
import com.example.entity.Warehouse;
import com.example.entity.WarehouseStock;
import com.example.enums.AppLanguage;
import com.example.enums.StockOperationType;
import com.example.enums.WarehouseStatus;
import com.example.enums.InventoryProductStatus;
import com.example.exp.AppConflictException;
import com.example.exp.AppBadException;
import com.example.exp.AppNotFoundException;
import com.example.repository.WarehouseRepository;
import com.example.repository.StockReservationRepository;
import com.example.repository.WarehouseStockRepository;
import com.example.repository.InventoryProductRepository;
import com.example.repository.StockOperationRepository;
import com.example.service.ResourceBundleService;
import com.example.service.StockReservationService;
import com.example.service.WarehouseService;
import com.example.utils.SpringSecurityUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.service.StockOperationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;

// Eski endpoint ham umumiy rezerv mantiqidan foydalanadi; miqdor ikki marta qo'shilmaydi.
@Service
@RequiredArgsConstructor
public class StockOperationServiceImpl implements StockOperationService {
    private final StockReservationService reservations;
    private final WarehouseRepository warehouses;
    private final ResourceBundleService messages;
    private final Clock clock;
    private final StockReservationRepository reservationRepository;
    private final WarehouseStockRepository stocks;
    private final InventoryProductRepository products;
    private final StockOperationRepository operations;
    private final WarehouseService warehouseService;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public AdjustmentResponse adjust(AdjustmentRequest request, AppLanguage language) {
        Long actor = SpringSecurityUtil.getProfileId();
        if (actor == null)
            throw new AccessDeniedException(messages.getMessage("auth.profile.missing", language));
        // Foydalanuvchi aynan shu ombor kompaniyasining egasi ekanini mavjud service tekshiradi.
        var ownedWarehouse = warehouseService.get(request.warehouseId(), language);
        var warehouse = warehouses.lockPresent(request.warehouseId())
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("warehouse.not.found", language)));
        entityManager.refresh(warehouse, LockModeType.PESSIMISTIC_WRITE);
        if (!warehouse.getCompanyId().equals(ownedWarehouse.getCompanyId()))
            throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        String key = request.idempotencyKey().strip();
        String reason = request.reason().strip();
        if (key.isEmpty() || reason.isEmpty())
            throw new AppBadException(messages.getMessage("adjustment.request.invalid", language));

        // Ombor qulfi bir xil request'ni kutayotgan boshqa transaction bilan ketma-ket bajaradi.
        // Takroriy so'rovda joriy qoldiq qayta o'rnatilmaydi, tarixdagi javob qaytariladi.
        var previous = operations.findByRequestKey(warehouse.getCompanyId(), key);
        if (previous.isPresent()) {
            StockOperation old = previous.get();
            if (old.getType() != StockOperationType.ADJUSTMENT
                    || !old.getStock().getWarehouse().getId().equals(request.warehouseId())
                    || !old.getStock().getInventoryProduct().getId().equals(request.inventoryProductId())
                    || old.getQuantityAfter().compareTo(request.actualQuantity()) != 0
                    || !reason.equals(old.getReason()))
                throw new AppConflictException(messages.getMessage("adjustment.key.conflict", language));
            return adjustmentDTO(old);
        }
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE)
            throw new AppConflictException(messages.getMessage("reservation.stock.inactive", language));

        // Qulflash tartibi: ombor -> mahsulot -> qoldiq. Mahsulotni arxivlash bilan ham muvofiqlashadi.
        var product = products.lockPresent(request.inventoryProductId())
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("control.product.missing", language)));
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        if (!product.getCompanyId().equals(warehouse.getCompanyId()))
            throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        if (product.getStatus() != InventoryProductStatus.ACTIVE || Boolean.TRUE.equals(product.getDeleted()))
            throw new AppConflictException(messages.getMessage("reservation.stock.inactive", language));
        Long stockId = stocks.positionId(warehouse.getId(), product.getId())
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.stock.missing", language)));
        WarehouseStock stock = stocks.lockPresent(stockId)
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.stock.missing", language)));
        entityManager.refresh(stock, LockModeType.PESSIMISTIC_WRITE);
        if (request.actualQuantity().compareTo(stock.getReserved()) < 0)
            throw new AppConflictException(messages.getMessage("adjustment.below.reserved", language));
        var before = stock.getOnHand();
        var change = request.actualQuantity().subtract(before);
        if (change.signum() == 0)
            throw new AppConflictException(messages.getMessage("adjustment.unchanged", language));

        // Rezerv o'zgarmaydi. Yangi qoldiq va tarix bitta transaction ichida yoziladi.
        stock.setOnHand(request.actualQuantity());
        stock.setModifiedBy(actor);
        stocks.saveAndFlush(stock);
        StockOperation operation = new StockOperation();
        operation.setStock(stock);
        operation.setCompanyId(warehouse.getCompanyId());
        operation.setType(StockOperationType.ADJUSTMENT);
        operation.setIdempotencyKey(key);
        operation.setQuantityBefore(before);
        operation.setQuantityChange(change);
        operation.setQuantityAfter(stock.getOnHand());
        operation.setReservedSnapshot(stock.getReserved());
        operation.setReason(reason);
        operation.setCreatedBy(actor);
        operation.setModifiedBy(actor);
        operation = operations.saveAndFlush(operation);
        // DB va Java vaqt/kasr aniqligi farq qilishi mumkin; birinchi javob ham DB'dagi qiymatdan olinadi.
        entityManager.refresh(operation);
        return adjustmentDTO(operation);
    }

    private AdjustmentResponse adjustmentDTO(StockOperation operation) {
        var stock = operation.getStock();
        return new AdjustmentResponse(operation.getId(), operation.getType(), stock.getWarehouse().getId(),
                stock.getInventoryProduct().getId(), operation.getQuantityBefore(), operation.getQuantityChange(),
                operation.getQuantityAfter(), operation.getReservedSnapshot(),
                operation.getQuantityAfter().subtract(operation.getReservedSnapshot()), operation.getReason(),
                operation.getCreatedBy(), operation.getCreatedDate());
    }

    @Override
    @Transactional
    public RezervResponse rezervAdd(RezervRequest request, AppLanguage language) {
        var warehouse = warehouses.findByIdAndDeletedFalse(request.warehouseId())
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("warehouse.not.found", language)));
        Instant expiresAt = reservationRepository.findByCompanyIdAndIdempotencyKey(warehouse.getCompanyId(), request.idempotencyKey().strip())
                .map(StockReservation::getExpiresAt).orElseGet(() -> clock.instant().plusSeconds(1800));
        var result = reservations.create(new ReservationCreateRequest(warehouse.getCompanyId(),
                request.idempotencyKey(), expiresAt, request.reason(),
                List.of(new ReservationCreateRequest.Item(request.warehouseId(), request.inventoryProductId(),
                        request.quantity()))), language);
        var item = result.items().getFirst();
        return new RezervResponse(result.id(), result.status(), item.warehouseId(), item.inventoryProductId(),
                item.quantity(), item.reserved(), item.available(), result.createdDate());
    }

    @Override
    @Transactional
    public WriteOffResponse writeOffs(RezervRequest rezervRequest, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null)
            throw new AccessDeniedException(messages.getMessage("auth.profile.missing", language));
        // Bu method ombor mavjudligi va foydalanuvchining unga egaligini ham tekshiradi.
        WarehouseDTO ownedWarehouse = warehouseService.get(rezervRequest.warehouseId(), language);
        Warehouse warehouse = warehouses.lockPresent(rezervRequest.warehouseId())
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("warehouse.not.found", language)));
        entityManager.refresh(warehouse, LockModeType.PESSIMISTIC_WRITE);
        if (!warehouse.getCompanyId().equals(ownedWarehouse.getCompanyId()))
            throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        String key = rezervRequest.idempotencyKey().strip();
        String reason = rezervRequest.reason().strip();
        if (key.isEmpty() || reason.isEmpty())
            throw new AppBadException(messages.getMessage("writeoff.request.invalid", language));
        // Ombor qulfi olingach tekshiramiz: parallel retry birinchi operatsiya tugashini kutadi.
        // Hisobdan chiqarish rezervlar jadvalidan emas, operatsiyalar jurnalidan qidiriladi.
        Optional<StockOperation> previous = operations.findByRequestKey(warehouse.getCompanyId(), key);
        if (previous.isPresent()) {
            StockOperation operation = previous.get();
            WarehouseStock stock = operation.getStock();
            // Bir kalit boshqa mahsulot, miqdor yoki amal uchun qayta ishlatilmaydi.
            if (operation.getType() != StockOperationType.WRITE_OFF
                    || !stock.getWarehouse().getId().equals(rezervRequest.warehouseId())
                    || !stock.getInventoryProduct().getId().equals(rezervRequest.inventoryProductId())
                    || operation.getQuantityChange().negate().compareTo(rezervRequest.quantity()) != 0
                    || !reason.equals(operation.getReason())) {
                throw new AppConflictException(messages.getMessage("writeoff.key.conflict", language));
            }
            // Joriy qoldiq keyin o'zgargan bo'lsa ham, aynan oldingi natija qaytariladi.
            return writeOffDTO(operation);
        }
        if (warehouse.getStatus() != WarehouseStatus.ACTIVE)
            throw new AppConflictException(messages.getMessage("reservation.stock.inactive", language));
        // Boshqa ombor amallari bilan bir xil qulf tartibi: ombor -> mahsulot -> qoldiq.
        var product = products.lockPresent(rezervRequest.inventoryProductId())
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("control.product.missing", language)));
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        if (!product.getCompanyId().equals(warehouse.getCompanyId()))
            throw new AccessDeniedException(messages.getMessage("auth.access.denied", language));
        if (product.getStatus() != InventoryProductStatus.ACTIVE || Boolean.TRUE.equals(product.getDeleted()))
            throw new AppConflictException(messages.getMessage("reservation.stock.inactive", language));
        // Shu ombordagi mahsulot qoldig'ini topamiz va transaction tugaguncha bloklaymiz.
        Long stockId = stocks.positionId(rezervRequest.warehouseId(), rezervRequest.inventoryProductId())
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.stock.missing", language)));
        WarehouseStock stock = stocks.lockPresent(stockId)
                .orElseThrow(() -> new AppNotFoundException(messages.getMessage("reservation.stock.missing", language)));
        // Persistence context'da oldin o'qilgan bo'lsa ham, blok ostidagi yangi qiymat olinadi.
        entityManager.refresh(stock, LockModeType.PESSIMISTIC_WRITE);
        if (rezervRequest.quantity().compareTo(stock.getAvailable()) > 0)
            throw new AppConflictException(messages.getMessage("writeoff.stock.insufficient", language));

        // Faqat umumiy qoldiq kamayadi; rezervlangan miqdorga tegmaymiz.
        var before = stock.getOnHand();
        stock.setOnHand(before.subtract(rezervRequest.quantity()));
        stock.setModifiedBy(profileId);
        stocks.saveAndFlush(stock);

        // Amal turi request'dan olinmaydi: bu endpoint doim WRITE_OFF yaratadi.
        StockOperation operation = new StockOperation();
        operation.setStock(stock);
        operation.setCompanyId(warehouse.getCompanyId());
        operation.setType(StockOperationType.WRITE_OFF);
        operation.setIdempotencyKey(key);
        operation.setQuantityBefore(before);
        operation.setQuantityChange(rezervRequest.quantity().negate());
        operation.setQuantityAfter(stock.getOnHand());
        operation.setReservedSnapshot(stock.getReserved());
        operation.setReason(reason);
        operation.setCreatedBy(profileId);
        operation.setModifiedBy(profileId);
        operation = operations.saveAndFlush(operation);
        // Birinchi javob ham retry'dagi kabi bazada saqlangan vaqt va miqdorlardan olinadi.
        entityManager.refresh(operation);
        return writeOffDTO(operation);
    }

    private WriteOffResponse writeOffDTO(StockOperation operation) {
        WarehouseStock stock = operation.getStock();
        return new WriteOffResponse(
                operation.getId(),
                operation.getType(),
                stock.getWarehouse().getId(),
                stock.getInventoryProduct().getId(),
                operation.getQuantityBefore(),
                operation.getQuantityChange(),
                operation.getQuantityAfter(),
                operation.getReservedSnapshot(),
                operation.getQuantityAfter().subtract(operation.getReservedSnapshot()),
                operation.getReason(),
                operation.getCreatedBy(),
                operation.getCreatedDate());
    }
}
