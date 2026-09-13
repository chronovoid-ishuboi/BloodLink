package com.bloodlink.service;

import com.bloodlink.util.AppConfig;
import com.bloodlink.model.BloodGroup;
import com.bloodlink.model.NidExtraction;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GeminiOcrService implements OcrService {
    private static final Logger LOGGER = Logger.getLogger(GeminiOcrService.class.getName());
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=%s";
    private final String apiKey;

    public GeminiOcrService() {
        this.apiKey = AppConfig.get("gemini.api.key");
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public NidExtraction extract(List<File> imageFiles) {
        if (!isConfigured()) {
            // Mock fallback for student demo if no API key is provided
            return new NidExtraction(true, "Demo User", LocalDate.of(1995, 1, 1),
                    BloodGroup.O_POSITIVE, "Dhaka, Bangladesh", "1234567890", null);
        }
        if (imageFiles == null || imageFiles.isEmpty()) {
            return NidExtraction.failure("No image files were provided.");
        }

        try {
            StringBuilder partsBuilder = new StringBuilder();
            
            String prompt = "Act as an automated OCR engine. Extract the following text fields from the provided images: Name, Date of Birth (YYYY-MM-DD), " +
                    "Blood Group (often found marked in red, e.g. A_POSITIVE, O_NEGATIVE, B_POSITIVE, AB_NEGATIVE, etc), " +
                    "Address (translate any non-English address to English accurately), and ID number. " +
                    "Return the result STRICTLY in this exact line-by-line format with NO Markdown formatting or backticks:\\n" +
                    "NAME: <name>\\n" +
                    "DOB: <dob>\\n" +
                    "BLOOD_GROUP: <blood_group>\\n" +
                    "ADDRESS: <address>\\n" +
                    "NID: <nid>\\n" +
                    "If a field is missing, leave the value completely empty.";
                    
            partsBuilder.append("{\"text\": \"").append(prompt).append("\"}");

            for (File imageFile : imageFiles) {
                if (!imageFile.exists()) continue;
                byte[] fileBytes = Files.readAllBytes(imageFile.toPath());
                String base64Image = Base64.getEncoder().encodeToString(fileBytes);
                String mimeType = getMimeType(imageFile);
                
                partsBuilder.append(",");
                partsBuilder.append(String.format("{\"inline_data\": {\"mime_type\": \"%s\", \"data\": \"%s\"}}", mimeType, base64Image));
            }

            String jsonPayload = String.format("""
                    {
                      "contents": [{
                        "parts": [
                          %s
                        ]
                      }],
                      "generationConfig": {"temperature": 0.0}
                    }
                    """, partsBuilder.toString());

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL_TEMPLATE, apiKey)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.warning("Gemini API error: " + response.statusCode() + " - " + response.body());
                String errorMsg = "AI processing failed. Check your API key and quota.";
                try {
                    // Try to extract the actual error message from Google's response
                    int msgStart = response.body().indexOf("\"message\": \"");
                    if (msgStart != -1) {
                        msgStart += 12;
                        int msgEnd = response.body().indexOf("\"", msgStart);
                        if (msgEnd != -1) {
                            errorMsg = "Google API Error: " + response.body().substring(msgStart, msgEnd);
                        }
                    }
                } catch (Exception ignored) {}
                return NidExtraction.failure(errorMsg);
            }

            return parseGeminiResponse(response.body());

        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Failed to call Gemini API", e);
            return NidExtraction.failure("Network error occurred during AI processing.");
        }
    }

    private String getMimeType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }

    private NidExtraction parseGeminiResponse(String jsonResponse) {
        // Find the "text" field in the Gemini JSON response more robustly
        int textStart = jsonResponse.indexOf("\"text\"");
        if (textStart == -1) {
            return NidExtraction.failure("Could not parse AI response.");
        }
        textStart = jsonResponse.indexOf("\"", textStart + 6);
        if (textStart == -1) return NidExtraction.failure("Could not parse AI response.");
        textStart++;
        
        int textEnd = jsonResponse.indexOf("\"", textStart);
        // Handle escaped quotes inside the text field
        while (textEnd != -1 && jsonResponse.charAt(textEnd - 1) == '\\') {
            textEnd = jsonResponse.indexOf("\"", textEnd + 1);
        }
        if (textEnd == -1) {
            return NidExtraction.failure("Could not parse AI response.");
        }
        
        // Extract and unescape the text
        String rawText = jsonResponse.substring(textStart, textEnd)
                                     .replace("\\n", "\n")
                                     .replace("\\\"", "\"");

        // Remove markdown backticks if Gemini ignored instructions
        rawText = rawText.replace("```text", "").replace("```", "").trim();

        String name = null;
        LocalDate dob = null;
        BloodGroup bloodGroup = null;
        String address = null;
        String nid = null;

        for (String line : rawText.split("\n")) {
            line = line.trim();
            if (line.startsWith("NAME:")) name = extractValue(line);
            else if (line.startsWith("DOB:")) dob = parseDate(extractValue(line));
            else if (line.startsWith("BLOOD_GROUP:")) bloodGroup = parseBloodGroup(extractValue(line));
            else if (line.startsWith("ADDRESS:")) address = extractValue(line);
            else if (line.startsWith("NID:")) nid = extractValue(line);
        }

        if (name == null && dob == null && nid == null && bloodGroup == null && address == null) {
            return NidExtraction.failure("AI could not confidently detect any fields on this card.");
        }

        return new NidExtraction(true, name, dob, bloodGroup, address, nid, null);
    }

    private String extractValue(String line) {
        int colonIdx = line.indexOf(':');
        if (colonIdx == -1 || colonIdx == line.length() - 1) return null;
        String val = line.substring(colonIdx + 1).trim();
        return val.isEmpty() || val.equalsIgnoreCase("null") || val.equalsIgnoreCase("none") ? null : val;
    }

    private LocalDate parseDate(String val) {
        if (val == null) return null;
        try { return LocalDate.parse(val); } catch (Exception e) { return null; }
    }

    private BloodGroup parseBloodGroup(String val) {
        if (val == null) return null;
        try { return BloodGroup.valueOf(val.toUpperCase()); } catch (Exception e) { return null; }
    }

}
