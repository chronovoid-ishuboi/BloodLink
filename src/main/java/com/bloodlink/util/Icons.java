package com.bloodlink.util;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * BloodLink's in-house icon set: a central registry of SVG path data drawn as
 * native JavaFX {@link SVGPath} nodes.
 * <p>
 * <b>Why hand-built rather than an icon font or an icon-pack dependency.</b>
 * This continues the convention the codebase already set with the brand
 * teardrop and {@link com.bloodlink.view.components.StarRating}'s star: every
 * glyph is a vector shape compiled into the app, so there is no new runtime
 * dependency, no network fetch, nothing to install on a lab machine, and no
 * possibility of a missing-glyph box if a font fails to load. It also keeps
 * the app fully offline, which the course demo requires.
 * <p>
 * <b>Every path here is self-authored geometry</b> on a 24x24 grid -- simple
 * primitives (rectangles, circles built from two arcs, and straight-line
 * polygons) rather than anything traced from an existing icon set.
 * <p>
 * <b>Colour comes from CSS, never from here.</b> Each icon is created with one
 * or more style classes and coloured by {@code -fx-fill} in {@code theme.css},
 * exactly like {@code .brand-icon}. That is what lets the same glyph be green
 * on a stat tile, white inside a primary button, and muted grey in a table
 * header without a single hard-coded hex value in Java.
 * <p>
 * <b>Sizing.</b> {@link #icon} wraps the scaled path in a {@link Group} rather
 * than calling {@code setScaleX/Y} on the path alone. A bare scale leaves the
 * node's layout bounds at 24x24, so an HBox would still reserve 24px for a
 * 14px glyph; a Group reports its children's transformed bounds, so layout
 * containers space icons correctly at any size.
 * <p>
 * Icons are rendered with the {@linkplain FillRule#EVEN_ODD even-odd fill
 * rule} so a shape can knock a hole out of itself (the hole in the map pin,
 * the check inside the shield, the slots in the trash bin). For the
 * single-outline shapes here, even-odd and non-zero are identical anyway.
 */
public final class Icons {

    private Icons() { }

    /** The grid every path below is drawn on. */
    public static final double CANVAS = 24.0;

    // --- Navigation ---------------------------------------------------------
    public static final String HOME = "M12,2.6 L21.5,10.6 L21.5,21.5 L14.2,21.5 L14.2,14.6 L9.8,14.6 L9.8,21.5 L2.5,21.5 L2.5,10.6 Z";
    public static final String LIST = "M3,5 H21 V7.6 H3 Z M3,10.7 H21 V13.3 H3 Z M3,16.4 H21 V19 H3 Z";
    public static final String GRID = "M3,3 H10.6 V10.6 H3 Z M13.4,3 H21 V10.6 H13.4 Z M3,13.4 H10.6 V21 H3 Z M13.4,13.4 H21 V21 H13.4 Z";
    public static final String CHEVRON_RIGHT = "M9.2,4.6 L11,3 L19.4,12 L11,21 L9.2,19.4 L16.2,12 Z";
    public static final String CHEVRON_LEFT = "M14.8,4.6 L13,3 L4.6,12 L13,21 L14.8,19.4 L7.8,12 Z";

    // --- People -------------------------------------------------------------
    public static final String USER = "M12,3.4 A4.3,4.3 0 1 0 12,12 A4.3,4.3 0 1 0 12,3.4 Z "
            + "M12,13.8 C7.6,13.8 4,16.5 4,20.1 V21.2 H20 V20.1 C20,16.5 16.4,13.8 12,13.8 Z";
    public static final String USERS = "M8.6,3.4 A3.8,3.8 0 1 0 8.6,11 A3.8,3.8 0 1 0 8.6,3.4 Z "
            + "M8.6,12.6 C4.8,12.6 1.6,15 1.6,18.2 V21 H15.6 V18.2 C15.6,15 12.4,12.6 8.6,12.6 Z "
            + "M17.2,5.2 A3.2,3.2 0 1 0 17.2,11.6 A3.2,3.2 0 1 0 17.2,5.2 Z "
            + "M17.2,13 C16.6,13 16.1,13.1 15.6,13.2 C16.9,14.6 17.6,16.3 17.6,18.2 V21 H22.4 V18.2 C22.4,15.3 20.1,13 17.2,13 Z";

    // --- Medical ------------------------------------------------------------
    /** The brand teardrop, reused as the "blood" glyph so the mark and the icon set share one shape. */
    public static final String DROPLET = "M12,2 C12,2 5,10.6 5,14.9 A7,7 0 0 0 19,14.9 C19,10.6 12,2 12,2 Z";
    public static final String PLUS_CROSS = "M9.4,2.8 H14.6 V9.4 H21.2 V14.6 H14.6 V21.2 H9.4 V14.6 H2.8 V9.4 H9.4 Z";
    public static final String HOSPITAL = "M3.6,21.4 V6.4 L12,2.2 L20.4,6.4 V21.4 Z "
            + "M10.8,8.6 H13.2 V11.6 H16.2 V14 H13.2 V17 H10.8 V14 H7.8 V11.6 H10.8 Z";
    public static final String HEART = "M12,21.2 C12,21.2 2.8,15 2.8,8.9 A4.9,4.9 0 0 1 12,6.2 A4.9,4.9 0 0 1 21.2,8.9 C21.2,15 12,21.2 12,21.2 Z";
    public static final String LOCK = "M4.4,10.4 H19.6 V21.8 H4.4 Z "
            + "M7,10.4 V7.4 A5,5 0 0 1 17,7.4 V10.4 H14.6 V7.4 A2.6,2.6 0 0 0 9.4,7.4 V10.4 Z "
            + "M12,13 A1.9,1.9 0 1 1 12,16.8 A1.9,1.9 0 1 1 12,13 Z "
            + "M11.2,16.8 H12.8 V19.4 H11.2 Z";
    /** Stroke-only: draw with {@link #pulseLine}, never filled. */
    public static final String PULSE = "M1.5,12 H6.8 L9.3,4.6 L13.2,19.4 L15.7,12 H22.5";

    // --- Status -------------------------------------------------------------
    public static final String SHIELD_CHECK = "M12,2.2 L20.2,5.4 V11.6 C20.2,16.8 16.8,20.9 12,22.2 C7.2,20.9 3.8,16.8 3.8,11.6 V5.4 Z "
            + "M10.9,16.1 L7.2,12.4 L8.9,10.7 L10.9,12.7 L15.3,8.3 L17,10 Z";
    public static final String CHECK_CIRCLE = "M12,2 A10,10 0 1 0 12,22 A10,10 0 1 0 12,2 Z "
            + "M10.7,16.6 L6.2,12.1 L8,10.3 L10.7,13 L16,7.7 L17.8,9.5 Z";
    public static final String X_CIRCLE = "M12,2 A10,10 0 1 0 12,22 A10,10 0 1 0 12,2 Z "
            + "M8.5,7 L12,10.5 L15.5,7 L17,8.5 L13.5,12 L17,15.5 L15.5,17 L12,13.5 L8.5,17 L7,15.5 L10.5,12 L7,8.5 Z";
    public static final String CLOCK = "M12,2 A10,10 0 1 0 12,22 A10,10 0 1 0 12,2 Z "
            + "M10.9,5.8 H13.1 V12.3 L17.1,14.6 L16,16.5 L10.9,13.6 Z";
    public static final String BELL = "M12,2.2 C8.6,2.2 6.3,4.7 6.3,8.1 V13.2 L4.2,16.8 H19.8 L17.7,13.2 V8.1 C17.7,4.7 15.4,2.2 12,2.2 Z "
            + "M9.6,18.2 A2.4,2.4 0 0 0 14.4,18.2 Z";
    public static final String MEDAL = "M7.8,2 H10.5 L12,5.8 L13.5,2 H16.2 L13.7,8 H10.3 Z "
            + "M12,8.2 A6.9,6.9 0 1 0 12,22 A6.9,6.9 0 1 0 12,8.2 Z "
            + "M12,11.3 A3.8,3.8 0 1 1 12,18.9 A3.8,3.8 0 1 1 12,11.3 Z";

    // --- Contact / data -----------------------------------------------------
    public static final String PHONE = "M6.8,2.6 L9.6,2.6 L11.3,7.5 L9,9.2 C10,11.5 12.5,14 14.8,15 L16.5,12.7 L21.4,14.4 L21.4,17.2 C21.4,19.5 19.5,21.4 17.2,21.4 C9.1,21.4 2.6,14.9 2.6,6.8 C2.6,4.5 4.5,2.6 6.8,2.6 Z";
    public static final String MAIL = "M2.4,5.2 H21.6 V18.8 H2.4 Z "
            + "M4.6,7.3 L12,13.1 L19.4,7.3 L19.4,9.5 L12,15.3 L4.6,9.5 Z";
    public static final String CALENDAR = "M6.2,2.2 H8.4 V4.4 H15.6 V2.2 H17.8 V4.4 H21 V21.6 H3 V4.4 H6.2 Z "
            + "M5.2,9 H18.8 V19.4 H5.2 Z";
    public static final String MAP_PIN = "M12,2 A7.2,7.2 0 0 0 4.8,9.2 C4.8,14.6 12,22.2 12,22.2 C12,22.2 19.2,14.6 19.2,9.2 A7.2,7.2 0 0 0 12,2 Z "
            + "M12,6.5 A2.7,2.7 0 1 1 12,11.9 A2.7,2.7 0 1 1 12,6.5 Z";

    // --- Actions ------------------------------------------------------------
    public static final String EDIT = "M2.6,17.2 L14.6,5.2 L18.8,9.4 L6.8,21.4 L2.6,21.4 Z M16.2,3.6 L18,1.8 L22.2,6 L20.4,7.8 Z";
    public static final String LOGOUT = "M3.4,2.6 H12 V5.4 H6.2 V18.6 H12 V21.4 H3.4 Z "
            + "M14.6,7.2 L19.4,12 L14.6,16.8 L12.7,14.9 L14.6,13 H8.8 V11 H14.6 L12.7,9.1 Z";
    public static final String SEARCH = "M10.4,2.6 A7.8,7.8 0 1 0 10.4,18.2 A7.8,7.8 0 1 0 10.4,2.6 Z "
            + "M10.4,5 A5.4,5.4 0 1 1 10.4,15.8 A5.4,5.4 0 1 1 10.4,5 Z "
            + "M16,15.3 L21.6,20.9 L20,22.4 L14.4,16.8 Z";
    public static final String FILTER = "M2.6,3.6 H21.4 L14.4,12.4 V20 L9.6,21.8 V12.4 Z";
    public static final String UPLOAD = "M12,2.4 L18.2,8.6 L16,10.8 L13.5,8.3 V15.6 H10.5 V8.3 L8,10.8 L5.8,8.6 Z "
            + "M3.6,14.4 H6.6 V18.6 H17.4 V14.4 H20.4 V21.6 H3.6 Z";
    public static final String TRASH = "M9,2.4 H15 V4.6 H21.2 V7.2 H2.8 V4.6 H9 Z "
            + "M4.8,8.6 H19.2 V21.6 H4.8 Z "
            + "M8.4,11 H10.4 V19.2 H8.4 Z M13.6,11 H15.6 V19.2 H13.6 Z";
    public static final String PLUS_CIRCLE = "M12,2 A10,10 0 1 0 12,22 A10,10 0 1 0 12,2 Z "
            + "M10.7,6.8 H13.3 V10.7 H17.2 V13.3 H13.3 V17.2 H10.7 V13.3 H6.8 V10.7 H10.7 Z";
    public static final String SETTINGS = "M19.8,10.76 L22.31,9.53 L22.31,14.47 L19.8,13.24 L18.39,16.64 L21.04,17.54 "
            + "L17.54,21.04 L16.64,18.39 L13.24,19.8 L14.47,22.31 L9.53,22.31 L10.76,19.8 L7.36,18.39 L6.46,21.04 "
            + "L2.96,17.54 L5.61,16.64 L4.2,13.24 L1.69,14.47 L1.69,9.53 L4.2,10.76 L5.61,7.36 L2.96,6.46 "
            + "L6.46,2.96 L7.36,5.61 L10.76,4.2 L9.53,1.69 L14.47,1.69 L13.24,4.2 L16.64,5.61 L17.54,2.96 "
            + "L21.04,6.46 L18.39,7.36 Z "
            + "M12,8.1 A3.9,3.9 0 1 1 12,15.9 A3.9,3.9 0 1 1 12,8.1 Z";
    public static final String CHART = "M3,20.4 H21 V22 H3 Z M4.6,11 H8 V19 H4.6 Z M10.3,6 H13.7 V19 H10.3 Z M16,13.4 H19.4 V19 H16 Z";
    public static final String STAR = "M12,2 L14.9,8.6 L22,9.3 L16.6,14.1 L18.2,21 L12,17.4 L5.8,21 L7.4,14.1 L2,9.3 L9.1,8.6 Z";

    /**
     * Builds an icon node at {@code size} pixels square, carrying the given CSS
     * style classes on the underlying shape so {@code -fx-fill} can colour it.
     *
     * @param pathData one of the constants above
     * @param size     rendered edge length in pixels
     * @param styleClasses style classes applied to the shape (not the wrapper)
     */
    public static Node icon(String pathData, double size, String... styleClasses) {
        return wrap(shape(pathData, styleClasses), size);
    }

    /**
     * The pulse/ECG motif as a stroked (not filled) polyline. Pair with the
     * {@code .pulse-line}, {@code .pulse-line-muted} or {@code .pulse-divider}
     * style classes, which set the stroke and force the fill transparent.
     */
    public static Node pulseLine(double width, String... styleClasses) {
        SVGPath path = new SVGPath();
        path.setContent(PULSE);
        path.setFillRule(FillRule.EVEN_ODD);
        path.getStyleClass().add("pulse-line");
        path.getStyleClass().addAll(styleClasses);
        return wrap(path, width);
    }

    /** The raw shape at its natural 24x24 size, for callers doing their own layout. */
    public static SVGPath shape(String pathData, String... styleClasses) {
        SVGPath path = new SVGPath();
        path.setContent(pathData);
        path.setFillRule(FillRule.EVEN_ODD);
        path.getStyleClass().addAll(styleClasses);
        return path;
    }

    private static Node wrap(SVGPath path, double size) {
        double factor = size / CANVAS;
        path.getTransforms().add(new Scale(factor, factor));
        // A Group reports its children's *transformed* bounds, so an HBox/VBox
        // reserves the scaled size rather than the untransformed 24x24 grid.
        Group group = new Group(path);
        group.setManaged(true);
        return group;
    }
}
