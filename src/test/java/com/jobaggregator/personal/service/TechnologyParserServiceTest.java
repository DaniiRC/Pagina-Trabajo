package com.jobaggregator.personal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TechnologyParserServiceTest {

    private TechnologyParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new TechnologyParserService();
    }

    @Test
    void testExtractTechnologies() {
        String title = "Senior Java and Spring Boot Developer with Docker & AWS";
        String description = "We are seeking an engineer experienced in Linux, PostgreSQL, REST APIs, and Kubernetes.";
        List<String> tags = List.of("backend", "remote", "java");

        Set<String> techs = parserService.extractTechnologies(title, description, tags);

        assertTrue(techs.contains("Java"));
        assertTrue(techs.contains("Spring Boot"));
        assertTrue(techs.contains("Docker"));
        assertTrue(techs.contains("AWS"));
        assertTrue(techs.contains("Linux"));
        assertTrue(techs.contains("PostgreSQL"));
        assertTrue(techs.contains("Kubernetes"));
        assertTrue(techs.contains("REST API"));
    }

    @Test
    void testCleanHtmlDescription() {
        String rawHtml = "<p>Join our <strong>innovative</strong> team!</p><br/>&amp; build awesome systems.";
        String cleaned = parserService.cleanHtmlDescription(rawHtml);

        assertFalse(cleaned.contains("<p>"));
        assertFalse(cleaned.contains("</p>"));
        assertFalse(cleaned.contains("&amp;"));
        assertTrue(cleaned.contains("& build awesome systems."));
    }
}
