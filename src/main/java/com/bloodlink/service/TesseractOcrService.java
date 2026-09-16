package com.bloodlink.service;

import com.bloodlink.model.NidExtraction;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs a real, local OCR pass (Tesseract, via the Tess4J JNA binding) over a
 * photographed Bangladesh NID card and heuristically pulls out name /
 * date-of-birth / NID number from the recognized text. Identity-registration
 * ASSISTANCE only -- see {@link com.bloodlink.model.NidExtraction}'s Javadoc
 * for the boundary this must never cross.
 * <p>
 * <b>Real operational requirement, not just a Maven dependency</b>: this needs
 * a local Tesseract OCR engine actually installed on the machine (e.g. the
 * UB-Mannheim build on Windows) with English trained data available, either
 * on the system PATH or pointed to via the {@code TESSDATA_PREFIX}
 * environment variable -- the same variable Tesseract itself conventionally
 * uses, so no BloodLink-specific configuration is invented here. If
 * Tesseract isn't installed, isn't found, or fails to read a given image,
 * every method here returns a graceful {@link NidExtraction#failure} rather
 * than throwing -- registration must keep working manually regardless.
 * <p>
 * <b>Honesty note carried over from this delivery's other native-dependent
 * piece (the push server)</b>: I could not compile or run this against a
 * real Tesseract installation in this environment to mechanically verify it
 * the way the rest of this delivery was cross-checked line by line. The
 * Tess4J API surface used here (Tesseract, TesseractException, doOCR(File),
 * setDatapath, setLanguage) has been stable across many releases, but this
 * file carries a different, weaker guarantee than everything else delivered
 * this session. Test it against a real card photo before relying on it.
 */
public final class TesseractOcrService implements OcrService {
    private static final Pattern NAME_PATTERN = Pattern.compile("(?i)name\\s*[:\\-]?\\s*([A-Za-z .'\\-]{3,60})");
    /**
     * Matches both the textual form a Bangladesh NID normally prints
     * ("14 Mar 1998") and the numeric forms that turn up on older or
     * re-issued cards ("14-03-1998", "14/03/1998").
     */
    private static final Pattern DOB_PATTERN = Pattern.compile(
            "(?i)date\\s*of\\s*birth\\s*[:\\-]?\\s*"
                    + "(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4})");
    private static final Pattern BLOOD_GROUP_PATTERN = Pattern.compile("(?i)blood\\s*group[^:]*[:\\-]?\\s*([ABO][A-Za-z+-]{0,2})");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(?i)address[^:]*[:\\-]?\\s*([^\\n]+)");
    private static final Pattern NID_PATTERN = Pattern.compile("(?i)(?:national\\s*id\\s*no|id\\s*no|nid\\s*no)\\s*[:\\-]?\\s*([0-9]{10,17})");
    /**
     * <b>Locale.ENGLISH is load-bearing, not decoration.</b> A Bangladesh NID
     * prints its month in English ("Mar"), but {@code ofPattern} without a
     * locale binds to the machine's default at class-load time. On a machine set
     * to Bengali -- i.e. a good share of this app's actual users -- the
     * formatter would expect Bengali month names and every date on every card
     * would silently fail to parse, leaving the date-of-birth field blank with
     * no error to explain why.
     */
    private static final DateTimeFormatter[] DOB_FORMATS = {
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ENGLISH)
    };

    @Override
    public NidExtraction extract(java.util.List<File> imageFiles) {
        if (imageFiles == null || imageFiles.isEmpty()) return NidExtraction.failure("No image files were provided.");
        try {
            Tesseract tesseract = new Tesseract();
            String tessdataPath = System.getenv("TESSDATA_PREFIX");
            if (tessdataPath != null && !tessdataPath.isBlank()) tesseract.setDatapath(tessdataPath);
            tesseract.setLanguage("eng");
            
            StringBuilder rawText = new StringBuilder();
            for (File imageFile : imageFiles) {
                if (imageFile.exists()) {
                    rawText.append(tesseract.doOCR(imageFile)).append("\n");
                }
            }
            return parse(rawText.toString());
        } catch (TesseractException e) {
            return NidExtraction.failure("The OCR engine could not read these images. Try a clearer, well-lit, unrotated photo.");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            // Expected on a machine without Tesseract installed -- not a bug, not logged as one.
            return NidExtraction.failure("Local OCR (Tesseract) is not installed on this machine. You can fill in your details manually below.");
        } catch (RuntimeException e) {
            // Tess4J talks to a native library through JNA, which can surface a
            // half-configured install as an unchecked exception rather than the two
            // errors above (a broken TESSDATA_PREFIX, a missing eng.traineddata, an
            // architecture mismatch). Registration must survive all of it: the whole
            // contract of this class is that OCR failure never blocks manual entry.
            return NidExtraction.failure("Local OCR could not run on this machine ("
                    + e.getClass().getSimpleName() + "). You can fill in your details manually below.");
        }
    }

    private NidExtraction parse(String rawText) {
        if (rawText == null || rawText.isBlank())
            return NidExtraction.failure("No readable text was found in the image.");
        String name = firstGroup(NAME_PATTERN, rawText);
        LocalDate dob = parseDate(firstGroup(DOB_PATTERN, rawText));
        
        String bloodGroupRaw = firstGroup(BLOOD_GROUP_PATTERN, rawText);
        com.bloodlink.model.BloodGroup bloodGroup = null;
        if (bloodGroupRaw != null) {
            String norm = bloodGroupRaw.toUpperCase().replaceAll("\\s+", "");
            if (norm.endsWith("+")) norm = norm.substring(0, norm.length()-1) + "_POSITIVE";
            else if (norm.endsWith("-")) norm = norm.substring(0, norm.length()-1) + "_NEGATIVE";
            try { bloodGroup = com.bloodlink.model.BloodGroup.valueOf(norm); } catch (Exception ignored) {}
        }
        
        String address = firstGroup(ADDRESS_PATTERN, rawText);
        String nid = firstGroup(NID_PATTERN, rawText);
        
        if (name == null && dob == null && nid == null && bloodGroup == null && address == null) {
            return NidExtraction.failure("Could not confidently detect any fields on this card. " +
                    "Try a clearer photo, or fill in your details manually.");
        }
        return new NidExtraction(true, name, dob, bloodGroup, address, nid, null);
    }

    private String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) return null;
        for (DateTimeFormatter format : DOB_FORMATS) {
            try { return LocalDate.parse(raw, format); } catch (RuntimeException ignored) { /* try next format */ }
        }
        return null;
    }

}
