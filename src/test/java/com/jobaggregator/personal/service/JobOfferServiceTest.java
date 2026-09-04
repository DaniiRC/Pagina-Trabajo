package com.jobaggregator.personal.service;

import com.jobaggregator.personal.dto.JobOfferResponseDto;
import com.jobaggregator.personal.model.JobOffer;
import com.jobaggregator.personal.model.JobSource;
import com.jobaggregator.personal.model.JobStatus;
import com.jobaggregator.personal.repository.JobOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({JobOfferService.class, SpanishGeographyService.class, TechnologyParserService.class})
class JobOfferServiceTest {

    @Autowired
    private JobOfferService jobOfferService;

    @Autowired
    private JobOfferRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // Oferta 1: Tecnoempleo España (Madrid)
        JobOffer offerSpain = JobOffer.builder()
                .title("Programador Junior Java Spring Boot")
                .companyName("Indra")
                .shortDescription("Desarrollo de microservicios en Madrid")
                .fullDescription("Buscamos desarrollador junior java en Madrid, España")
                .url("https://example.com/job/spain-1")
                .publishedDate(LocalDateTime.now())
                .requiredTechnologies(Set.of("Java", "Spring Boot"))
                .studyLevels(Set.of("DAM", "DAW"))
                .status(JobStatus.NUEVA)
                .source(JobSource.TECNOEMPLEO)
                .location("Madrid, España")
                .country("España")
                .autonomousCommunity("Comunidad de Madrid")
                .provinceOrCity("Madrid")
                .isRemote(false)
                .build();

        // Oferta 2: Oferta en Alemania
        JobOffer offerGermany = JobOffer.builder()
                .title("Senior Java Developer")
                .companyName("Berlin Tech")
                .shortDescription("Cloud microservices in Berlin")
                .fullDescription("Job in Berlin, Germany")
                .url("https://example.com/job/germany-1")
                .publishedDate(LocalDateTime.now())
                .requiredTechnologies(Set.of("Java"))
                .studyLevels(Set.of("DAM"))
                .status(JobStatus.NUEVA)
                .source(JobSource.ARBEITNOW)
                .location("Berlin, Germany")
                .country("Alemania")
                .provinceOrCity("Berlin")
                .isRemote(false)
                .build();

        repository.saveAll(List.of(offerSpain, offerGermany));
    }

    /**
     * Filtra por country = "España" (parámetro 6).
     * El parámetro 5 es location (no country).
     */
    @Test
    void testFilterByCountrySpain() {
        Page<JobOfferResponseDto> result = jobOfferService.getOffers(
                null, null, null, null, null, "España", null, null, null, null, 0, 10
        //       status kw   tech  rem  loc   country  comm  prov  mod   studies
        );
        assertEquals(1, result.getTotalElements(), "Debe encontrar exactamente 1 oferta en España");
        assertEquals("Indra", result.getContent().get(0).getCompanyName());
    }

    /**
     * Filtra por country = "Espana" (sin acento). La normalización interna convierte
     * "Espana" → "espana" que activa el bloque España y luego busca con acento.
     */
    @Test
    void testFilterByCountryEspanaWithoutAccent() {
        Page<JobOfferResponseDto> result = jobOfferService.getOffers(
                null, null, null, null, null, "Espana", null, null, null, null, 0, 10
        );
        assertEquals(1, result.getTotalElements(), "Debe encontrar la oferta buscando 'Espana' sin acento");
    }

    /**
     * Búsqueda por keyword "España". El keyword search incluye location, country,
     * provinceOrCity y autonomousCommunity. "España" debe coincidir con country="España"
     * o location="Madrid, España".
     */
    @Test
    void testSearchKeywordEspana() {
        Page<JobOfferResponseDto> result = jobOfferService.getOffers(
                null, "España", null, null, null, null, null, null, null, null, 0, 10
        );
        System.out.println("Keyword 'España' found: " + result.getTotalElements());
        assertTrue(result.getTotalElements() >= 1, "Buscar 'España' como palabra clave debe encontrar ofertas en España");
    }

    /**
     * Buscar por province "Madrid" debe encontrar la oferta en Madrid.
     */
    @Test
    void testFilterByProvinceMadrid() {
        Page<JobOfferResponseDto> result = jobOfferService.getOffers(
                null, null, null, null, null, null, null, "Madrid", null, null, 0, 10
        );
        assertTrue(result.getTotalElements() >= 1, "Filtrar por provincia Madrid debe encontrar ofertas en Madrid");
    }

    /**
     * Sin filtros: deben aparecer TODAS las ofertas (España + Alemania).
     */
    @Test
    void testNoFilterReturnsAll() {
        Page<JobOfferResponseDto> result = jobOfferService.getOffers(
                null, null, null, null, null, null, null, null, null, null, 0, 10
        );
        assertEquals(2, result.getTotalElements(), "Sin filtros deben aparecer todas las ofertas");
    }
}
