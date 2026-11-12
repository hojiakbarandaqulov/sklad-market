package com.example.service.impl;

import com.example.dto.AddressSuggestion;
import com.example.service.AddressService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AddressServiceImpl implements AddressService {

    private static final String ADDRESS_FILE = "uz-addresses/addresses.json";
    private static final int MIN_QUERY_LENGTH = 3;
    private static final int MAX_RESULTS = 5;

    // Manzillar servis ishga tushganda yuklanadi va xotirada turadi.
    private final List<AddressSuggestion> addresses;

    public AddressServiceImpl(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(ADDRESS_FILE);

        // Jackson JAR ichidagi JSON massivini Java ro‘yxatiga aylantiradi.
        // try-with-resources o‘qish tugagach oqimni avtomatik yopadi.
        try (InputStream input = resource.getInputStream()) {
            this.addresses = objectMapper.readValue(
                    input, new TypeReference<List<AddressSuggestion>>() {});
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Manzillar faylini o‘qib bo‘lmadi: " + ADDRESS_FILE, exception);
        }
    }

    @Override
    public List<AddressSuggestion> regionSearch(String q) {
        if (q == null) {
            return List.of();
        }

        String query = q.strip().toLowerCase(Locale.ROOT);
        if (query.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }

        List<AddressSuggestion> suggestions = new ArrayList<>(MAX_RESULTS);

        for (AddressSuggestion address : addresses) {
            // Label to‘liq manzil. Masalan, "Chust" yozilsa,
            // labelida Chust tumani bor mahallalar ham chiqadi.
            String fullAddress = address.label().toLowerCase(Locale.ROOT);
            if (!fullAddress.contains(query)) {
                continue;
            }

            suggestions.add(address);

            // Birinchi 5 ta mos manzil topilgach, qidiruvni to‘xtatamiz.
            if (suggestions.size() == MAX_RESULTS) {
                break;
            }
        }

        return suggestions;
    }

}
