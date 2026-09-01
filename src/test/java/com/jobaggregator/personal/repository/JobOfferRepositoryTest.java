package com.jobaggregator.personal.repository;

import com.jobaggregator.personal.model.JobOffer;
import com.jobaggregator.personal.model.JobSource;
import com.jobaggregator.personal.model.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class JobOfferRepositoryTest {

    @Autowired
    private JobOfferRepository repository;

    @Test
    void testSaveAndFindJobOffer() {
        JobOffer offer = JobOffer.builder()
                .title("Java Backend Engineer")
                .companyName("Tech Innovators")
                .shortDescription("Developing microservices with Spring Boot and PostgreSQL")
                .url("https://example.com/job/12345")
                .publishedDate(LocalDateTime.now())
                .requiredTechnologies(Set.of("Java", "Spring Boot", "PostgreSQL"))
                .status(JobStatus.NUEVA)
                .source(JobSource.REMOTIVE)
                .isRemote(true)
                .build();

        JobOffer saved = repository.save(offer);
        assertNotNull(saved.getId());
        assertTrue(repository.existsByUrl("https://example.com/job/12345"));

        assertEquals(1, repository.countByStatus(JobStatus.NUEVA));

        Page<JobOffer> searchResults = repository.findWithFilters(JobStatus.NUEVA, "Innovators", PageRequest.of(0, 10));
        assertEquals(1, searchResults.getTotalElements());
    }
}
