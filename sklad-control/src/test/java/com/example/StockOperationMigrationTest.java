package com.example;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

class StockOperationMigrationTest {
    private static final String UPGRADE = "004-stock-operation-adjustment-fields.xml";

    @Test
    void upgradePreservesOldLedgerAndAddsIdempotentAdjustmentFields() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:operation-upgrade;MODE=PostgreSQL", "sa", "");
             var resources = new ClassLoaderResourceAccessor()) {
            preparePreviousSchema(connection, resources);
            apply(connection, resources, UPGRADE);
            try (var statement = connection.createStatement();
                 var row = statement.executeQuery("SELECT * FROM stock_operations WHERE id=1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getBigDecimal("quantity_after")).isEqualByComparingTo("10.500");
                assertThat(row.getString("reason")).isEqualTo("Initial stock");
                assertThat(row.getObject("company_id")).isNull();
                assertThat(row.getObject("idempotency_key")).isNull();
                assertThat(row.getObject("reserved_snapshot")).isNull();
            }

            String adjustment = "INSERT INTO stock_operations(stock_id,type,quantity_before,quantity_change,quantity_after,"
                    + "company_id,idempotency_key,reserved_snapshot) VALUES(1,'ADJUSTMENT',10.500,2,12.500,1,'adjust-1',1.125)";
            execute(connection, adjustment);
            assertThatThrownBy(() -> execute(connection, adjustment)).isInstanceOf(SQLException.class);
            // Eski, idempotency key'siz jurnal yozuvlari ham qo'shilishi mumkin.
            execute(connection, "INSERT INTO stock_operations(stock_id,type,quantity_before,quantity_change,quantity_after) "
                    + "VALUES(1,'RECEIPT',12.500,1,13.500)");
            apply(connection, resources, UPGRADE);
            try (var statement = connection.createStatement();
                 var row = statement.executeQuery("SELECT COUNT(*) FROM stock_operations")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt(1)).isEqualTo(3);
            }
        }
    }

    @Test
    void manuallyAddedFieldsAndConstraintAreKeptWhenRegisteringTheUpgrade() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:operation-upgrade-existing;MODE=PostgreSQL", "sa", "");
             var resources = new ClassLoaderResourceAccessor()) {
            preparePreviousSchema(connection, resources);
            execute(connection, "ALTER TABLE stock_operations ADD COLUMN company_id BIGINT");
            execute(connection, "ALTER TABLE stock_operations ADD COLUMN idempotency_key VARCHAR(128)");
            execute(connection, "ALTER TABLE stock_operations ADD COLUMN reserved_snapshot DECIMAL(19,3)");
            execute(connection, "ALTER TABLE stock_operations ADD CONSTRAINT uk_stock_operations_company_key UNIQUE(company_id,idempotency_key)");
            execute(connection, "UPDATE stock_operations SET company_id=1,idempotency_key='existing',reserved_snapshot=2.125 WHERE id=1");
            apply(connection, resources, UPGRADE);
            try (var statement = connection.createStatement();
                 var row = statement.executeQuery("SELECT company_id,idempotency_key,reserved_snapshot FROM stock_operations WHERE id=1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong(1)).isEqualTo(1L);
                assertThat(row.getString(2)).isEqualTo("existing");
                assertThat(row.getBigDecimal(3)).isEqualByComparingTo("2.125");
            }
        }
    }

    private void preparePreviousSchema(Connection connection, ClassLoaderResourceAccessor resources) throws Exception {
        apply(connection, resources, "001-create-warehouses.xml");
        apply(connection, resources, "002-create-inventory-and-reservations.xml");
        apply(connection, resources, "003-reservation-workflow.xml");
        execute(connection, "INSERT INTO warehouses(id,company_id,name) VALUES(1,1,'Main')");
        execute(connection, "INSERT INTO inventory_products(id,company_id,catalog_card_id,sku) VALUES(1,1,100,'SKU-1')");
        execute(connection, "INSERT INTO warehouse_stocks(id,warehouse_id,inventory_product_id,on_hand) VALUES(1,1,1,10.500)");
        execute(connection, "INSERT INTO stock_operations(id,stock_id,type,quantity_before,quantity_change,quantity_after,reason) "
                + "VALUES(1,1,'OPENING_BALANCE',0,10.500,10.500,'Initial stock')");
        // IDENTITY keyingi jurnal yozuvini mavjud 1-ID bilan to'qnashtirmasin.
        execute(connection, "ALTER TABLE stock_operations ALTER COLUMN id RESTART WITH 2");
    }

    private void apply(Connection connection, ClassLoaderResourceAccessor resources, String filename) throws Exception {
        new Liquibase("db/changelog/changes/" + filename, resources, new JdbcConnection(connection))
                .update(new Contexts(), new LabelExpression());
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
