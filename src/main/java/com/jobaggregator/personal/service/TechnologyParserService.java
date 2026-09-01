package com.jobaggregator.personal.service;

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

        // 1. Process explicit tags provided by the API
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

        // 2. Scan title and description text with regex patterns
        String combinedText = (title != null ? title : "") + " " + (description != null ? description : "");
        for (Map.Entry<String, Pattern> entry : TECH_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(combinedText).find()) {
                detected.add(entry.getKey());
            }
        }

        return detected;
    }

    public String cleanHtmlDescription(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return "";
        // Remove HTML tags and convert special entities
        String text = rawHtml
                .replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();

        // Limit short description length for clean storage and display
        if (text.length() > 600) {
            return text.substring(0, 597) + "...";
        }
        return text;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
