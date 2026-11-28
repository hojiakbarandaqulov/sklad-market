package com.example.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResumeCategoryMigrationTest {
    private Flyway flyway(String url, String target) {
        return Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/skladjobs-migration")
                .table("flyway_schema_history_sklad_jobs")
                .baselineOnMigrate(true).baselineVersion("0").target(target).load();
    }

    @Test void upgradePreservesOldResumesAndTheirImages() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var sql = connection.createStatement()) {
            flyway(url, "1").migrate();
            sql.execute("CREATE TABLE resume (id BIGINT PRIMARY KEY, title VARCHAR(255), file_id VARCHAR(255))");
            sql.execute("INSERT INTO resume VALUES (42, 'Old resume', 'photo.png')");
            var upgrade = flyway(url, "2");
            assertEquals(1, upgrade.migrate().migrationsExecuted);
            assertEquals(0, upgrade.migrate().migrationsExecuted);
            try (var row = sql.executeQuery("SELECT title, file_id, category_id FROM resume WHERE id=42")) {
                assertTrue(row.next());
                assertEquals("Old resume", row.getString("title"));
                assertEquals("photo.png", row.getString("file_id"));
                assertNull(row.getObject("category_id"));
            }
            sql.execute("INSERT INTO job_category (id, name_uz, name_ru, name_en) VALUES (7, 'IT', 'IT', 'IT')");
            sql.execute("UPDATE resume SET category_id=7 WHERE id=42");
            try (var row = sql.executeQuery("SELECT id FROM resume WHERE category_id=7")) {
                assertTrue(row.next());
                assertEquals(42L, row.getLong(1));
            }
            assertThrows(SQLException.class, () -> sql.execute("UPDATE resume SET category_id=999 WHERE id=42"));
        }
    }

    @Test void emptyDatabaseCanCreateAndReadCategorizedResumes() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        assertEquals(2, flyway(url, "2").migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var sql = connection.createStatement()) {
            sql.execute("INSERT INTO job_category (id, name_uz, name_ru, name_en) VALUES (7, 'IT', 'IT', 'IT')");
            sql.execute("INSERT INTO resume (candidate_id, title, full_name, phone, email, region_id, category_id) "
                    + "VALUES (16, 'Developer', 'Ali Valiyev', '+998901234567', 'ali@example.com', 1, 7)");
            try (var row = sql.executeQuery("SELECT candidate_id, category_id, deleted FROM resume")) {
                assertTrue(row.next());
                assertEquals(16L, row.getLong("candidate_id"));
                assertEquals(7L, row.getLong("category_id"));
                assertFalse(row.getBoolean("deleted"));
            }
        }
    }
}
