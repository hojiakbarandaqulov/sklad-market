package com.example.config.clent;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class ChatFeignConfiguration {
    @Bean
    public Request.Options chatRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }
}