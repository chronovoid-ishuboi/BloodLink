package com.bloodlink.view.components;

import com.bloodlink.util.Icons;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

import java.util.Locale;

/**
 * A five-star rating row.
 * <p>
 * <b>Real partial stars.</b> This previously approximated a half star by
 * filling it in a slightly lighter shade, which read as "a slightly different
 * whole star" rather than a half -- fine when ratings were a minor detail on a
 * list row, misleading now that they head the donor profile. Each star is now an
 * empty star with a filled star laid over it, clipped to the exact fraction
 * earned, so 4.3 renders as four full stars and a 30%-filled fifth. The clip is
 * a rectangle in the icon's own 24x24 coordinate space, applied before the
 * scale transform, so it stays exact at any size.
 * <p>
 * <b>Null means "no reviews", not zero.</b> The nullable constructor renders
 * "No reviews yet" instead of five empty stars. {@code ReputationSummary},
 * {@code MatchCandidate} and {@code DonorMatchView} all document that rule;
 * putting it inside the one widget everybody uses is what stops the next screen
 * from getting it wrong.
 * <p>
 * Colours come from the {@code .star-filled} / {@code .star-empty} classes in
 * {@code theme.css}, not from inline styles.
 */
public final class StarRating extends HBox {

    private static final double DEFAULT_STAR_SIZE = 15;

    public StarRating(double rating) { this(rating, false); }

    public StarRating(double rating, boolean showNumber) { this(Double.valueOf(rating), showNumber, DEFAULT_STAR_SIZE); }

    /**
     * @param rating     average rating, or {@code null} when there are no reviews
     *                    yet -- rendered as "No reviews yet", never as 0 stars
     * @param showNumber append the numeric value after the stars
     * @param starSize   edge length of one star in pixels
     */
    public StarRating(Double rating, boolean showNumber, double starSize) {
        super(3);
        setAlignment(Pos.CENTER_LEFT);

        if (rating == null) {
            Label none = new Label("No reviews yet");
            none.getStyleClass().add("star-rating-empty-note");
            getChildren().add(none);
            setAccessibleText("No reviews yet");
            return;
        }

        double clamped = Math.max(0, Math.min(5, rating));
        for (int i = 1; i <= 5; i++) {
            // How much of THIS star is earned: 1 for a full star, 0 for an empty
            // one, and the remainder in between.
            getChildren().add(star(Math.max(0, Math.min(1, clamped - (i - 1))), starSize));
        }

        if (showNumber) {
            Label number = new Label(String.format(Locale.ENGLISH, "%.1f", clamped));
            number.getStyleClass().add("star-rating-number");
            getChildren().add(number);
        }
        setAccessibleText(String.format(Locale.ENGLISH, "%.1f out of 5 stars", clamped));
    }

    private static Node star(double filledFraction, double size) {
        SVGPath empty = Icons.shape(Icons.STAR, "star-empty");
        Group layers = new Group(empty);

        if (filledFraction > 0) {
            SVGPath filled = Icons.shape(Icons.STAR, "star-filled");
            if (filledFraction < 1) {
                // Clipped in the icon's own 24x24 space, so the cut lands at exactly
                // the right fraction of the star's width once scaled.
                filled.setClip(new Rectangle(0, 0, Icons.CANVAS * filledFraction, Icons.CANVAS));
            }
            layers.getChildren().add(filled);
        }

        double factor = size / Icons.CANVAS;
        layers.getTransforms().add(new Scale(factor, factor));
        return new Group(layers);
    }
}
