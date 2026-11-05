package org.example.customer;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class CompanyMigrationHistoryTest {
    @Test
    void preservesAppliedReviewMigrationAndAvoidsVersionCollision() throws Exception {
        var loader = getClass().getClassLoader();
        var original = loader.getResourceAsStream("db/migration/V3__create_company_reviews.sql");
        assertNotNull(original);
        CRC32 checksum = new CRC32();
        try (var reader = new BufferedReader(new InputStreamReader(original, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                checksum.update(line.getBytes(StandardCharsets.UTF_8));
            }
        }
        assertEquals(-539930524, (int) checksum.getValue(), "The V3 applied to production must remain immutable");
        assertNull(loader.getResource("db/migration/V3__add_company_region.sql"));
        assertNotNull(loader.getResource("db/migration/V4__add_customers.sql"));
        assertNotNull(loader.getResource("db/migration/V5__add_company_region.sql"));
    }
}
