package com.bloodlink.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeoIPService {
    
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public record GeoLocation(double lat, double lon, String city, String region) {}

    /**
     * Tries to detect approximate location based on IP address using a free API (ip-api.com).
     * Does not require an API key and allows 45 requests per minute for non-commercial use.
     */
    public static Optional<GeoLocation> detectLocation() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/?fields=status,message,lat,lon,city,regionName"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                if (body.contains("\"status\":\"success\"")) {
                    double lat = extractDouble(body, "\"lat\":");
                    double lon = extractDouble(body, "\"lon\":");
                    String city = extractString(body, "\"city\":");
                    String regionName = extractString(body, "\"regionName\":");
                    
                    return Optional.of(new GeoLocation(lat, lon, city, regionName));
                }
            }
        } catch (Exception e) {
            System.err.println("GeoIP detection failed: " + e.getMessage());
        }
        return Optional.empty();
    }
    
    private static double extractDouble(String json, String key) {
        int index = json.indexOf(key);
        if (index == -1) return 0.0;
        int start = index + key.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) return 0.0;
        try {
            return Double.parseDouble(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    private static String extractString(String json, String key) {
        int index = json.indexOf(key);
        if (index == -1) return "";
        int start = json.indexOf("\"", index + key.length());
        if (start == -1) return "";
        int end = json.indexOf("\"", start + 1);
        if (end == -1) return "";
        return json.substring(start + 1, end);
    }
}
