package org.example.ai.business.service;

import jakarta.annotation.PreDestroy;
import org.example.ai.business.dto.BusinessContact;
import org.example.ai.business.dto.BusinessContactLookup;
import org.example.ai.business.dto.BusinessContactStatus;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.business.remote.PublicCompanyContactClient;
import org.example.ai.business.remote.RemoteCompanyDetail;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Hydrates only fields already exposed by company-service's public company-by-slug endpoint. */
@Component
public class PublicCompanyContactHydrator {

    private final PublicCompanyContactClient client;
    private final int maxLookups;
    private final ExecutorService executor;

    public PublicCompanyContactHydrator(
            PublicCompanyContactClient client,
            @Value("${ai.business-contact.max-lookups:8}") int maxLookups,
            @Value("${ai.business-contact.parallelism:4}") int parallelism) {
        this.client = client;
        this.maxLookups = Math.max(0, Math.min(maxLookups, 20));
        int threads = Math.max(1, Math.min(parallelism, 8));
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "ai-public-contact-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(threads * 4), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public BusinessContactLookup hydrate(String slug, ToolExecutionContext context) {
        return lookup(slug, context);
    }

    /**
     * Starts all capped lookups before awaiting them, so selected cards do not incur serial
     * downstream latency. Missing/slow contacts are omitted while the indexed results survive.
     */
    public Map<String, BusinessContactLookup> hydrateBatch(List<String> slugs, ToolExecutionContext context) {
        if (slugs == null || slugs.isEmpty() || maxLookups == 0) return Map.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String slug : slugs) {
            if (slug != null && !slug.isBlank()) unique.add(slug);
            if (unique.size() >= maxLookups) break;
        }
        List<CompletableFuture<ContactLookup>> futures = new ArrayList<>(unique.size());
        for (String slug : unique) {
            try {
                futures.add(CompletableFuture.supplyAsync(
                        () -> new ContactLookup(slug, lookup(slug, context)), executor));
            } catch (RejectedExecutionException saturated) {
                // Optional enrichment is shed under load; indexed discovery remains available.
            }
        }
        Map<String, BusinessContactLookup> contacts = new LinkedHashMap<>();
        for (CompletableFuture<ContactLookup> future : futures) {
            ContactLookup result = future.join();
            contacts.put(result.slug(), result.lookup());
        }
        return Map.copyOf(contacts);
    }

    private BusinessContactLookup lookup(String slug, ToolExecutionContext context) {
        if (slug == null || slug.isBlank()) {
            return BusinessContactLookup.status(BusinessContactStatus.NOT_CHECKED);
        }
        try {
            String language = PlatformLanguage.header(context == null ? null : context.acceptLanguage());
            RemoteCompanyDetail company = client.fetch(slug, language);
            if (company == null) {
                return BusinessContactLookup.status(BusinessContactStatus.TEMPORARILY_UNAVAILABLE);
            }
            BusinessContact contact = new BusinessContact(company.phonePrimary(), company.phoneSecondary(),
                    company.website(), company.address());
            return hasValue(contact)
                    ? BusinessContactLookup.available(contact)
                    : BusinessContactLookup.status(BusinessContactStatus.NO_PUBLIC_FIELDS);
        } catch (GatewayNotFoundException notFound) {
            return BusinessContactLookup.status(BusinessContactStatus.NOT_FOUND);
        } catch (RuntimeException ignored) {
            // Contact enrichment is optional: indexed discovery must remain available on a downstream outage.
            return BusinessContactLookup.status(BusinessContactStatus.TEMPORARILY_UNAVAILABLE);
        }
    }

    private boolean hasValue(BusinessContact contact) {
        return hasText(contact.phonePrimary()) || hasText(contact.phoneSecondary())
                || hasText(contact.website()) || hasText(contact.address());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record ContactLookup(String slug, BusinessContactLookup lookup) {}
}
