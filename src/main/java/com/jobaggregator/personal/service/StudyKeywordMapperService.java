package com.jobaggregator.personal.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StudyKeywordMapperService {

    private static final Map<String, String> STUDY_KEYWORDS = new LinkedHashMap<>();

    static {
        STUDY_KEYWORDS.put("SMR", "sistemas redes soporte helpdesk");
        STUDY_KEYWORDS.put("DAM", "desarrollo multiplataforma java android flutter");
        STUDY_KEYWORDS.put("DAW", "desarrollo web frontend backend react");
        STUDY_KEYWORDS.put("ASIR", "administrador sistemas redes linux cloud");
        STUDY_KEYWORDS.put("DEVOPS", "devops cloud kubernetes docker ci cd");
        STUDY_KEYWORDS.put("GRADO_INFORMATICA", "ingenieria informatica desarrollador software backend");
        STUDY_KEYWORDS.put("INGENIERIA", "software engineer backend arquitectura");
        STUDY_KEYWORDS.put("BOOTCAMP", "junior desarrollador web fullstack javascript python");
    }

    public String getKeywordsForStudy(String studyLevel) {
        if (studyLevel == null || studyLevel.isBlank()) {
            return "desarrollador programador informatica";
        }
        return STUDY_KEYWORDS.getOrDefault(studyLevel.toUpperCase().trim(), "desarrollador programador software");
    }

    public List<String> getKeywordsForStudies(Collection<String> studyLevels) {
        if (studyLevels == null || studyLevels.isEmpty()) {
            return List.of(
                    STUDY_KEYWORDS.get("DAM"),
                    STUDY_KEYWORDS.get("DAW"),
                    STUDY_KEYWORDS.get("ASIR")
            );
        }
        List<String> result = new ArrayList<>();
        for (String s : studyLevels) {
            if (s != null && !s.isBlank()) {
                result.add(getKeywordsForStudy(s));
            }
        }
        return result.isEmpty() ? List.of(STUDY_KEYWORDS.get("DAM")) : result;
    }

    public Map<String, String> getAllStudyMappings() {
        return Collections.unmodifiableMap(STUDY_KEYWORDS);
    }
}
