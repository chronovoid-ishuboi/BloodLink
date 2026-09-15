package com.bloodlink.view.components;

import com.bloodlink.model.BadgeTier;
import com.bloodlink.util.BadgeRegistry;
import com.bloodlink.util.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Renders a donor's badge as icon + tier name, replacing the bare
 * {@code new Label(badgeTier.name())} the dashboards used to show (which put
 * raw enum constants like "PLATINUM" on screen).
 * <p>
 * Same pattern as {@link StarRating}: a self-contained {@code HBox} subclass
 * built in Java so any screen can drop one in.
 * <p>
 * Everything visual — the label text, the icon geometry, the colour — comes from
 * {@link BadgeRegistry}, i.e. from {@code resources/com/bloodlink/badges/}, so
 * restyling badges never means editing this class. The tier colour is the one
 * place the app sets a fill from Java rather than CSS, because that colour is
 * user data from {@code badges.json} and cannot be a fixed stylesheet rule;
 * layout and typography still come from the {@code .badge-*} classes in
 * {@code theme.css}.
 */
public final class BadgeView extends HBox {

    /** Standard inline size, e.g. on a match card or a dashboard stat tile. */
    public BadgeView(BadgeTier tier) { this(tier, 16, false); }

    /**
     * @param tier      the donor's tier; {@code null} is treated as {@link BadgeTier#NONE}
     * @param iconSize  icon edge length in pixels
     * @param chip      when true, draws the badge on a tinted rounded pill
     */
    public BadgeView(BadgeTier tier, double iconSize, boolean chip) {
        super(6);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add(chip ? "badge-chip" : "badge-view");

        BadgeRegistry.BadgeStyle style = BadgeRegistry.styleFor(tier);
        Color color = style.color();

        if (style.pathData() != null) {
            SVGPath shape = Icons.shape(style.pathData());
            shape.setFill(color);
            double factor = iconSize / Icons.CANVAS;
            shape.getTransforms().add(new javafx.scene.transform.Scale(factor, factor));
            getChildren().add(new javafx.scene.Group(shape));
        }

        Label label = new Label(style.label());
        label.getStyleClass().add(iconSize >= 20 ? "badge-label-lg" : "badge-label");
        label.setTextFill(color);
        getChildren().add(label);

        if (chip) {
            // Derived from the tier colour rather than hard-coded, so a user's own
            // palette in badges.json still produces a readable pill.
            setStyle("-fx-background-color: " + toRgba(color, 0.14) + ";");
        }
        setAccessibleText(style.label());
    }

    private static String toRgba(Color color, double alpha) {
        return String.format("rgba(%d,%d,%d,%.2f)",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255),
                alpha);
    }
}
