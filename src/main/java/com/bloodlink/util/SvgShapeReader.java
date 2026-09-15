package com.bloodlink.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a small SVG file into the single path-data string that JavaFX's
 * {@code SVGPath} understands.
 * <p>
 * JavaFX has no SVG document loader — {@code SVGPath} takes path geometry and
 * nothing else. This reader bridges that gap just far enough to let a user drop
 * a hand-drawn or exported {@code .svg} into
 * {@code resources/com/bloodlink/badges/} and have it show up, which is the
 * whole promise of the badge asset system.
 * <p>
 * <b>Deliberately narrow.</b> It extracts geometry from {@code <path>},
 * {@code <circle>}, {@code <ellipse>}, {@code <rect>}, {@code <polygon>},
 * {@code <polyline>} and {@code <line>}, converting each to path data, and
 * ignores everything else — {@code transform}, per-shape fills and strokes,
 * gradients, text, nested documents. This is not a general SVG renderer and
 * does not pretend to be one; the badge README documents the same limits for
 * the user. Anything it cannot read comes back as {@code null} so the caller
 * can fall back rather than draw nonsense.
 * <p>
 * All extracted shapes are concatenated into one path, which the caller draws
 * with the even-odd fill rule so inner shapes knock holes through outer ones.
 */
final class SvgShapeReader {
    private static final Logger LOGGER = Logger.getLogger(SvgShapeReader.class.getName());

    /** Strips comments so a commented-out shape is not picked up as real geometry. */
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern ELEMENT = Pattern.compile("<\\s*(path|circle|ellipse|rect|polygon|polyline|line)\\b([^>]*)>",
            Pattern.CASE_INSENSITIVE);

    private SvgShapeReader() { }

    /**
     * Reads the classpath resource at {@code resourcePath} and returns SVG path
     * data, or {@code null} if it is missing, unreadable, or contains no shape
     * this reader understands.
     * <p>
     * A file whose name does not end in {@code .svg} is treated as raw path data
     * and returned verbatim, which is the documented "just put the d= string in a
     * .txt file" option.
     */
    static String pathDataFrom(String resourcePath) {
        String raw = readResource(resourcePath);
        if (raw == null || raw.isBlank()) return null;
        if (!resourcePath.toLowerCase(Locale.ROOT).endsWith(".svg")) return raw.trim();

        String svg = COMMENT.matcher(raw).replaceAll("");
        List<String> parts = new ArrayList<>();
        Matcher matcher = ELEMENT.matcher(svg);
        while (matcher.find()) {
            String converted = convert(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2));
            if (converted != null && !converted.isBlank()) parts.add(converted.trim());
        }
        if (parts.isEmpty()) {
            LOGGER.warning("No usable shapes found in " + resourcePath
                    + " (this reader handles path/circle/ellipse/rect/polygon/polyline/line only).");
            return null;
        }
        return String.join(" ", parts);
    }

    private static String readResource(String resourcePath) {
        try (InputStream in = SvgShapeReader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.warning("Badge icon not found on the classpath: " + resourcePath);
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not read badge icon " + resourcePath, e);
            return null;
        }
    }

    private static String convert(String tag, String attributes) {
        return switch (tag) {
            case "path" -> attribute(attributes, "d");
            case "circle" -> {
                double r = number(attributes, "r", 0);
                yield r <= 0 ? null : ellipse(number(attributes, "cx", 0), number(attributes, "cy", 0), r, r);
            }
            case "ellipse" -> {
                double rx = number(attributes, "rx", 0);
                double ry = number(attributes, "ry", 0);
                yield (rx <= 0 || ry <= 0) ? null : ellipse(number(attributes, "cx", 0), number(attributes, "cy", 0), rx, ry);
            }
            case "rect" -> rect(attributes);
            case "polygon" -> points(attributes, true);
            case "polyline" -> points(attributes, false);
            case "line" -> "M" + number(attributes, "x1", 0) + "," + number(attributes, "y1", 0)
                    + " L" + number(attributes, "x2", 0) + "," + number(attributes, "y2", 0);
            default -> null;
        };
    }

    /** Two half-arcs, because SVG has no "full ellipse" arc command. */
    private static String ellipse(double cx, double cy, double rx, double ry) {
        return "M" + (cx - rx) + "," + cy
                + " A" + rx + "," + ry + " 0 1 1 " + (cx + rx) + "," + cy
                + " A" + rx + "," + ry + " 0 1 1 " + (cx - rx) + "," + cy + " Z";
    }

    private static String rect(String attributes) {
        double w = number(attributes, "width", 0);
        double h = number(attributes, "height", 0);
        if (w <= 0 || h <= 0) return null;
        double x = number(attributes, "x", 0);
        double y = number(attributes, "y", 0);
        // Per the SVG spec, an omitted rx/ry mirrors the other one.
        double rx = number(attributes, "rx", Double.NaN);
        double ry = number(attributes, "ry", Double.NaN);
        if (Double.isNaN(rx) && Double.isNaN(ry)) {
            return "M" + x + "," + y + " H" + (x + w) + " V" + (y + h) + " H" + x + " Z";
        }
        if (Double.isNaN(rx)) rx = ry;
        if (Double.isNaN(ry)) ry = rx;
        rx = Math.min(rx, w / 2);
        ry = Math.min(ry, h / 2);
        return "M" + (x + rx) + "," + y
                + " H" + (x + w - rx)
                + " A" + rx + "," + ry + " 0 0 1 " + (x + w) + "," + (y + ry)
                + " V" + (y + h - ry)
                + " A" + rx + "," + ry + " 0 0 1 " + (x + w - rx) + "," + (y + h)
                + " H" + (x + rx)
                + " A" + rx + "," + ry + " 0 0 1 " + x + "," + (y + h - ry)
                + " V" + (y + ry)
                + " A" + rx + "," + ry + " 0 0 1 " + (x + rx) + "," + y + " Z";
    }

    private static String points(String attributes, boolean close) {
        String raw = attribute(attributes, "points");
        if (raw == null) return null;
        String[] numbers = raw.trim().split("[\\s,]+");
        if (numbers.length < 4) return null;
        StringBuilder out = new StringBuilder();
        // SVG points are a flat x,y,x,y stream; a trailing odd value is dropped.
        for (int i = 0; i + 1 < numbers.length; i += 2) {
            out.append(i == 0 ? "M" : " L").append(numbers[i]).append(',').append(numbers[i + 1]);
        }
        if (close) out.append(" Z");
        return out.toString();
    }

    private static String attribute(String attributes, String name) {
        Matcher matcher = Pattern.compile("\\b" + name + "\\s*=\\s*\"([^\"]*)\"|\\b" + name + "\\s*=\\s*'([^']*)'",
                Pattern.CASE_INSENSITIVE).matcher(attributes);
        if (!matcher.find()) return null;
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    private static double number(String attributes, String name, double fallback) {
        String value = attribute(attributes, name);
        if (value == null) return fallback;
        try {
            // Tolerates unit suffixes an editor may emit, e.g. width="24px".
            return Double.parseDouble(value.trim().replaceAll("(?i)(px|pt)$", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
