package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@EnableScheduling
public class ReservationConfig {
    @Bean
    public Clock reservationClock() { return Clock.systemUTC(); }
}
