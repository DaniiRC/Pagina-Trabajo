package com.jobaggregator.personal.service;

import com.jobaggregator.personal.model.JobModality;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class TechnologyParserService {

    private static final Map<String, Pattern> TECH_PATTERNS = new LinkedHashMap<>();

    static {
        // Multiplatform Development & Languages
        addTech("Java", "\\bjava\\b(?!script)");
        addTech("Spring Boot", "\\b(spring boot|spring framework|spring)\\b");
        addTech("Kotlin", "\\bkotlin\\b");
        addTech("Swift", "\\bswift\\b(?!ui)");
        addTech("SwiftUI", "\\bswiftui\\b");
        addTech("Flutter", "\\bflutter\\b");
        addTech("Dart", "\\bdart\\b");
        addTech("React Native", "\\breact[- ]native\\b");
        addTech("React", "\\breact(\\.js)?\\b(?![- ]native)");
        addTech("Vue.js", "\\bvue(\\.js)?\\b");
        addTech("Angular", "\\bangular\\b");
        addTech("TypeScript", "\\btypescript\\b|\\bts\\b");
        addTech("JavaScript", "\\bjavascript\\b|\\bjs\\b");
        addTech("Python", "\\bpython\\b");
        addTech("Django", "\\bdjango\\b");
        addTech("FastAPI", "\\bfastapi\\b");
        addTech("Node.js", "\\bnode(\\.js)?\\b");
        addTech("C#", "\\bc#\\b|\\bcsharp\\b");
        addTech(".NET", "\\b(\\.)?net( core)?\\b");
        addTech("Go", "\\b(golang|go)\\b");
        addTech("Rust", "\\brust\\b");
        addTech("C++", "\\bc\\+\\+\\b");
        addTech("PHP", "\\bphp\\b");

        // Systems, DevOps, Networks & Cloud
        addTech("Linux", "\\blinux\\b|\\bubuntu\\b|\\bdebian\\b|\\bredhat\\b|\\bcentos\\b");
        addTech("Docker", "\\bdocker\\b");
        addTech("Kubernetes", "\\b(kubernetes|k8s)\\b");
        addTech("DevOps", "\\bdevops\\b");
        addTech("CI/CD", "\\b(ci/cd|cicd|github actions|gitlab ci|jenkins)\\b");
        addTech("Terraform", "\\bterraform\\b");
        addTech("Ansible", "\\bansible\\b");
        addTech("AWS", "\\b(aws|amazon web services)\\b");
        addTech("Azure", "\\b(azure|microsoft azure)\\b");
        addTech("GCP", "\\b(gcp|google cloud)\\b");
        addTech("Redes/Networking", "\\b(networking|redes|tcp/ip|vpn|dns|firewall|cisco)\\b");
        addTech("Sysadmin", "\\b(sysadmin|system administrator|administrador de sistemas)\\b");
        addTech("Bash/Shell", "\\b(bash|shell scripting|sh)\\b");
        addTech("PowerShell", "\\bpowershell\\b");
        addTech("Nginx", "\\bnginx\\b");
        addTech("Apache", "\\bapache\\b");
        addTech("Ciberseguridad", "\\b(cybersecurity|seguridad|security|pentesting|soc)\\b");

        // Databases & Architectures
        addTech("SQL", "\\bsql\\b");
        addTech("PostgreSQL", "\\b(postgresql|postgres)\\b");
        addTech("MySQL", "\\bmysql\\b");
        addTech("MongoDB", "\\bmongodb\\b|\\bmongo\\b");
        addTech("Redis", "\\bredis\\b");
        addTech("REST API", "\\b(rest api|restful|rest)\\b");
        addTech("GraphQL", "\\bgraphql\\b");
        addTech("Git", "\\bgit\\b(?!hub actions)");
    }

    private static void addTech(String name, String regex) {
        TECH_PATTERNS.put(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    public Set<String> extractTechnologies(String title, String description, Collection<String> existingTags) {
        Set<String> detected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        if (existingTags != null) {
            for (String tag : existingTags) {
                if (tag == null || tag.isBlank()) continue;
                String cleanTag = tag.trim();
                boolean matched = false;
                for (Map.Entry<String, Pattern> entry : TECH_PATTERNS.entrySet()) {
                    if (entry.getValue().matcher(cleanTag).find() || cleanTag.equalsIgnoreCase(entry.getKey())) {
                        detected.add(entry.getKey());
                        matched = true;
                    }
                }
                if (!matched && cleanTag.length() <= 20) {
                    detected.add(capitalize(cleanTag));
                }
            }
        }

        String combinedText = (title != null ? title : "") + " " + (description != null ? description : "");
        for (Map.Entry<String, Pattern> entry : TECH_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(combinedText).find()) {
                detected.add(entry.getKey());
            }
        }

        return detected;
    }

    public boolean isTechJob(String title, String description, Collection<String> tags, Set<String> detectedTechs) {
        if (detectedTechs != null && !detectedTechs.isEmpty()) {
            return true;
        }

        String titleNorm = SpanishGeographyService.removeAccents(title != null ? title.toLowerCase() : "");
        String descNorm = SpanishGeographyService.removeAccents(description != null ? description.toLowerCase() : "");

        // Obvious non-tech roles to explicitly reject
        Pattern nonTechPattern = Pattern.compile(
                "\\b(spa|beauty|wellness|massage|masajista|nurse|enfermero|enfermera|physiotherapist|fisioterapeuta|cook|chef|cocinero|cocinera|waiter|waitress|camarero|camarera|hotel|receptionist|recepcionista|driver|conductor|conductora|limpieza|cleaner|plumber|electrician|electricista|carpenter|carpintero|real estate|inmobiliaria|accountant|contable|store manager|retail|dependiente|dependienta|sales assistant|cashier|cajero|cajera|athlete|fitness|marketing|ventas|sales representative|hr generalist|recruiter|copywriter|community manager)\\b",
                Pattern.CASE_INSENSITIVE
        );
        if (nonTechPattern.matcher(titleNorm).find()) {
            return false;
        }

        // Tech tags check
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null) continue;
                String t = tag.toLowerCase().trim();
                if (t.contains("dev") || t.contains("tech") || t.contains("software") || t.contains("engineer") ||
                    t.contains("program") || t.contains("frontend") || t.contains("backend") || t.contains("fullstack") ||
                    t.contains("sistemas") || t.contains("cloud") || t.contains("it") || t.contains("data") ||
                    t.contains("qa") || t.contains("security") || t.contains("web") || t.contains("mobile")) {
                    return true;
                }
            }
        }

        // Tech role keywords in title
        Pattern techRolePattern = Pattern.compile(
                "\\b(desarrollador|desarrolladora|programador|programadora|developer|engineer|ingeniero|ingeniera|software|frontend|backend|fullstack|full-stack|devops|sysadmin|sistemas|soporte tecnico|helpdesk|microinformatico|qa|tester|cloud|ciberseguridad|cybersecurity|architect|arquitecto|dba|database|scrum master|product owner|tech lead|mobile developer|android|ios|flutter|data engineer|data scientist|data analyst|machine learning)\\b",
                Pattern.CASE_INSENSITIVE
        );
        if (techRolePattern.matcher(titleNorm).find()) {
            return true;
        }

        // Check if description has strong tech indicators (with word boundary regex)
        Pattern descTechPattern = Pattern.compile(
                "\\b(software|programacion|programming|desarrollo de software|desarrollador|base de datos|database|api rest|rest api|linux|cloud computing|kubernetes|docker|microservicios|backend|frontend)\\b",
                Pattern.CASE_INSENSITIVE
        );
        return descTechPattern.matcher(descNorm).find();
    }

    /**
     * Returns true if the offer does NOT appear to require senior-level experience.
     * Rejects offers explicitly requiring 5+ years of experience, architect/lead roles, etc.
     */
    public boolean isJuniorFriendly(String title, String description) {
        String titleNorm = SpanishGeographyService.removeAccents(title != null ? title.toLowerCase() : "");
        String descNorm  = SpanishGeographyService.removeAccents(description != null ? description.toLowerCase() : "");
        String combined  = titleNorm + " " + descNorm;

        // Reject explicit seniority signals in title
        Pattern seniorTitlePattern = Pattern.compile(
            "\\b(senior|sr\\.|lead|principal|staff|architect|arquitecto|cto|vp of engineering|" +
            "head of|director of|jefe de desarrollo|tech lead|engineering manager)\\b",
            Pattern.CASE_INSENSITIVE
        );
        if (seniorTitlePattern.matcher(titleNorm).find()) {
            return false;
        }

        // Reject if description requires many years of experience
        Pattern yearsPattern = Pattern.compile(
            "\\b([5-9]|1[0-9]|20)\\s*\\+?\\s*(a[ñn]os?|years?)\\s*(de\\s+)?(experiencia|experience)\\b|" +
            "\\b(experiencia|experience)\\s*(de|of|:)?\\s*([5-9]|1[0-9]|20)\\s*\\+?\\s*(a[ñn]os?|years?)\\b|" +
            "\\bminimum\\s+([5-9]|1[0-9])\\s*years?\\b|" +
            "\\b([5-9]\\+|1[0-9]\\+)\\s*years?\\b",
            Pattern.CASE_INSENSITIVE
        );
        if (yearsPattern.matcher(combined).find()) {
            return false;
        }

        return true;
    }

    /**
     * Computes a 0-100 junior DAM/DAW/ASIR affinity score for the offer.
     * Higher = better match for a junior developer profile.
     */
    public int computeJuniorScore(String title, String description, Set<String> technologies, Set<String> studyLevels) {
        int score = 50; // base score

        // +points for relevant study levels
        if (studyLevels != null) {
            if (studyLevels.contains("DAM"))   score += 15;
            if (studyLevels.contains("DAW"))   score += 12;
            if (studyLevels.contains("ASIR"))  score += 10;
            if (studyLevels.contains("SMR"))   score +=  8;
            if (studyLevels.contains("DEVOPS")) score += 8;
            if (studyLevels.contains("GRADO_INFORMATICA")) score -= 5;
            if (studyLevels.contains("INGENIERIA")) score -= 15;
        }

        // +points for junior/trainee keywords
        String combined = SpanishGeographyService.removeAccents(
            ((title != null ? title : "") + " " + (description != null ? description : "")).toLowerCase()
        );
        if (Pattern.compile("\\b(junior|jr\\.?|grad(uado|uate)|trainee|becario|recien titulado|sin experiencia|poca experiencia|primer empleo)\\b",
                Pattern.CASE_INSENSITIVE).matcher(combined).find()) {
            score += 20;
        }

        // +points for DAM-relevant techs
        Set<String> damTechs = Set.of("Java", "Spring Boot", "Kotlin", "Flutter", "Android", "React Native", "SQL", "MySQL", "PostgreSQL");
        Set<String> dawTechs = Set.of("React", "Vue.js", "Angular", "TypeScript", "JavaScript", "Node.js", "PHP", "REST API");
        if (technologies != null) {
            for (String t : technologies) {
                if (damTechs.contains(t) || dawTechs.contains(t)) score += 3;
            }
        }

        // -points for senior signals
        if (!isJuniorFriendly(title, description)) score -= 40;

        return Math.max(0, Math.min(100, score));
    }

    public Set<String> extractStudyLevels(String title, String description, Set<String> technologies) {
        Set<String> studies = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String combined = ((title != null ? title : "") + " " + (description != null ? description : "")).toLowerCase();

        // DAM check
        if ((technologies != null && technologies.stream().anyMatch(t -> List.of("Kotlin", "Flutter", "Dart", "Swift", "SwiftUI", "React Native", "Android", "iOS").contains(t))) ||
            combined.contains("dam") || combined.contains("multiplataforma") || combined.contains("mobile") || combined.contains("movil") || combined.contains("android") || combined.contains("ios")) {
            studies.add("DAM");
        }

        // DAW check
        if ((technologies != null && technologies.stream().anyMatch(t -> List.of("React", "Vue.js", "Angular", "Node.js", "TypeScript", "JavaScript", "PHP", "Spring Boot", "Django", "FastAPI", "REST API", "GraphQL").contains(t))) ||
            combined.contains("daw") || combined.contains("frontend") || combined.contains("fullstack") || combined.contains("web developer") || combined.contains("desarrollo web")) {
            studies.add("DAW");
        }

        // ASIR check
        if ((technologies != null && technologies.stream().anyMatch(t -> List.of("Linux", "Redes/Networking", "Sysadmin", "Bash/Shell", "PowerShell", "Ciberseguridad", "Nginx", "Apache").contains(t))) ||
            combined.contains("asir") || combined.contains("sistemas") || combined.contains("administrador de sistemas") || combined.contains("redes")) {
            studies.add("ASIR");
        }

        // SMR check
        if (combined.contains("smr") || combined.contains("soporte") || combined.contains("helpdesk") || combined.contains("tecnico microinformatico") || combined.contains("mantenimiento informatico")) {
            studies.add("SMR");
        }

        // DEVOPS check
        if ((technologies != null && technologies.stream().anyMatch(t -> List.of("Docker", "Kubernetes", "DevOps", "CI/CD", "Terraform", "Ansible", "AWS", "Azure", "GCP").contains(t))) ||
            combined.contains("devops") || combined.contains("cloud engineer") || combined.contains("sre") || combined.contains("infraestructura cloud")) {
            studies.add("DEVOPS");
        }

        // Grado / Ingeniería Informática check
        if (combined.contains("grado") || combined.contains("ingenieria") || combined.contains("computer science") || combined.contains("ingeniero") || combined.contains("software engineer") || combined.contains("arquitecto")) {
            studies.add("GRADO_INFORMATICA");
            studies.add("INGENIERIA");
        }

        // Only assign baseline IT profiles if the offer is actually a tech job
        if (studies.isEmpty() && isTechJob(title, description, null, technologies)) {
            studies.add("DAM");
            studies.add("DAW");
            studies.add("ASIR");
            studies.add("GRADO_INFORMATICA");
        }

        return studies;
    }

    public JobModality inferModality(Boolean isRemote, String location, String text) {
        String combined = (location != null ? location : "") + " " + (text != null ? text : "");
        String norm = SpanishGeographyService.removeAccents(combined.toLowerCase());

        if (norm.contains("hibrido") || norm.contains("hybrid") || norm.contains("semi-presencial")) {
            return JobModality.HIBRIDO;
        }
        if (Boolean.TRUE.equals(isRemote) || norm.contains("100% remoto") || norm.contains("remote") || norm.contains("remoto") || norm.contains("teletrabajo") || norm.contains("worldwide")) {
            return JobModality.REMOTO_100;
        }
        if (norm.contains("presencial") || norm.contains("on-site") || norm.contains("onsite")) {
            return JobModality.PRESENCIAL;
        }
        return Boolean.TRUE.equals(isRemote) ? JobModality.REMOTO_100 : JobModality.HIBRIDO;
    }

    public String cleanHtmlDescription(String rawHtml) {
        String fullClean = cleanFullDescription(rawHtml);
        if (fullClean.length() > 350) {
            return fullClean.substring(0, 347) + "...";
        }
        return fullClean;
    }

    public String cleanFullDescription(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return "";

        String text = rawHtml;

        // Pass 1: Replace HTML block breakers with newlines
        text = text.replaceAll("(?i)<br\\s*/?>", "\n")
                   .replaceAll("(?i)</p>", "\n\n")
                   .replaceAll("(?i)</li>", "\n")
                   .replaceAll("(?i)</div>", "\n")
                   .replaceAll("(?i)</tr>", "\n")
                   .replaceAll("(?i)<li>", " • ");

        // Pass 2: Decode common HTML entities (including double-escaped ones)
        text = decodeHtmlEntities(text);

        // Pass 3: Strip any remaining HTML tags
        text = text.replaceAll("<[^>]*>", " ");

        // Pass 4: In case there were double encoded tags (&lt;p&gt;)
        text = decodeHtmlEntities(text);
        text = text.replaceAll("<[^>]*>", " ");

        // Pass 5: Clean extra whitespace while preserving natural paragraph breaks
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.replaceAll("[ \\t\\r\\f]+", " ").trim();
            if (!trimmed.isEmpty()) {
                sb.append(trimmed).append("\n\n");
            }
        }

        return sb.toString().trim();
    }

    private String decodeHtmlEntities(String input) {
        if (input == null) return "";
        return input
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("&rsquo;", "'")
                .replaceAll("&lsquo;", "'")
                .replaceAll("&rdquo;", "\"")
                .replaceAll("&ldquo;", "\"")
                .replaceAll("&bull;", "•")
                .replaceAll("&middot;", "•")
                .replaceAll("&ndash;", "-")
                .replaceAll("&mdash;", "—")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&trade;", "™")
                .replaceAll("&copy;", "©")
                .replaceAll("&reg;", "®");
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
