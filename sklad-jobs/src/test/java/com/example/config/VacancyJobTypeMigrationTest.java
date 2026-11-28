package com.example.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VacancyJobTypeMigrationTest {
    private Flyway flyway(String url, String target) {
        return Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/skladjobs-migration")
                .table("flyway_schema_history_sklad_jobs")
                .baselineOnMigrate(true).baselineVersion("0").target(target).load();
    }

    @Test void upgradePreservesLegacyVacanciesAndSupportsLaterClassification() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        flyway(url, "2").migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.execute("INSERT INTO vacancy (id, position_name, employment_type, price) VALUES (42, 'Old vacancy', 'Full time', 123)");
            assertEquals(1, flyway(url, "3").migrate().migrationsExecuted);
            assertEquals(0, flyway(url, "3").migrate().migrationsExecuted);
            try (var row = sql.executeQuery("SELECT position_name, employment_type, price, job_type FROM vacancy WHERE id=42")) {
                assertTrue(row.next());
                assertEquals("Old vacancy", row.getString("position_name"));
                assertEquals("Full time", row.getString("employment_type"));
                assertEquals(123, row.getInt("price"));
                assertNull(row.getString("job_type"));
            }
            for (String type : new String[]{"PERMANENT", "TEMPORARY"}) {
                sql.execute("UPDATE vacancy SET job_type='" + type + "' WHERE id=42");
                try (var row = sql.executeQuery("SELECT job_type FROM vacancy WHERE id=42")) {
                    assertTrue(row.next());
                    assertEquals(type, row.getString(1));
                }
            }
            assertThrows(SQLException.class, () -> sql.execute("UPDATE vacancy SET job_type='INVALID' WHERE id=42"));
        }
    }

    @Test void freshDatabaseRunsAllMigrations() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        assertEquals(3, flyway(url, "3").migrate().migrationsExecuted);
    }
}
