package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.internal.ProductClient;
import org.example.service.CompanyProductCountService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyProductCountServiceImpl implements CompanyProductCountService {
    private final ProductClient productClient;

    @Override
    public Long get(Long companyId) {
        return load(List.of(companyId)).get(companyId);
    }

    @Override
    public <T> List<T> enrich(List<T> items, Function<T, Long> id, BiConsumer<T, Long> setter) {
        Map<Long, Long> counts = load(items.stream().map(id).filter(Objects::nonNull).distinct().toList());
        items.forEach(item -> setter.accept(item, counts.get(id.apply(item))));
        return items;
    }

    private Map<Long, Long> load(List<Long> ids) {
        Map<Long, Long> result = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 500) {
            try {
                Map<Long, Long> batch = productClient.getCompanyProductCounts(ids.subList(start, Math.min(start + 500, ids.size())));
                if (batch != null) result.putAll(batch);
            } catch (feign.FeignException e) {
                log.warn("Company product counts unavailable, HTTP status {}", e.status());
            }
        }
        return result;
    }
}