package com.jobaggregator.personal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpanishGeographyServiceTest {

    private SpanishGeographyService geographyService;

    @BeforeEach
    void setUp() {
        geographyService = new SpanishGeographyService();
    }

    @Test
    void testInferSpanishProvinces() {
        SpanishGeographyService.GeoResult madrid = geographyService.inferGeography("Madrid, Spain", "Java Dev", "", false);
        assertEquals("España", madrid.country());
        assertEquals("Comunidad de Madrid", madrid.autonomousCommunity());
        assertEquals("Madrid", madrid.provinceOrCity());

        SpanishGeographyService.GeoResult sevilla = geographyService.inferGeography("Sevilla", "Backend Dev", "", false);
        assertEquals("España", sevilla.country());
        assertEquals("Andalucía", sevilla.autonomousCommunity());
        assertEquals("Sevilla", sevilla.provinceOrCity());

        SpanishGeographyService.GeoResult barcelona = geographyService.inferGeography("Barcelona, España", "Frontend Dev", "", false);
        assertEquals("España", barcelona.country());
        assertEquals("Cataluña", barcelona.autonomousCommunity());
        assertEquals("Barcelona", barcelona.provinceOrCity());
    }

    @Test
    void testInferForeignEuropeanCities() {
        SpanishGeographyService.GeoResult hamburg = geographyService.inferGeography("Hamburg", "DevOps Engineer", "", false);
        assertEquals("Alemania", hamburg.country());
        assertNull(hamburg.autonomousCommunity());
        assertEquals("Europa", hamburg.continent());

        SpanishGeographyService.GeoResult london = geographyService.inferGeography("London", "Solutions Engineer", "", false);
        assertEquals("Reino Unido", london.country());
        assertNull(london.autonomousCommunity());
        assertEquals("Europa", london.continent());

        SpanishGeographyService.GeoResult bury = geographyService.inferGeography("Bury St Edmunds", "Tech Lead", "", false);
        assertEquals("Reino Unido", bury.country());
        assertNull(bury.autonomousCommunity());
    }

    @Test
    void testInferRemoteGlobal() {
        SpanishGeographyService.GeoResult remote = geographyService.inferGeography("Worldwide", "Senior Python Engineer", "", true);
        assertEquals("Worldwide", remote.country());
        assertEquals("Global", remote.continent());
        assertNull(remote.autonomousCommunity());
    }
}
