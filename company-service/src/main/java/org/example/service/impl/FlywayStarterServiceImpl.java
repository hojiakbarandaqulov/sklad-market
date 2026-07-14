package org.example.service.impl;

import org.example.service.FlywayStarterService;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class FlywayStarterServiceImpl implements FlywayStarterService {
    private final DataSource dataSource;

    public FlywayStarterServiceImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        Flyway.configure().baselineOnMigrate(true)
                .dataSource(dataSource).load().migrate();
    }
}
