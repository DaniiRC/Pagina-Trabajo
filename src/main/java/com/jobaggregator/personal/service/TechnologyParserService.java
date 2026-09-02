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

    public Set<String> extractStudyLevels(String title, String description, Set<String> technologies) {
        Set<String> studies = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String combined = ((title != null ? title : "") + " " + (description != null ? description : "")).toLowerCase();

        // DAM check
        if (technologies.stream().anyMatch(t -> List.of("Kotlin", "Flutter", "Dart", "Swift", "SwiftUI", "React Native", "Android", "iOS").contains(t)) ||
            combined.contains("dam") || combined.contains("multiplataforma") || combined.contains("mobile") || combined.contains("movil")) {
            studies.add("DAM");
        }

        // DAW check
        if (technologies.stream().anyMatch(t -> List.of("React", "Vue.js", "Angular", "Node.js", "TypeScript", "JavaScript", "PHP", "Spring Boot").contains(t)) ||
            combined.contains("daw") || combined.contains("frontend") || combined.contains("fullstack") || combined.contains("web")) {
            studies.add("DAW");
        }

        // ASIR check
        if (technologies.stream().anyMatch(t -> List.of("Linux", "Redes/Networking", "Sysadmin", "Bash/Shell", "PowerShell", "Ciberseguridad", "Nginx", "Apache").contains(t)) ||
            combined.contains("asir") || combined.contains("sistemas") || combined.contains("administrador de sistemas") || combined.contains("redes")) {
            studies.add("ASIR");
        }

        // SMR check
        if (combined.contains("smr") || combined.contains("soporte") || combined.contains("helpdesk") || combined.contains("tecnico microinformatico") || combined.contains("mantenimiento")) {
            studies.add("SMR");
        }

        // DEVOPS check
        if (technologies.stream().anyMatch(t -> List.of("Docker", "Kubernetes", "DevOps", "CI/CD", "Terraform", "Ansible", "AWS", "Azure", "GCP").contains(t)) ||
            combined.contains("devops") || combined.contains("cloud") || combined.contains("sre") || combined.contains("infraestructura")) {
            studies.add("DEVOPS");
        }

        // Grado / Ingeniería Informática check
        if (combined.contains("grado") || combined.contains("ingenieria") || combined.contains("computer science") || combined.contains("ingeniero") || combined.contains("software engineer") || combined.contains("arquitecto")) {
            studies.add("GRADO_INFORMATICA");
            studies.add("INGENIERIA");
        }

        // If no explicit study found, assign baseline IT profiles
        if (studies.isEmpty()) {
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
