package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableFeignClients(clients = {com.example.config.clent.CompanyClient.class, com.example.config.clent.ChatClient.class})
@EnableJpaAuditing
@SpringBootApplication
public class SkladJobsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SkladJobsApplication.class, args);
	}

}
