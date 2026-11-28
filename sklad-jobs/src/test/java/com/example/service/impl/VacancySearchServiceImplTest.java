package com.example.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.ElasticsearchClusterClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.example.document.VacancyDocument;
import com.example.repository.VacancySearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.EntityAsMap;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.elasticsearch.repository.support.ElasticsearchRepositoryFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VacancySearchServiceImplTest {
    private ElasticsearchClient client;
    private ElasticsearchIndicesClient indicesClient;
    private ElasticsearchTemplate operations;
    private VacancySearchServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(ElasticsearchClient.class);
        indicesClient = mock(ElasticsearchIndicesClient.class);
        var transport = mock(ElasticsearchTransport.class);
        when(client._transport()).thenReturn(transport);
        when(transport.jsonpMapper()).thenReturn(new JacksonJsonpMapper());
        when(client.indices()).thenReturn(indicesClient);
        when(indicesClient._transport()).thenReturn(transport);
        var clusterClient = mock(ElasticsearchClusterClient.class);
        when(client.cluster()).thenReturn(clusterClient);
        when(clusterClient._transport()).thenReturn(transport);
        // Match Spring Boot's converter setup, including built-in BigDecimal conversions.
        var conversions = new ElasticsearchCustomConversions(java.util.List.of());
        var mappingContext = new SimpleElasticsearchMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();
        var converter = new MappingElasticsearchConverter(mappingContext);
        converter.setConversions(conversions);
        converter.afterPropertiesSet();
        operations = new ElasticsearchTemplate(client, converter);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(true));
        service = new VacancySearchServiceImpl(createRepository(), operations);
        clearInvocations(client, indicesClient);
    }

    private VacancySearchRepository createRepository() {
        return new ElasticsearchRepositoryFactory(operations).getRepository(VacancySearchRepository.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableElasticsearchRepositories(basePackageClasses = VacancySearchRepository.class)
    static class RepositoryConfig {
    }

    @Test
    void initializesIndexWithJsonAnalyzerAndDocumentMapping() throws Exception {
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(false));
        when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(
                CreateIndexResponse.of(response -> response.index("vacancy-v2").acknowledged(true).shardsAcknowledged(true)));

        // The repository is discovered in the same com.example package tree as the application.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("elasticsearchTemplate",
                    org.springframework.data.elasticsearch.core.ElasticsearchOperations.class, () -> operations);
            context.register(RepositoryConfig.class);
            context.refresh();
            assertNotNull(context.getBean(VacancySearchRepository.class));
        }

        var request = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesClient).create(request.capture());
        assertEquals("vacancy-v2", request.getValue().index());
        var analysis = request.getValue().settings().analysis();
        var analyzer = analysis.analyzer().get("uz_ru_translit_analyzer").custom();
        assertEquals("standard", analyzer.tokenizer());
        assertEquals(java.util.List.of("cyrillic_to_latin"), analyzer.charFilter());
        assertEquals(java.util.List.of("lowercase", "asciifolding"), analyzer.filter());
        var transliteration = analysis.charFilter().get("cyrillic_to_latin").definition().mapping();
        assertTrue(transliteration.mappings().contains("\u049A => q"), transliteration.mappings().toString());
        assertTrue(transliteration.mappings().contains("' =>"));
        var properties = request.getValue().mappings().properties();
        for (String field : java.util.List.of("positionName", "shortDescription", "requirements", "workingConditions")) {
            assertEquals("uz_ru_translit_analyzer", properties.get(field).text().analyzer(), field);
            assertEquals("uz_ru_translit_analyzer", properties.get(field).text().searchAnalyzer(), field);
        }
        assertTrue(properties.get("lng").isDouble());
        assertTrue(properties.get("lat").isDouble());
        assertEquals(100.0, properties.get("price").scaledFloat().scalingFactor());
    }

    @Test
    void startupLeavesExistingIndexUntouched() throws Exception {
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(true));
        assertNotNull(createRepository());
        verify(indicesClient, never()).create(any(CreateIndexRequest.class));
    }

    @Test
    void createAndFullUpdateSendSameDatabaseIdToVacancyIndex() throws Exception {
        var response = mock(IndexResponse.class);
        when(response.id()).thenReturn("42");
        when(response.index()).thenReturn("vacancy-v2");
        when(client.index(any(IndexRequest.class))).thenReturn(response);
        var document = VacancyDocument.builder().id("42").positionName("Java developer")
                .lng(69.2401).lat(41.2995).build();

        service.index(document);
        document.setPositionName("Senior Java developer");
        service.update(document);

        var requests = ArgumentCaptor.forClass(IndexRequest.class);
        verify(client, times(2)).index(requests.capture());
        for (var request : requests.getAllValues()) {
            assertEquals("vacancy-v2", request.index());
            assertEquals("42", request.id());
        }
        var updatedSource = (Map<?, ?>) requests.getAllValues().get(1).document();
        assertEquals("Senior Java developer", updatedSource.get("positionName"));
        assertEquals(69.2401, updatedSource.get("lng"));
        assertEquals(0L, updatedSource.get("viewsCountCache"));
    }

    @Test
    void getUsesCorrectIndexAndIdAndReturnsEmptyForMissingDocument() throws Exception {
        GetResponse<EntityAsMap> response = GetResponse.of(result -> result.index("vacancy-v2").id("42").found(false));
        when(client.get(any(GetRequest.class), eq(EntityAsMap.class))).thenReturn(response);
        assertTrue(service.get(42L).isEmpty());
        var request = ArgumentCaptor.forClass(GetRequest.class);
        verify(client).get(request.capture(), eq(EntityAsMap.class));
        assertEquals("vacancy-v2", request.getValue().index());
        assertEquals("42", request.getValue().id());
    }

    @Test
    void deleteTargetsOneDocumentInVacancyIndex() throws Exception {
        var response = mock(DeleteResponse.class);
        when(response.id()).thenReturn("42");
        when(client.delete(any(DeleteRequest.class))).thenReturn(response);
        service.delete(42L);
        var request = ArgumentCaptor.forClass(DeleteRequest.class);
        verify(client).delete(request.capture());
        assertEquals("vacancy-v2", request.getValue().index());
        assertEquals("42", request.getValue().id());
        verify(indicesClient).refresh(any(RefreshRequest.class));
    }

    @Test
    void documentRoundTripPreservesSalaryCoordinatesAndPublicationTime() {
        var document = VacancyDocument.builder().id("42").price(new BigDecimal("12345.67"))
                .lng(69.2401).lat(41.2995).publishedAt(Instant.parse("2026-10-09T07:00:00.123Z")).build();
        var converter = operations.getElasticsearchConverter();
        var source = converter.mapObject(document);
        var restored = converter.read(VacancyDocument.class, source);
        assertEquals(document.getPrice(), restored.getPrice());
        assertEquals(document.getLng(), restored.getLng());
        assertEquals(document.getLat(), restored.getLat());
        assertEquals(document.getPublishedAt(), restored.getPublishedAt());
        assertEquals(0L, restored.getViewsCountCache());
    }

    @Test
    void rejectsIdsThatCannotBeReadOrDeletedUsingDatabaseId() {
        for (String id : new String[]{null, "", " ", "abc", "0", "-1", "042", "+42"}) {
            var document = VacancyDocument.builder().id(id).build();
            assertThrows(IllegalArgumentException.class, () -> service.index(document));
            assertThrows(IllegalArgumentException.class, () -> service.update(document));
        }
        assertThrows(IllegalArgumentException.class, () -> service.index(null));
        assertThrows(IllegalArgumentException.class, () -> service.get(null));
        assertThrows(IllegalArgumentException.class, () -> service.delete(0L));
        verify(client, never())._transport();
    }
}
