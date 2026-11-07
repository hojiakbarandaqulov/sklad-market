package com.example;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Run against an isolated PostgreSQL instance using MIGRATION_TEST_JDBC_URL. */
class ReservationForeignKeyMigrationTest {
    Connection connection;
    String schema;

    @BeforeEach void setup() throws Exception {
        String url = System.getenv("MIGRATION_TEST_JDBC_URL");
        assumeTrue(url != null && url.startsWith("jdbc:postgresql:"));
        connection = DriverManager.getConnection(url, "postgres", "");
        schema = "reservation_fk_test_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE SCHEMA " + schema);
        sql("SET search_path TO " + schema);
        sql("CREATE TABLE stock_reservations(id bigint PRIMARY KEY)");
        sql("CREATE TABLE warehouse_stocks(id bigint PRIMARY KEY, on_hand numeric, reserved numeric)");
        sql("CREATE TABLE stock_reservation_item(id bigint PRIMARY KEY, reservation_id bigint, stock_id bigint, quantity numeric)");
        sql("ALTER TABLE stock_reservation_item ADD CONSTRAINT fk_item_reservation FOREIGN KEY(reservation_id) REFERENCES stock_reservations(id)");
        sql("ALTER TABLE stock_reservation_item ADD CONSTRAINT fk_item_stock FOREIGN KEY(stock_id) REFERENCES warehouse_stocks(id)");
        sql("INSERT INTO stock_reservations VALUES(1)");
        sql("INSERT INTO warehouse_stocks VALUES(1,5000,50)");
        sql("INSERT INTO stock_reservation_item VALUES(1,1,1,50)");
    }

    @AfterEach void cleanup() throws Exception {
        if (connection != null) {
            try { sql("DROP SCHEMA " + schema + " CASCADE"); }
            finally { connection.close(); }
        }
    }

    @Test void removesBothLegacyConstraintsAndPreservesRowsAndValidConstraints() throws Exception {
        legacy();
        apply();
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conrelid='stock_reservation_item'::regclass AND contype='f'"))
                .isEqualTo(2);
        assertThat(count("SELECT count(*) FROM stock_reservation_item WHERE id=1 AND quantity=50")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM warehouse_stocks WHERE on_hand=5000 AND reserved=50")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM stock_reservation")).isEqualTo(1);
        // Reproduce production: a reservation only in the plural table now accepts an item.
        sql("INSERT INTO stock_reservations VALUES(2)");
        sql("INSERT INTO stock_reservation_item VALUES(2,2,1,50)");
        assertThatThrownBy(() -> sql("INSERT INTO stock_reservation_item VALUES(3,999,1,50)"))
                .isInstanceOf(java.sql.SQLException.class);
        apply();
        assertThat(count("SELECT count(*) FROM databasechangelog")).isEqualTo(1);
    }

    @Test void alreadyRepairedDatabaseIsUnchanged() throws Exception {
        apply();
        assertThat(count("SELECT count(*) FROM stock_reservation_item")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conrelid='stock_reservation_item'::regclass AND contype='f'"))
                .isEqualTo(2);
    }

    @Test void partiallyRepairedDatabaseIsSupported() throws Exception {
        legacy();
        sql("ALTER TABLE stock_reservation_item DROP CONSTRAINT arbitrary_legacy_reservation");
        apply();
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conrelid='stock_reservation_item'::regclass AND contype='f'"))
                .isEqualTo(2);
    }

    @Test void missingReplacementPreventsAnyLegacyRemoval() throws Exception {
        legacy();
        sql("ALTER TABLE stock_reservation_item DROP CONSTRAINT fk_item_stock");
        assertThatThrownBy(this::apply).hasStackTraceContaining("validated FK");
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conrelid='stock_reservation_item'::regclass AND conname LIKE 'arbitrary_legacy_%'"))
                .isEqualTo(2);
    }

    @Test void unvalidatedReplacementPreventsLegacyRemoval() throws Exception {
        legacy();
        sql("ALTER TABLE stock_reservation_item DROP CONSTRAINT fk_item_stock");
        sql("ALTER TABLE stock_reservation_item ADD CONSTRAINT fk_item_stock FOREIGN KEY(stock_id) REFERENCES warehouse_stocks(id) NOT VALID");
        assertThatThrownBy(this::apply).hasStackTraceContaining("validated FK");
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conrelid='stock_reservation_item'::regclass AND conname LIKE 'arbitrary_legacy_%'"))
                .isEqualTo(2);
    }

    private void legacy() throws Exception {
        sql("CREATE TABLE stock_reservation(id bigint PRIMARY KEY)");
        sql("CREATE TABLE warehouse_stock(id bigint PRIMARY KEY)");
        sql("INSERT INTO stock_reservation VALUES(1)");
        sql("INSERT INTO warehouse_stock VALUES(1)");
        sql("ALTER TABLE stock_reservation_item ADD CONSTRAINT arbitrary_legacy_reservation FOREIGN KEY(reservation_id) REFERENCES stock_reservation(id)");
        sql("ALTER TABLE stock_reservation_item ADD CONSTRAINT arbitrary_legacy_stock FOREIGN KEY(stock_id) REFERENCES warehouse_stock(id)");
    }

    private void apply() throws Exception {
        var resources = new ClassLoaderResourceAccessor();
        var liquibase = new Liquibase("db/changelog/changes/006-remove-legacy-reservation-item-fks.xml",
                resources, new JdbcConnection(connection));
        liquibase.getDatabase().setDefaultSchemaName(schema);
        try { liquibase.update(new Contexts(), new LabelExpression()); }
        finally {
            if (!connection.getAutoCommit()) connection.rollback();
            connection.setAutoCommit(true);
            resources.close();
        }
    }

    private void sql(String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private long count(String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getLong(1);
        }
    }
}
