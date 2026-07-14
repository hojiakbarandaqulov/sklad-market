package org.example.service;

import org.springframework.boot.CommandLineRunner;

public interface FlywayStarterService extends CommandLineRunner {
    void run(String... args) throws Exception;
}
