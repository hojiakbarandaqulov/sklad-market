package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.document.ProductDocument;
import org.example.dto.product.ProductSearchResponse;
import org.example.enums.AppLanguage;
import org.example.repository.ProductSearchRepository;
import org.example.service.ProductSearchService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductSearchServiceImpl implements ProductSearchService {
    private final ProductSearchRepository repository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void index(ProductDocument document) {
        repository.save(document);
    }

    @Override
    public void delete(Long productId) {
        repository.deleteById(productId.toString());
    }

    @Override
    public void update(ProductDocument document) {
        Optional<ProductDocument> byId = repository.findById(document.getId());
        if (byId.isPresent()) {
            repository.save(document);
        }
    }

    @Override
    public List<ProductSearchResponse> search(String query, int page, int perPage) {
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .should(s -> s
                                        .matchPhrasePrefix(m -> m
                                                .field("name")
                                                .query(query.trim())
                                                .boost(5.0f)
                                        )

                                )
                                .should(s -> s
                                        .match(m -> m
                                                .field("shortDescription")
                                                .query(query)
                                                .fuzziness("AUTO")
                                                .boost(1.0f)
                                        )
                                ).filter(f -> f
                                        .term(t -> t
                                                .field("moderationStatus")
                                                .value("APPROVED")
                                        )
                                ).filter(f -> f
                                        .term(t -> t
                                                .field("isActive")
                                                .value(true)
                                        )
                                ).minimumShouldMatch("1")
                        )
                )
                .withPageable(PageRequest.of(page - 1, perPage)).build();
        SearchHits<ProductDocument> hits = elasticsearchOperations.search(searchQuery, ProductDocument.class);


        return hits.stream()
                .map(SearchHit::getContent)
                .map(this::toSearchResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageImpl<ProductSearchResponse> productSearch(String q, String categoryId, int page, int perPage, AppLanguage language) {

        String searchText = q == null ? null : q.trim();
        Pageable pageable = PageRequest.of(page - 1, perPage);
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(query -> query
                        .bool(b -> {
                            b.filter(f -> f
                                    .term(t -> t
                                            .field("moderationStatus")
                                            .value("APPROVED")
                                    )
                            );

                            b.filter(f -> f
                                    .term(t -> t
                                            .field("isActive")
                                            .value(true)
                                    )
                            );

                            if (searchText != null && !searchText.isBlank()) {
                                b.should(s -> s
                                        .matchPhrasePrefix(m -> m
                                                .field("name")
                                                .query(searchText)
                                                .boost(5.0f)
                                        )
                                );

                                b.should(s -> s
                                        .match(m -> m
                                                .field("name")
                                                .query(searchText)
                                                .fuzziness("AUTO")
                                                .boost(3.0f)
                                        )
                                );

                                b.should(s -> s
                                        .match(m -> m
                                                .field("shortDescription")
                                                .query(searchText)
                                                .fuzziness("AUTO")
                                                .boost(1.0f)
                                        )
                                );

                                b.minimumShouldMatch("1");
                            }

                         /*   if (regionId != null) {
                                b.filter(f -> f
                                        .term(t -> t
                                                .field("regionId")
                                                .value(regionId)
                                        )
                                );
                            }*/

                            if (categoryId != null && !categoryId.isBlank()) {
                                b.filter(f -> f
                                        .term(t -> t
                                                .field("categoryId")
                                                .value(Long.valueOf(categoryId))
                                        )
                                );
                            }

                            return b;
                        })
                )
                .withPageable(pageable).build();
        SearchHits<ProductDocument> hits = elasticsearchOperations.search(searchQuery, ProductDocument.class);
        List<ProductSearchResponse> content = hits.stream()
                .map(SearchHit::getContent)
                .map(this::toSearchResponse)
                .toList();

        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }


    private ProductSearchResponse toSearchResponse(ProductDocument doc) {
        return ProductSearchResponse.builder()
                .id(Long.parseLong(doc.getId()))
                .name(doc.getName())
                .slug(doc.getSlug())
                .price(doc.getPrice())
                .currency(doc.getCurrency())
                .primaryImageUrl(doc.getPrimaryImageUrl())
                .build();
    }
}
