package com.example.config;

import com.example.document.VacancyDocument;
import com.example.entity.Vacancy;
import com.example.mapper.VacancyDocumentMapper;
import com.example.repository.VacancyRepository;
import com.example.repository.VacancySearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VacancySearchIndexInitializer implements ApplicationRunner {
    private final VacancyRepository repository;
    // Repository initialization creates the versioned index with @Setting and @Field mappings.
    private final VacancySearchRepository searchRepository;
    private final ElasticsearchOperations operations;

    @Override
    public void run(ApplicationArguments args) {
        int pageNumber = 0;
        long indexed = 0;
        Page<Vacancy> page;
        do {
            page = repository.findAll(PageRequest.of(pageNumber++, 200, Sort.by("id")));
            if (page.hasContent()) {
                var documents = page.getContent().stream().map(VacancyDocumentMapper::from).toList();
                searchRepository.saveAll(documents);
                indexed += documents.size();
            }
        } while (page.hasNext());
        operations.indexOps(VacancyDocument.class).refresh();
        log.info("Vacancy search index initialized from database: {} documents", indexed);
    }
}
