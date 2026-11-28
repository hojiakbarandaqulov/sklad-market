package com.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.document.VacancyDocument;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.Setting;
import org.springframework.data.elasticsearch.core.ResourceUtil;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransliteratedSearchTest {
    // Execute the configured pipeline with Lucene, which supplies Elasticsearch's token filters.
    // This test needs no running Elasticsearch server.
    @Test
    void findsLatinAndCyrillicTextInBothDirectionsInEverySearchField() throws Exception {
        var path = VacancyDocument.class.getAnnotation(Setting.class).settingPath();
        var settings = new ObjectMapper().readTree(ResourceUtil.readFileFromClasspath(path)).get("analysis");
        var config = settings.get("analyzer").get("uz_ru_translit_analyzer");
        assertEquals("standard", config.get("tokenizer").asText());
        assertEquals("[\"cyrillic_to_latin\"]", config.get("char_filter").toString());
        assertEquals("[\"lowercase\",\"asciifolding\"]", config.get("filter").toString());
        var map = new NormalizeCharMap.Builder();
        for (var rule : settings.get("char_filter").get("cyrillic_to_latin").get("mappings")) {
            var parts = rule.asText().split("\\s*=>\\s*", -1);
            map.add(parts[0], parts[1]);
        }
        var mapping = map.build();
        try (Analyzer analyzer = new Analyzer() {
            @Override
            protected Reader initReader(String fieldName, Reader reader) {
                return new MappingCharFilter(mapping, reader);
            }

            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                var tokenizer = new StandardTokenizer();
                return new TokenStreamComponents(tokenizer, new ASCIIFoldingFilter(new LowerCaseFilter(tokenizer)));
            }
        }) {
            String[][] examples = {
                    {"armatura", "\u0410\u0420\u041c\u0410\u0422\u0423\u0420\u0410"},
                    {"beton", "\u0431\u0435\u0442\u043e\u043d"},
                    {"qurilish", "\u049a\u0423\u0420\u0418\u041b\u0418\u0428"},
                    {"o'g'it", "\u040e\u0492\u0418\u0422"},
                    {"g\u2018isht", "\u0492\u0418\u0428\u0422"}
            };
            for (var field : List.of("positionName", "shortDescription", "requirements", "workingConditions")) {
                var annotation = VacancyDocument.class.getDeclaredField(field).getAnnotation(Field.class);
                assertEquals("uz_ru_translit_analyzer", annotation.analyzer(), field);
                assertEquals("uz_ru_translit_analyzer", annotation.searchAnalyzer(), field);
                for (var example : examples) {
                    assertEquals(tokens(analyzer, field, example[0]), tokens(analyzer, field, example[1]), field);
                    assertFinds(analyzer, field, example[0], example[1]);
                    assertFinds(analyzer, field, example[1], example[0]);
                }
                assertFinds(analyzer, field, "arma", "\u0410\u0420\u041c\u0410\u0422\u0423\u0420\u0410");
            }
        }
    }

    private List<String> tokens(Analyzer analyzer, String field, String text) throws Exception {
        var result = new ArrayList<String>();
        try (var stream = analyzer.tokenStream(field, text)) {
            var term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                result.add(term.toString());
            }
            stream.end();
        }
        return result;
    }

    private void assertFinds(Analyzer analyzer, String field, String query, String indexedText) throws Exception {
        try (var directory = new ByteBuffersDirectory()) {
            try (var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                var document = new org.apache.lucene.document.Document();
                document.add(new TextField(field, indexedText, org.apache.lucene.document.Field.Store.NO));
                writer.addDocument(document);
            }
            try (var reader = DirectoryReader.open(directory)) {
                var terms = tokens(analyzer, field, query);
                assertEquals(1, terms.size());
                var prefix = new PrefixQuery(new Term(field, terms.get(0)));
                assertEquals(1, new IndexSearcher(reader).count(prefix), field + ": " + query + " -> " + indexedText);
            }
        }
    }
}

