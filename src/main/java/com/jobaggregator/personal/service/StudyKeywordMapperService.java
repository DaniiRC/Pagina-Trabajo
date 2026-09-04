package com.jobaggregator.personal.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StudyKeywordMapperService {

    private static final Map<String, String> STUDY_KEYWORDS = new LinkedHashMap<>();

    private final String activeProfilesConfig;
    private final Set<String> activeProfiles;

    public StudyKeywordMapperService() {
        this("DAM,DAM_JAVA,DAM_MOBILE,PRACTICAS_BECA");
    }

    public StudyKeywordMapperService(
            @org.springframework.beans.factory.annotation.Value("${jobs.studies.active:${JOBS_ACTIVE_STUDIES:DAM,DAM_JAVA,DAM_MOBILE,PRACTICAS_BECA}}")
            String activeProfilesConfig) {
        this.activeProfilesConfig = activeProfilesConfig != null ? activeProfilesConfig : "DAM,DAM_JAVA,DAM_MOBILE,PRACTICAS_BECA";
        this.activeProfiles = new LinkedHashSet<>();
        for (String p : this.activeProfilesConfig.split(",")) {
            if (!p.trim().isBlank()) {
                this.activeProfiles.add(p.trim().toUpperCase());
            }
        }
    }

    static {
        STUDY_KEYWORDS.put("SMR", "sistemas redes soporte helpdesk");
        STUDY_KEYWORDS.put("DAM", "desarrollo multiplataforma java android flutter");
        STUDY_KEYWORDS.put("DAW", "desarrollo web frontend backend react");
        STUDY_KEYWORDS.put("ASIR", "administrador sistemas redes linux cloud");
        STUDY_KEYWORDS.put("DEVOPS", "devops cloud kubernetes docker ci cd");
        STUDY_KEYWORDS.put("GRADO_INFORMATICA", "ingenieria informatica desarrollador software backend");
        STUDY_KEYWORDS.put("INGENIERIA", "software engineer backend arquitectura");
        STUDY_KEYWORDS.put("BOOTCAMP", "junior desarrollador web fullstack javascript python");

        // Specific Junior DAM / Early-Career Profiles
        STUDY_KEYWORDS.put("DAM_JAVA", "desarrollador junior java spring boot");
        STUDY_KEYWORDS.put("DAM_MOBILE", "desarrollador junior android kotlin flutter");
        STUDY_KEYWORDS.put("DAM_DOTNET", "desarrollador junior c# .net backend");
        STUDY_KEYWORDS.put("DAM_FULLSTACK", "programador junior frontend backend web");
        STUDY_KEYWORDS.put("PRACTICAS_BECA", "practicas desarrollo becario programador junior sin experiencia");
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

    /**
     * Devuelve solo los perfiles configurados como activos (por defecto DAM Junior y prácticas).
     * Evita consultas a perfiles de ingeniería/sénior que consumen cuotas y traen ofertas inalcanzables.
     */
    public Map<String, String> getActiveStudyMappings() {
        Map<String, String> active = new LinkedHashMap<>();
        for (String profile : activeProfiles) {
            if (STUDY_KEYWORDS.containsKey(profile)) {
                active.put(profile, STUDY_KEYWORDS.get(profile));
            }
        }
        return active.isEmpty() ? Map.of("DAM", STUDY_KEYWORDS.get("DAM")) : Collections.unmodifiableMap(active);
    }

    public Set<String> getActiveProfileNames() {
        return Collections.unmodifiableSet(activeProfiles);
    }

    public Map<String, String> getAllStudyMappings() {
        return Collections.unmodifiableMap(STUDY_KEYWORDS);
    }
}
