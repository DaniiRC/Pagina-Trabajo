package com.jobaggregator.personal.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SpanishGeographyService {

    public static final Map<String, List<String>> COMMUNITIES_AND_PROVINCES = new LinkedHashMap<>();
    private static final Map<String, String> PROVINCE_TO_COMMUNITY = new HashMap<>();

    static {
        addCommunity("Andalucía", List.of("Almería", "Cádiz", "Córdoba", "Granada", "Huelva", "Jaén", "Málaga", "Sevilla"));
        addCommunity("Aragón", List.of("Huesca", "Teruel", "Zaragoza"));
        addCommunity("Asturias", List.of("Asturias", "Gijón", "Oviedo", "Avilés"));
        addCommunity("Islas Baleares", List.of("Baleares", "Palma de Mallorca", "Mallorca", "Ibiza", "Menorca"));
        addCommunity("Canarias", List.of("Las Palmas", "Santa Cruz de Tenerife", "Tenerife", "Gran Canaria"));
        addCommunity("Cantabria", List.of("Cantabria", "Santander"));
        addCommunity("Castilla-La Mancha", List.of("Albacete", "Ciudad Real", "Cuenca", "Guadalajara", "Toledo"));
        addCommunity("Castilla y León", List.of("Ávila", "Burgos", "León", "Palencia", "Salamanca", "Segovia", "Soria", "Valladolid", "Zamora"));
        addCommunity("Cataluña", List.of("Barcelona", "Girona", "Lleida", "Tarragona"));
        addCommunity("Comunidad Valenciana", List.of("Alicante", "Castellón", "Valencia"));
        addCommunity("Extremadura", List.of("Badajoz", "Cáceres", "Mérida"));
        addCommunity("Galicia", List.of("A Coruña", "La Coruña", "Lugo", "Ourense", "Pontevedra", "Vigo", "Santiago de Compostela"));
        addCommunity("Comunidad de Madrid", List.of("Madrid"));
        addCommunity("Región de Murcia", List.of("Murcia", "Cartagena"));
        addCommunity("Comunidad Foral de Navarra", List.of("Navarra", "Pamplona"));
        addCommunity("País Vasco", List.of("Álava", "Gipuzkoa", "Bizkaia", "Bilbao", "San Sebastián", "Vitoria", "Vitoria-Gasteiz", "Donostia"));
        addCommunity("La Rioja", List.of("La Rioja", "Logroño"));
        addCommunity("Ceuta", List.of("Ceuta"));
        addCommunity("Melilla", List.of("Melilla"));
    }

    private static void addCommunity(String community, List<String> provinces) {
        COMMUNITIES_AND_PROVINCES.put(community, provinces);
        for (String p : provinces) {
            PROVINCE_TO_COMMUNITY.put(removeAccents(p.toLowerCase()), community);
        }
    }

    public Map<String, List<String>> getSpanishGeographyTree() {
        return Collections.unmodifiableMap(COMMUNITIES_AND_PROVINCES);
    }

    public String findCommunityByProvince(String provinceName) {
        if (provinceName == null || provinceName.isBlank()) return null;
        String normalized = removeAccents(provinceName.trim().toLowerCase());

        for (Map.Entry<String, String> entry : PROVINCE_TO_COMMUNITY.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Infiere de forma estricta y jerárquica la geografía de una oferta.
     * Prioridad:
     * 1. Ubicación declarada (rawLocation)
     * 2. Título de la oferta
     * 3. Patrones explícitos de ubicación en descripción
     */
    public GeoResult inferGeography(String rawLocation, String jobTitle, String jobDescription, Boolean isRemote) {
        String locNorm = rawLocation != null ? removeAccents(rawLocation.toLowerCase().trim()) : "";

        // 1. Check if location explicitly indicates other European or global countries/cities
        if (locNorm.contains("germany") || locNorm.contains("alemania") || locNorm.contains("deutschland") ||
            locNorm.contains("berlin") || locNorm.contains("munich") || locNorm.contains("munchen") ||
            locNorm.contains("hamburg") || locNorm.contains("frankfurt") || locNorm.contains("cologne") ||
            locNorm.contains("koln") || locNorm.contains("stuttgart") || locNorm.contains("dusseldorf")) {
            return new GeoResult("Europa", "Alemania", null, rawLocation != null ? rawLocation : "Alemania");
        }
        if (locNorm.contains("uk") || locNorm.contains("united kingdom") || locNorm.contains("reino unido") ||
            locNorm.contains("london") || locNorm.contains("londres") || locNorm.contains("manchester") ||
            locNorm.contains("birmingham") || locNorm.contains("bristol") || locNorm.contains("edinburgh") ||
            locNorm.contains("glasgow") || locNorm.contains("cambridge") || locNorm.contains("oxford") ||
            locNorm.contains("bury st edmunds") || locNorm.contains("leeds") || locNorm.contains("liverpool")) {
            return new GeoResult("Europa", "Reino Unido", null, rawLocation != null ? rawLocation : "Reino Unido");
        }
        if (locNorm.contains("france") || locNorm.contains("francia") || locNorm.contains("paris") ||
            locNorm.contains("lyon") || locNorm.contains("marseille") || locNorm.contains("toulouse") || locNorm.contains("nantes")) {
            return new GeoResult("Europa", "Francia", null, rawLocation != null ? rawLocation : "Francia");
        }
        if (locNorm.contains("italy") || locNorm.contains("italia") || locNorm.contains("rome") ||
            locNorm.contains("roma") || locNorm.contains("milan") || locNorm.contains("milano") || locNorm.contains("turin")) {
            return new GeoResult("Europa", "Italia", null, rawLocation != null ? rawLocation : "Italia");
        }
        if (locNorm.contains("netherlands") || locNorm.contains("paises bajos") || locNorm.contains("holanda") ||
            locNorm.contains("amsterdam") || locNorm.contains("rotterdam") || locNorm.contains("utrecht") || locNorm.contains("hague")) {
            return new GeoResult("Europa", "Países Bajos", null, rawLocation != null ? rawLocation : "Países Bajos");
        }
        if (locNorm.contains("portugal") || locNorm.contains("lisbon") || locNorm.contains("lisboa") ||
            locNorm.contains("porto") || locNorm.contains("oporto") || locNorm.contains("braga")) {
            return new GeoResult("Europa", "Portugal", null, rawLocation != null ? rawLocation : "Portugal");
        }
        if (locNorm.contains("poland") || locNorm.contains("polonia") || locNorm.contains("warsaw") ||
            locNorm.contains("warszawa") || locNorm.contains("krakow") || locNorm.contains("wroclaw")) {
            return new GeoResult("Europa", "Polonia", null, rawLocation != null ? rawLocation : "Polonia");
        }
        if (locNorm.contains("switzerland") || locNorm.contains("suiza") || locNorm.contains("zurich") ||
            locNorm.contains("geneva") || locNorm.contains("ginebra") || locNorm.contains("basel") || locNorm.contains("bern")) {
            return new GeoResult("Europa", "Suiza", null, rawLocation != null ? rawLocation : "Suiza");
        }
        if (locNorm.contains("austria") || locNorm.contains("vienna") || locNorm.contains("wien")) {
            return new GeoResult("Europa", "Austria", null, rawLocation != null ? rawLocation : "Austria");
        }
        if (locNorm.contains("belgium") || locNorm.contains("belgica") || locNorm.contains("brussels") || locNorm.contains("bruselas")) {
            return new GeoResult("Europa", "Bélgica", null, rawLocation != null ? rawLocation : "Bélgica");
        }
        if (locNorm.contains("ireland") || locNorm.contains("irlanda") || locNorm.contains("dublin")) {
            return new GeoResult("Europa", "Irlanda", null, rawLocation != null ? rawLocation : "Irlanda");
        }
        if (locNorm.contains("united states") || locNorm.contains("usa") || locNorm.contains("estados unidos") ||
            locNorm.contains("san francisco") || locNorm.contains("new york") || locNorm.contains("austin") ||
            locNorm.contains("seattle") || locNorm.contains("los angeles") || locNorm.contains("us only")) {
            return new GeoResult("América del Norte", "Estados Unidos", null, rawLocation != null ? rawLocation : "Estados Unidos");
        }
        if (locNorm.contains("canada") || locNorm.contains("toronto") || locNorm.contains("vancouver") || locNorm.contains("montreal")) {
            return new GeoResult("América del Norte", "Canadá", null, rawLocation != null ? rawLocation : "Canadá");
        }

        // 2. Check Spanish provinces in rawLocation
        for (Map.Entry<String, List<String>> entry : COMMUNITIES_AND_PROVINCES.entrySet()) {
            String community = entry.getKey();
            for (String prov : entry.getValue()) {
                String provNorm = removeAccents(prov.toLowerCase());
                Pattern provPattern = Pattern.compile("\\b" + Pattern.quote(provNorm) + "\\b", Pattern.CASE_INSENSITIVE);

                if (provPattern.matcher(locNorm).find()) {
                    return new GeoResult("Europa", "España", community, prov);
                }
            }
        }

        // 3. Check if rawLocation explicitly mentions Spain
        if (locNorm.contains("spain") || locNorm.contains("espana")) {
            return new GeoResult("Europa", "España", null, locNorm.isEmpty() ? "España" : rawLocation);
        }

        // 4. Check if rawLocation is generic Europe/EMEA
        if (locNorm.contains("europe") || locNorm.contains("europa") || locNorm.contains("emea")) {
            return new GeoResult("Europa", "Europa", null, "Europa");
        }

        // 5. Check if rawLocation is generic Worldwide/Remote
        if (Boolean.TRUE.equals(isRemote) || locNorm.contains("worldwide") || locNorm.contains("remote") || locNorm.contains("anywhere") || locNorm.contains("global")) {
            return new GeoResult("Global", "Worldwide", null, "100% Remoto");
        }

        // 6. Explicit Spain keywords in description
        if (jobDescription != null) {
            String descNorm = removeAccents(jobDescription.toLowerCase());
            if (descNorm.contains("ubicacion: espana") || descNorm.contains("location: spain") ||
                descNorm.contains("based in spain") || descNorm.contains("residencia en espana") ||
                descNorm.contains("oficinas en madrid") || descNorm.contains("oficinas en barcelona")) {
                return new GeoResult("Europa", "España", null, "España");
            }
        }

        return new GeoResult("Global", "Internacional", null, rawLocation != null && !rawLocation.isBlank() ? rawLocation : "No especificado");
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
