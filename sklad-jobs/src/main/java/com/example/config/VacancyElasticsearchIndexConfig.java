package com.example.config;

import com.example.document.VacancyDocument;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@Configuration(proxyBeanMethods = false)
public class VacancyElasticsearchIndexConfig {

    @Bean
    public ApplicationRunner initializeVacancyIndex(ElasticsearchOperations operations) {
        return args -> {
            var indexOps = operations.indexOps(VacancyDocument.class);
            if (!indexOps.exists()) {
                // Reads @Setting JSON and @Field mappings before the first document is saved.
                if (!indexOps.createWithMapping()) {
                    throw new IllegalStateException("Vacancy index creation was not acknowledged");
                }
            }
        };
    }
}
