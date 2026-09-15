package com.bloodlink.util;

import com.bloodlink.model.BadgeTier;
import javafx.scene.paint.Color;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads the hand-editable badge manifest at
 * {@code /com/bloodlink/badges/badges.json} once, at first use, and exposes the
 * label / icon geometry / colour for each {@link BadgeTier}.
 * <p>
 * The point of this class is that badge <em>presentation</em> is data, not code:
 * a user can restyle every tier, or drop in their own icon files, by editing
 * that folder in their IDE without touching Java. See the README next to the
 * manifest for the file format. What stays in code is {@link BadgeTier} itself
 * — the donation thresholds are a rule about verified donations, not styling,
 * so they are deliberately not configurable here.
 * <p>
 * <b>This never throws.</b> A missing, malformed, or partially broken manifest
 * degrades to the built-in defaults (which match the shipped manifest, so the
 * app looks complete either way) and logs a warning. Failure is per-tier: one
 * bad entry does not discard the four good ones. A broken badge file is not a
 * reason for a blood-donation app to fail to start.
 * <p>
 * Parallel in spirit to {@link LogoManager}: a user-replaceable visual asset
 * with a safe built-in default.
 */
public final class BadgeRegistry {
    private static final Logger LOGGER = Logger.getLogger(BadgeRegistry.class.getName());
    private static final String MANIFEST = "/com/bloodlink/badges/badges.json";
    private static final String ASSET_DIR = "/com/bloodlink/badges/";

    /**
     * One tier's resolved presentation.
     *
     * @param label     text shown beside the icon
     * @param pathData  SVG path geometry for the icon, or {@code null} if the icon
     *                   file was missing/unreadable — callers render label-only
     * @param color     fill for the icon and tint for the label
     */
    public record BadgeStyle(String label, String pathData, Color color) { }

    /**
     * Built-in fallbacks. These intentionally duplicate the shipped
     * {@code badges.json} so that deleting the manifest changes nothing visible.
     */
    private static final Map<BadgeTier, String[]> DEFAULTS = new EnumMap<>(BadgeTier.class);
    static {
        DEFAULTS.put(BadgeTier.NONE, new String[]{"New Donor", "none.svg", "#8A9A94"});
        DEFAULTS.put(BadgeTier.BRONZE, new String[]{"Bronze Donor", "bronze.svg", "#B0703A"});
        DEFAULTS.put(BadgeTier.SILVER, new String[]{"Silver Donor", "silver.svg", "#8C9AA6"});
        DEFAULTS.put(BadgeTier.GOLD, new String[]{"Gold Donor", "gold.svg", "#C79A2E"});
        DEFAULTS.put(BadgeTier.PLATINUM, new String[]{"Platinum Donor", "platinum.svg", "#5C8AA0"});
    }

    private static volatile Map<BadgeTier, BadgeStyle> styles;

    private BadgeRegistry() { }

    public static BadgeStyle styleFor(BadgeTier tier) {
        if (tier == null) tier = BadgeTier.NONE;
        return load().get(tier);
    }

    public static String labelFor(BadgeTier tier) { return styleFor(tier).label(); }

    /** SVG path geometry for this tier's icon, or {@code null} when no icon could be loaded. */
    public static String iconFor(BadgeTier tier) { return styleFor(tier).pathData(); }

    public static Color colorFor(BadgeTier tier) { return styleFor(tier).color(); }

    /** Drops the cached manifest so the next call re-reads it. Used by tests. */
    static void reset() { styles = null; }

    private static Map<BadgeTier, BadgeStyle> load() {
        Map<BadgeTier, BadgeStyle> local = styles;
        if (local != null) return local;
        synchronized (BadgeRegistry.class) {
            if (styles == null) styles = read();
            return styles;
        }
    }

    private static Map<BadgeTier, BadgeStyle> read() {
        JSONObject manifest = readManifest();
        Map<BadgeTier, BadgeStyle> resolved = new EnumMap<>(BadgeTier.class);
        for (BadgeTier tier : BadgeTier.values()) {
            String[] fallback = DEFAULTS.get(tier);
            JSONObject entry = manifest == null ? null : manifest.optJSONObject(tier.name());
            if (manifest != null && entry == null) {
                LOGGER.warning("badges.json has no entry for " + tier.name() + "; using the built-in default.");
            }
            String label = optString(entry, "label", fallback[0]);
            String iconFile = optString(entry, "icon", fallback[1]);
            Color color = parseColor(optString(entry, "color", fallback[2]), fallback[2], tier);
            resolved.put(tier, new BadgeStyle(label, SvgShapeReader.pathDataFrom(ASSET_DIR + iconFile), color));
        }
        return resolved;
    }

    private static JSONObject readManifest() {
        try (InputStream in = BadgeRegistry.class.getResourceAsStream(MANIFEST)) {
            if (in == null) {
                LOGGER.warning("badges.json not found on the classpath; using built-in badge defaults.");
                return null;
            }
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Deliberately broad: a malformed manifest is a styling problem, and must
            // never prevent the app from starting.
            LOGGER.log(Level.WARNING, "badges.json could not be read; using built-in badge defaults.", e);
            return null;
        }
    }

    private static String optString(JSONObject entry, String key, String fallback) {
        if (entry == null) return fallback;
        String value = entry.optString(key, null);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static Color parseColor(String value, String fallback, BadgeTier tier) {
        try {
            return Color.web(value);
        } catch (RuntimeException e) {
            LOGGER.warning("badges.json has an unreadable color \"" + value + "\" for " + tier.name()
                    + "; using the built-in default " + fallback + ".");
            return Color.web(fallback);
        }
    }
}
