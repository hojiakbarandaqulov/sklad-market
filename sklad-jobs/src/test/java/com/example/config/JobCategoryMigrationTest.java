package com.example.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JobCategoryMigrationTest {
    @Test void migrationSupportsExistingDatabaseAndDoesNotChangeOldVacancies() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            connection.createStatement().execute("CREATE TABLE vacancy(id BIGINT PRIMARY KEY, position_name VARCHAR(255))");
            connection.createStatement().execute("INSERT INTO vacancy VALUES (42, 'Old vacancy')");
            var flyway = Flyway.configure().dataSource(url, "sa", "")
                    .locations("classpath:db/skladjobs-migration")
                    .table("flyway_schema_history_sklad_jobs")
                    .baselineOnMigrate(true).baselineVersion("0").load();
            assertEquals(3, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var result = connection.createStatement().executeQuery("SELECT category_id, position_name FROM vacancy WHERE id=42")) {
                assertTrue(result.next());
                assertNull(result.getObject(1));
                assertEquals("Old vacancy", result.getString(2));
            }
            assertThrows(java.sql.SQLException.class, () -> connection.createStatement()
                    .execute("UPDATE vacancy SET category_id=999 WHERE id=42"));
        }
    }
}
