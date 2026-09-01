package com.jobaggregator.personal.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SpanishGeographyService {

    public static final Map<String, List<String>> COMMUNITIES_AND_PROVINCES = new LinkedHashMap<>();
    private static final Map<String, String> PROVINCE_TO_COMMUNITY = new HashMap<>();

    static {
        addCommunity("Andalucía", List.of("Almería", "Cádiz", "Córdoba", "Granada", "Huelva", "Jaén", "Málaga", "Sevilla"));
        addCommunity("Aragón", List.of("Huesca", "Teruel", "Zaragoza"));
        addCommunity("Asturias", List.of("Asturias"));
        addCommunity("Islas Baleares", List.of("Baleares", "Palma de Mallorca", "Ibiza", "Menorca"));
        addCommunity("Canarias", List.of("Las Palmas", "Santa Cruz de Tenerife", "Tenerife", "Gran Canaria"));
        addCommunity("Cantabria", List.of("Cantabria", "Santander"));
        addCommunity("Castilla-La Mancha", List.of("Albacete", "Ciudad Real", "Cuenca", "Guadalajara", "Toledo"));
        addCommunity("Castilla y León", List.of("Ávila", "Burgos", "León", "Palencia", "Salamanca", "Segovia", "Soria", "Valladolid", "Zamora"));
        addCommunity("Cataluña", List.of("Barcelona", "Girona", "Lleida", "Tarragona"));
        addCommunity("Comunidad Valenciana", List.of("Alicante", "Castellón", "Valencia"));
        addCommunity("Extremadura", List.of("Badajoz", "Cáceres"));
        addCommunity("Galicia", List.of("A Coruña", "Lugo", "Ourense", "Pontevedra", "Vigo"));
        addCommunity("Comunidad de Madrid", List.of("Madrid"));
        addCommunity("Región de Murcia", List.of("Murcia", "Cartagena"));
        addCommunity("Comunidad Foral de Navarra", List.of("Navarra", "Pamplona"));
        addCommunity("País Vasco", List.of("Álava", "Gipuzkoa", "Bizkaia", "Bilbao", "San Sebastián", "Vitoria-Gasteiz"));
        addCommunity("La Rioja", List.of("La Rioja", "Logroño"));
        addCommunity("Ceuta", List.of("Ceuta"));
        addCommunity("Melilla", List.of("Melilla"));
    }

    private static void addCommunity(String community, List<String> provinces) {
        COMMUNITIES_AND_PROVINCES.put(community, provinces);
        for (String p : provinces) {
            PROVINCE_TO_COMMUNITY.put(p.toLowerCase(), community);
        }
    }

    public Map<String, List<String>> getSpanishGeographyTree() {
        return Collections.unmodifiableMap(COMMUNITIES_AND_PROVINCES);
    }

    public String findCommunityByProvince(String provinceName) {
        if (provinceName == null || provinceName.isBlank()) return null;
        String normalized = removeAccents(provinceName.trim().toLowerCase());

        for (Map.Entry<String, String> entry : PROVINCE_TO_COMMUNITY.entrySet()) {
            String entryNorm = removeAccents(entry.getKey().toLowerCase());
            if (normalized.contains(entryNorm) || entryNorm.contains(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public GeoResult inferGeography(String rawLocation, String jobTitle, String jobDescription, Boolean isRemote) {
        String combined = (rawLocation != null ? rawLocation : "") + " " +
                          (jobTitle != null ? jobTitle : "") + " " +
                          (jobDescription != null ? jobDescription : "");
        String combinedNorm = removeAccents(combined.toLowerCase());

        // Check if matching a specific Spanish province
        for (Map.Entry<String, List<String>> entry : COMMUNITIES_AND_PROVINCES.entrySet()) {
            String community = entry.getKey();
            for (String prov : entry.getValue()) {
                String provNorm = removeAccents(prov.toLowerCase());
                if (combinedNorm.matches(".*\\b" + provNorm + "\\b.*") || (rawLocation != null && removeAccents(rawLocation.toLowerCase()).contains(provNorm))) {
                    return new GeoResult("Europa", "España", community, prov);
                }
            }
        }

        // Check if generic Spain
        if (combinedNorm.contains("spain") || combinedNorm.contains("espana") || (rawLocation != null && removeAccents(rawLocation.toLowerCase()).contains("spain"))) {
            return new GeoResult("Europa", "España", null, null);
        }

        // Check if generic Europe
        if (combinedNorm.contains("germany") || combinedNorm.contains("alemania") || combinedNorm.contains("berlin") || combinedNorm.contains("munich")) {
            return new GeoResult("Europa", "Alemania", null, "Berlín / Alemania");
        }
        if (combinedNorm.contains("uk") || combinedNorm.contains("reino unido") || combinedNorm.contains("london") || combinedNorm.contains("londres")) {
            return new GeoResult("Europa", "Reino Unido", null, "Londres / UK");
        }
        if (combinedNorm.contains("europe") || combinedNorm.contains("europa") || combinedNorm.contains("emea")) {
            return new GeoResult("Europa", "Europa", null, null);
        }

        if (Boolean.TRUE.equals(isRemote) || combinedNorm.contains("worldwide") || combinedNorm.contains("remote") || combinedNorm.contains("teletrabajo")) {
            return new GeoResult("Global", "Worldwide", null, "Remoto Global");
        }

        return new GeoResult("Global", "Internacional", null, rawLocation);
    }

    public static String removeAccents(String text) {
        if (text == null) return "";
        return text
                .replaceAll("[áàäâ]", "a")
                .replaceAll("[éèëê]", "e")
                .replaceAll("[íìïî]", "i")
                .replaceAll("[óòöô]", "o")
                .replaceAll("[úùüû]", "u")
                .replaceAll("ñ", "n");
    }

    public record GeoResult(String continent, String country, String autonomousCommunity, String provinceOrCity) {}
}
