package com.bloodlink.service;

import com.bloodlink.util.AppConfig;
import com.bloodlink.model.BloodGroup;
import com.bloodlink.model.NidExtraction;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private final String apiKey;
    private final String model;

    public GeminiOcrService() {
        this.apiKey = AppConfig.get("gemini.api.key");
        // The model id used to be hard-coded. Google retires and renames these on
        // their own schedule, and a wrong id fails at request time with a 404 that
        // looks exactly like a bad API key -- so it belongs in configuration, where
        // it can be corrected without a rebuild. The default preserves the previous
        // behaviour; see application.properties.
        this.model = AppConfig.get("gemini.model");
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public NidExtraction extract(List<File> imageFiles) {
        if (!isConfigured()) {
            // Previously this returned a *successful* extraction for a fictional
            // "Demo User" -- including a blood group of O_POSITIVE -- whenever no API
            // key was set. That is the one thing an OCR path in a blood-donation app
            // must never do: the review dialog presents a successful result as
            // "detected from your card", so a user could accept a fabricated blood
            // group and register with it. NidExtraction's own Javadoc says this is
            // "never proof of blood group". Fail honestly instead; NidScanDialog
            // already falls back to local Tesseract, and manual entry always works.
            return NidExtraction.failure("AI-assisted scanning is not configured on this machine "
                    + "(no gemini.api.key). You can fill in your details manually below.");
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
                    .uri(URI.create(String.format(API_URL_TEMPLATE, model, apiKey)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                // Truncated: an error body can be large, and is third-party content
                // being written into this app's log.
                LOGGER.warning("Gemini API error " + response.statusCode() + " (model \"" + model + "\"): "
                        + abbreviate(response.body(), 400));
                return NidExtraction.failure(errorMessageFrom(response.body(), response.statusCode()));
            }

            return parseGeminiResponse(response.body());

        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Failed to call Gemini API", e);
            return NidExtraction.failure("Network error occurred during AI processing.");
        }
    }

    /** Surfaces Google's own explanation when it gives one, since "check your key" is rarely the real cause. */
    private String errorMessageFrom(String body, int statusCode) {
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            String message = error == null ? null : error.optString("message", null);
            if (message != null && !message.isBlank()) return "Google API error: " + message;
        } catch (JSONException ignored) {
            // Non-JSON error body (a proxy or gateway page); fall through.
        }
        if (statusCode == 404) {
            return "The configured AI model \"" + model + "\" was not found (HTTP 404). "
                    + "Check the gemini.model setting.";
        }
        return "AI processing failed (HTTP " + statusCode + "). Check your API key and quota.";
    }

    private String getMimeType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }

    /**
     * Pulls the model's text out of Google's JSON envelope.
     * <p>
     * This used to scan the raw response with {@code indexOf} and unescape it with
     * chained {@code replace} calls. That was wrong in two ways: the scan for the
     * closing quote mis-handled a literal backslash before it, and the unescaping
     * rewrote {@code \\n} (an escaped backslash, then 'n') into a newline. Now that
     * the project has a JSON parser on the classpath for the badge manifest, this
     * reads the envelope properly and both classes of bug disappear.
     */
    private NidExtraction parseGeminiResponse(String jsonResponse) {
        String rawText;
        try {
            JSONArray candidates = new JSONObject(jsonResponse).optJSONArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                // A 200 with no candidate usually means the prompt or the image was
                // blocked; say so rather than reporting a parse failure.
                return NidExtraction.failure("The AI returned no result for these images. "
                        + "Try a clearer photo, or fill in your details manually.");
            }
            JSONArray parts = candidates.getJSONObject(0).optJSONObject("content") == null
                    ? null : candidates.getJSONObject(0).getJSONObject("content").optJSONArray("parts");
            if (parts == null || parts.isEmpty()) {
                return NidExtraction.failure("Could not read the AI response.");
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                text.append(parts.getJSONObject(i).optString("text", ""));
            }
            rawText = text.toString();
        } catch (JSONException e) {
            LOGGER.log(Level.WARNING, "Unparseable Gemini response", e);
            return NidExtraction.failure("Could not read the AI response.");
        }

        // Strip code fences if the model ignored the "no Markdown" instruction.
        rawText = rawText.replace("```text", "").replace("```", "").trim();
        if (rawText.isBlank()) return NidExtraction.failure("The AI returned an empty result.");

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

    private static String abbreviate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "... (truncated)";
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
