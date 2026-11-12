package com.example.config;

import com.example.document.RegionDocument;
import com.example.entity.Region;
import com.example.repository.RegionRepository;
import com.example.repository.RegionSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegionSearchIndexInitializer implements ApplicationRunner {
    private final RegionRepository repository;
    // Repository ishga tushganda @Setting va @Field asosida indeks yaratiladi.
    private final RegionSearchRepository searchRepository;
    private final ElasticsearchOperations operations;

    @Override
    public void run(ApplicationArguments args) {
        // Add new fields to an existing index before rebuilding documents from the database.
        operations.indexOps(RegionDocument.class).putMapping();
        int pageNumber = 0;
        long indexed = 0;
        Page<Region> page;
        do {
            page = repository.findAllByDeletedFalse(PageRequest.of(pageNumber++, 200, Sort.by("id")));
            if (page.hasContent()) {
                List<RegionDocument> documents = new ArrayList<>(page.getNumberOfElements());
                for (Region region : page.getContent()) {
                    RegionDocument document = new RegionDocument();
                    document.setId(region.getId().toString());
                    document.setCode(region.getCode());
                    document.setNameUz(region.getNameUz());
                    document.setNameRu(region.getNameRu());
                    document.setNameEn(region.getNameEn());
                    document.setType(region.getType());
                    document.setSortOrder(region.getSortOrder());
                    documents.add(document);
                }
                searchRepository.saveAll(documents);
                indexed += documents.size();
            }
        } while (page.hasNext());
        operations.indexOps(RegionDocument.class).refresh();
        log.info("Region search index initialized from database: {} documents", indexed);
    }
}
