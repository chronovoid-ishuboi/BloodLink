package com.bloodlink.view.components;

import com.bloodlink.model.DonorMatchView;
import com.bloodlink.model.Urgency;
import com.bloodlink.util.Icons;
import com.bloodlink.util.PhotoCache;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.Locale;

/**
 * One incoming request, as shown to a donor in their matched-requests list.
 * <p>
 * Note this cell shows the <em>requester</em> side of a match, not a donor, so
 * there is no donor profile to open from here -- that lives on
 * {@link RequesterMatchCell}, which is the cell that actually represents donors.
 * <p>
 * Colours and layout moved from inline {@code setStyle()} calls to the
 * {@code .match-card*} classes in {@code theme.css}, matching the rest of the app.
 * A CRITICAL request gets a slowly pulsing dot beside its label -- the one piece
 * of motion in the list, reserved for the one state that means somebody needs
 * blood now.
 */
public final class DonorMatchCell extends ListCell<DonorMatchView> {

    /** Kept so the animation is stopped when the cell is recycled for another row. */
    private Animation urgencyPulse;

    @Override
    protected void updateItem(DonorMatchView match, boolean empty) {
        super.updateItem(match, empty);
        setText(null);
        stopPulse();

        if (empty || match == null) {
            setGraphic(null);
            return;
        }

        HBox card = new HBox(14);
        card.getStyleClass().add("match-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(avatar(match));

        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label name = new Label(match.requesterName() == null ? "Unknown requester" : match.requesterName());
        name.getStyleClass().add("match-card-name");

        HBox details = new HBox(10);
        details.setAlignment(Pos.CENTER_LEFT);
        details.getChildren().add(iconText(Icons.DROPLET,
                match.bloodGroup() == null ? "—" : match.bloodGroup().getDisplayName()));
        details.getChildren().add(iconText(Icons.HOSPITAL,
                blankToDash(match.hospitalName()) + " (" + blankToDash(match.district()) + ")"));
        if (match.distanceKm() != null) {
            details.getChildren().add(iconText(Icons.MAP_PIN,
                    String.format(Locale.ENGLISH, "%.1f km", match.distanceKm())));
        }

        HBox progress = new HBox(10);
        progress.setAlignment(Pos.CENTER_LEFT);
        progress.getChildren().add(iconText(Icons.USERS,
                match.unitsFulfilled() + " / " + match.unitsNeeded() + " units filled"));
        if (match.deadline() != null) {
            progress.getChildren().add(iconText(Icons.CALENDAR, "by " + match.deadline()));
        }

        // requesterRating is null when this requester has no reviews; StarRating
        // renders that as "No reviews yet" rather than zero stars.
        info.getChildren().addAll(name, details, progress, new StarRating(match.requesterRating(), true, 14));

        VBox status = new VBox(6);
        status.setAlignment(Pos.TOP_RIGHT);
        Label matchStatus = new Label(match.matchStatus().name());
        matchStatus.getStyleClass().addAll("chip", "chip-" + match.matchStatus().name().toLowerCase(Locale.ROOT));
        status.getChildren().addAll(matchStatus, urgency(match.urgency()));

        card.getChildren().addAll(info, status);
        setGraphic(card);
    }

    private Node urgency(Urgency urgency) {
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER_RIGHT);
        String name = urgency == null ? "NORMAL" : urgency.name();

        if ("CRITICAL".equals(name)) {
            Circle dot = new Circle(4);
            dot.getStyleClass().add("pulse-dot");
            FadeTransition pulse = new FadeTransition(Duration.seconds(0.9), dot);
            pulse.setFromValue(1.0);
            pulse.setToValue(0.25);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.setAutoReverse(true);
            pulse.play();
            urgencyPulse = pulse;
            row.getChildren().add(dot);
        }

        Label label = new Label(name);
        label.getStyleClass().addAll("urgency-label", "urgency-" + name.toLowerCase(Locale.ROOT).replace('_', '-'));
        row.getChildren().add(label);
        return row;
    }

    /**
     * ListCells are recycled, so an animation left running on a cell that has
     * moved on would keep ticking forever and leak a timer per scrolled row.
     */
    private void stopPulse() {
        if (urgencyPulse != null) {
            urgencyPulse.stop();
            urgencyPulse = null;
        }
    }

    private Node avatar(DonorMatchView match) {
        Image photo = PhotoCache.getPhotoSync(match.requesterId());
        if (photo != null) {
            ImageView view = new ImageView(photo);
            view.setFitWidth(48);
            view.setFitHeight(48);
            view.setPreserveRatio(false);
            view.setClip(new Circle(24, 24, 24));
            view.getStyleClass().add("avatar-photo");
            return view;
        }
        Label initials = new Label(DonorProfileDialog.initialsOf(match.requesterName()));
        initials.getStyleClass().addAll("avatar-initials", "avatar-initials-sm");
        return initials;
    }

    private static Node iconText(String iconPath, String text) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(text);
        label.getStyleClass().add("match-card-detail");
        row.getChildren().addAll(Icons.icon(iconPath, 12, "match-card-icon"), label);
        return row;
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
