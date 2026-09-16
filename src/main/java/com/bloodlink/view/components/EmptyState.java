package com.bloodlink.view.components;

import com.bloodlink.util.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The placeholder shown where a table or list has nothing in it yet.
 * <p>
 * Replaces the plain italic grey sentence the dashboards used to hand to
 * {@code setPlaceholder()}. An empty screen is the first thing a new user sees,
 * so it now carries a small drawn illustration -- the app's teardrop mark
 * crossed by the pulse motif, both already part of the design language -- above
 * a title and one sentence saying what would put something here.
 * <p>
 * Built from {@link Icons} shapes, so like every other glyph in the app it is
 * vector, offline, and coloured from {@code theme.css}.
 */
public final class EmptyState {

    private EmptyState() { }

    /**
     * @param title what is empty, e.g. "No donors matched yet"
     * @param hint  one sentence on what would fill it
     */
    public static VBox of(String title, String hint) {
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);

        StackPane art = new StackPane(
                Icons.icon(Icons.DROPLET, 44, "empty-state-art"),
                Icons.pulseLine(30, "empty-state-pulse"));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");
        titleLabel.setWrapText(true);
        titleLabel.setAlignment(Pos.CENTER);

        box.getChildren().addAll(art, titleLabel);

        if (hint != null && !hint.isBlank()) {
            Label hintLabel = new Label(hint);
            hintLabel.getStyleClass().add("empty-state-hint");
            hintLabel.setWrapText(true);
            hintLabel.setMaxWidth(320);
            hintLabel.setAlignment(Pos.CENTER);
            box.getChildren().add(hintLabel);
        }
        return box;
    }

    /** Single-sentence form, for placeholders that need no extra hint. */
    public static VBox of(String title) { return of(title, null); }
}
