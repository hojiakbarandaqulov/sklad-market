package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableFeignClients(basePackages = "org.example.config")
@EnableJpaAuditing
@SpringBootApplication
public class SkladJobsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkladJobsApplication.class, args);
	}

}
