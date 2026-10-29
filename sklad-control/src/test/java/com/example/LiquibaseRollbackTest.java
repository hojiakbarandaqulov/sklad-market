package com.example;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.*;

class LiquibaseRollbackTest {
    @Test
    void reservationUpgradeKeepsExistingRowsAndAllowsOrdersToBeOmitted() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:control-reservation-upgrade;MODE=PostgreSQL", "sa", "");
             var resources = new ClassLoaderResourceAccessor()) {
            // Aynan rezerv upgrade'i tekshiriladi; keyingi modullarning migration soniga bog'lanmaydi.
            var database = new JdbcConnection(connection);
            new Liquibase("db/changelog/changes/001-create-warehouses.xml", resources, database)
                    .update(new Contexts(), new LabelExpression());
            new Liquibase("db/changelog/changes/002-create-inventory-and-reservations.xml", resources, database)
                    .update(new Contexts(), new LabelExpression());
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE stock_reservations ALTER COLUMN order_id SET NOT NULL");
                statement.execute("INSERT INTO stock_reservations(company_id,order_id,idempotency_key,expires_at) VALUES(1,55,'old-reservation',CURRENT_TIMESTAMP)");
            }
            new Liquibase("db/changelog/changes/003-reservation-workflow.xml", resources, database)
                    .update(new Contexts(), new LabelExpression());
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO stock_reservations(company_id,order_id,idempotency_key,expires_at) VALUES(1,NULL,'standalone-reservation',CURRENT_TIMESTAMP)");
                try (var rows = statement.executeQuery("SELECT order_id FROM stock_reservations WHERE idempotency_key='old-reservation'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(55);
                }
            }
        }
    }

    @Test
    void warehouseMigrationCanBeRolledBackAndAppliedAgainInAnIsolatedTestDatabase() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:control-rollback;MODE=PostgreSQL", "sa", "");
             var liquibase = new Liquibase("db/changelog/db.changelog-master.xml",
                     new ClassLoaderResourceAccessor(), new JdbcConnection(connection))) {
            liquibase.update(new Contexts(), new LabelExpression());
            assertThat(connection.getMetaData().getTables(null, null, "WAREHOUSES", null).next()).isTrue();
            int applied = liquibase.getDatabase().getRanChangeSetList().size();
            liquibase.rollback(applied, new Contexts(), new LabelExpression());
            assertThat(connection.getMetaData().getTables(null, null, "WAREHOUSES", null).next()).isFalse();
            liquibase.update(new Contexts(), new LabelExpression());
            assertThat(connection.getMetaData().getTables(null, null, "WAREHOUSES", null).next()).isTrue();
        }
    }
}
