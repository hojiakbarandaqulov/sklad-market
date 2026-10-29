package com.example;

import com.example.entity.*;
import com.example.enums.ReservationStatus;
import com.example.enums.StockOperationType;
import com.example.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

// Liquibase yozilmaydi: entity bog'lanishlari faqat alohida vaqtinchalik H2 bazasida tekshiriladi.
@DataJpaTest(properties = {
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.datasource.url=jdbc:h2:mem:control-entity-mappings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = InventoryEntityMappingTest.JpaTestConfig.class)
class InventoryEntityMappingTest {
    // Parallel testning commit qilgan qatori boshqa test kartalari bilan to'qnashmasin.
    private static final java.util.concurrent.atomic.AtomicLong CARD_IDS = new java.util.concurrent.atomic.AtomicLong(100);
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void sameProductHasSeparateDecimalBalancesInDifferentWarehouses() {
        InventoryProduct product = product(entityManager, "SKU-DECIMAL");
        WarehouseStock first = stock(entityManager, product, "Toshkent", "12.750");
        first.setReserved(new BigDecimal("2.125"));
        WarehouseStock second = stock(entityManager, product, "Samarqand", "5.500");
        second.setReserved(new BigDecimal("1.250"));
        entityManager.flush();
        entityManager.clear();

        WarehouseStock storedFirst = entityManager.find(WarehouseStock.class, first.getId());
        WarehouseStock storedSecond = entityManager.find(WarehouseStock.class, second.getId());
        assertThat(storedFirst.getAvailable()).isEqualByComparingTo("10.625");
        assertThat(storedSecond.getAvailable()).isEqualByComparingTo("4.250");
        assertThat(storedFirst.getInventoryProduct().getId()).isEqualTo(storedSecond.getInventoryProduct().getId());
        assertThat(storedFirst.getWarehouse().getId()).isNotEqualTo(storedSecond.getWarehouse().getId());
    }

    @Test
    void oneProductCannotHaveTwoBalanceRowsInTheSameWarehouse() {
        InventoryProduct product = product(entityManager, "SKU-UNIQUE-STOCK");
        WarehouseStock existing = stock(entityManager, product, "Main", "10.000");
        entityManager.flush();
        WarehouseStock duplicate = new WarehouseStock();
        duplicate.setWarehouse(existing.getWarehouse());
        duplicate.setInventoryProduct(product);
        assertThatThrownBy(() -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void databaseRejectsReservationLargerThanPhysicalBalance() {
        WarehouseStock stock = stock(entityManager, product(entityManager, "SKU-OVER-RESERVED"), "Main", "10.000");
        entityManager.flush();
        stock.setReserved(new BigDecimal("10.001"));
        assertThatThrownBy(entityManager::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void sellerSkuCanBeReusedByAnotherCompany() {
        InventoryProduct first = product(entityManager, "SHARED-SKU");
        InventoryProduct second = new InventoryProduct();
        second.setCompanyId(2L);
        second.setCatalogCardId(first.getCatalogCardId());
        second.setSku(first.getSku());
        entityManager.persist(second);
        entityManager.flush();
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void oneReservationCanAllocateTheSameProductFromTwoWarehouses() {
        InventoryProduct product = product(entityManager, "SKU-RESERVATION");
        WarehouseStock first = stock(entityManager, product, "First", "10.000");
        WarehouseStock second = stock(entityManager, product, "Second", "20.000");
        StockReservation reservation = reservation(100L, "request-100");
        entityManager.persist(reservation);
        StockReservationItem firstItem = reservationItem(reservation, first, "2.500");
        StockReservationItem secondItem = reservationItem(reservation, second, "3.750");
        entityManager.persist(firstItem);
        entityManager.persist(secondItem);
        entityManager.flush();
        entityManager.clear();

        StockReservationItem stored = entityManager.find(StockReservationItem.class, secondItem.getId());
        assertThat(stored.getReservation().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(stored.getReservation().getExpiresAt()).isNotNull();
        assertThat(stored.getQuantity()).isEqualByComparingTo("3.750");
        assertThat(stored.getStock().getId()).isEqualTo(second.getId());
    }

    @Test
    void duplicateOrderReservationIsRejectedEvenWithAnotherRequestKey() {
        entityManager.persist(reservation(200L, "request-200"));
        entityManager.flush();
        assertThatThrownBy(() -> {
            entityManager.persist(reservation(200L, "another-request-200"));
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    void operationStoresSignedChangeAndBeforeAfterSnapshots() {
        WarehouseStock stock = stock(entityManager, product(entityManager, "SKU-OPERATION"), "Main", "8.500");
        StockOperation operation = operation(stock, "10.000", "-1.500", "8.500");
        entityManager.persist(operation);
        entityManager.flush();
        entityManager.clear();
        StockOperation stored = entityManager.find(StockOperation.class, operation.getId());
        assertThat(stored.getQuantityChange()).isEqualByComparingTo("-1.500");
        assertThat(stored.getQuantityAfter()).isEqualByComparingTo("8.500");
        assertThat(stored.getCreatedDate()).isNotNull();
    }

    @Test
    void databaseRejectsOperationWithInconsistentSnapshots() {
        WarehouseStock stock = stock(entityManager, product(entityManager, "SKU-BAD-OPERATION"), "Main", "10.000");
        assertThatThrownBy(() -> {
            entityManager.persist(operation(stock, "10.000", "-1.000", "20.000"));
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleReservationUpdateCannotOverwriteAnotherStockUpdate() {
        Long stockId;
        try (EntityManager setup = entityManagerFactory.createEntityManager()) {
            setup.getTransaction().begin();
            WarehouseStock stock = stock(setup, product(setup, "SKU-CONCURRENT"), "Concurrency", "10.000");
            setup.getTransaction().commit();
            stockId = stock.getId();
        }
        try (EntityManager first = entityManagerFactory.createEntityManager();
             EntityManager second = entityManagerFactory.createEntityManager()) {
            first.getTransaction().begin();
            second.getTransaction().begin();
            WarehouseStock firstRead = first.find(WarehouseStock.class, stockId);
            WarehouseStock secondRead = second.find(WarehouseStock.class, stockId);
            firstRead.setReserved(new BigDecimal("4.000"));
            first.getTransaction().commit();
            secondRead.setReserved(new BigDecimal("7.000"));
            assertThatThrownBy(() -> second.getTransaction().commit())
                    .isInstanceOf(RollbackException.class).hasCauseInstanceOf(OptimisticLockException.class);
        }
        try (EntityManager check = entityManagerFactory.createEntityManager()) {
            WarehouseStock actual = check.find(WarehouseStock.class, stockId);
            assertThat(actual.getReserved()).isEqualByComparingTo("4.000");
            assertThat(actual.getAvailable()).isEqualByComparingTo("6.000");
        }
    }

    private InventoryProduct product(EntityManager manager, String sku) {
        InventoryProduct product = new InventoryProduct();
        product.setCompanyId(1L);
        product.setCatalogCardId(CARD_IDS.incrementAndGet());
        product.setSku(sku);
        manager.persist(product);
        return product;
    }

    private WarehouseStock stock(EntityManager manager, InventoryProduct product, String name, String quantity) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCompanyId(product.getCompanyId());
        warehouse.setName(name);
        manager.persist(warehouse);
        WarehouseStock stock = new WarehouseStock();
        stock.setWarehouse(warehouse);
        stock.setInventoryProduct(product);
        stock.setOnHand(new BigDecimal(quantity));
        manager.persist(stock);
        return stock;
    }

    private StockReservation reservation(Long orderId, String key) {
        StockReservation reservation = new StockReservation();
        reservation.setCompanyId(1L);
        reservation.setOrderId(orderId);
        reservation.setIdempotencyKey(key);
        reservation.setExpiresAt(Instant.now().plusSeconds(900));
        return reservation;
    }

    private StockReservationItem reservationItem(StockReservation reservation, WarehouseStock stock, String quantity) {
        StockReservationItem item = new StockReservationItem();
        item.setReservation(reservation);
        item.setStock(stock);
        item.setQuantity(new BigDecimal(quantity));
        return item;
    }

    private StockOperation operation(WarehouseStock stock, String before, String change, String after) {
        StockOperation operation = new StockOperation();
        operation.setStock(stock);
        operation.setType(StockOperationType.WRITE_OFF);
        operation.setQuantityBefore(new BigDecimal(before));
        operation.setQuantityChange(new BigDecimal(change));
        operation.setQuantityAfter(new BigDecimal(after));
        operation.setReason("Sinovdagi hisobdan chiqarish");
        return operation;
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = Warehouse.class)
    @EnableJpaRepositories(basePackageClasses = WarehouseRepository.class)
    @EnableJpaAuditing
    static class JpaTestConfig {
    }
}
