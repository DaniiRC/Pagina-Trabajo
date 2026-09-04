package com.jobaggregator.personal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StudyKeywordMapperServiceTest {

    private StudyKeywordMapperService mapperService;

    @BeforeEach
    void setUp() {
        mapperService = new StudyKeywordMapperService();
    }

    @Test
    void testGetKeywordsForKnownStudies() {
        assertEquals("sistemas redes soporte helpdesk", mapperService.getKeywordsForStudy("SMR"));
        assertEquals("desarrollo multiplataforma java android flutter", mapperService.getKeywordsForStudy("dam"));
        assertEquals("desarrollo web frontend backend react", mapperService.getKeywordsForStudy("DAW"));
        assertEquals("administrador sistemas redes linux cloud", mapperService.getKeywordsForStudy("asir"));
        assertEquals("devops cloud kubernetes docker ci cd", mapperService.getKeywordsForStudy("DEVOPS"));
    }

    @Test
    void testGetKeywordsForUnknownOrNullStudy() {
        assertEquals("desarrollador programador informatica", mapperService.getKeywordsForStudy(null));
        assertEquals("desarrollador programador informatica", mapperService.getKeywordsForStudy("   "));
        assertEquals("desarrollador programador software", mapperService.getKeywordsForStudy("OTHER_UNKNOWN"));
    }

    @Test
    void testGetKeywordsForMultipleStudies() {
        List<String> keywords = mapperService.getKeywordsForStudies(List.of("DAM", "DAW"));
        assertEquals(2, keywords.size());
        assertTrue(keywords.contains("desarrollo multiplataforma java android flutter"));
        assertTrue(keywords.contains("desarrollo web frontend backend react"));
    }

    @Test
    void testGetAllStudyMappings() {
        Map<String, String> mappings = mapperService.getAllStudyMappings();
        assertNotNull(mappings);
        assertTrue(mappings.containsKey("DAM"));
        assertTrue(mappings.containsKey("DAW"));
        assertTrue(mappings.containsKey("SMR"));
        assertTrue(mappings.containsKey("ASIR"));
        assertThrows(UnsupportedOperationException.class, () -> mappings.put("TEST", "val"));
    }
}
