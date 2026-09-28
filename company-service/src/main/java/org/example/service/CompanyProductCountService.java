package org.example.service;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public interface CompanyProductCountService {
    Long get(Long companyId);

    <T> List<T> enrich(List<T> items, Function<T, Long> id, BiConsumer<T, Long> setter);
}
