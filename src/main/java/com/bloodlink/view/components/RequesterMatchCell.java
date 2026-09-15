package com.bloodlink.view.components;

import com.bloodlink.model.MatchCandidate;
import com.bloodlink.util.Icons;
import com.bloodlink.util.PhotoCache;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.Locale;

/**
 * One ranked donor, as shown to a requester in the matched-donors list.
 * <p>
 * All colour and layout now comes from the {@code .match-card*} classes in
 * {@code theme.css} instead of the inline {@code setStyle()} calls this class
 * used to carry, so the palette lives in one file. The tier badge is a real
 * {@link BadgeView} rather than the raw enum constant ("PLATINUM") it used to
 * print.
 * <p>
 * <b>Opening the profile.</b> The requester toolbar's Confirm Received and Rate
 * Donor actions both operate on the list <em>selection</em>, so opening a modal
 * profile on every single click would make those buttons unusable. Instead the
 * card keeps single-click selection and offers the profile two ways that cannot
 * collide with it: the "Profile" affordance on the right of the card, and a
 * double-click anywhere on the card.
 */
public final class RequesterMatchCell extends ListCell<MatchCandidate> {

    @Override
    protected void updateItem(MatchCandidate match, boolean empty) {
        super.updateItem(match, empty);
        setText(null);
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

        Label name = new Label(match.donorName() == null ? "Unknown donor" : match.donorName());
        name.getStyleClass().add("match-card-name");

        HBox details = new HBox(10);
        details.setAlignment(Pos.CENTER_LEFT);
        details.getChildren().add(iconText(Icons.DROPLET,
                match.bloodGroup() == null ? "—" : match.bloodGroup().getDisplayName()));
        details.getChildren().add(iconText(Icons.MAP_PIN, blankToDash(match.district())));
        // A null distance is genuinely unknown -- never rendered as a number.
        details.getChildren().add(iconText(Icons.HOSPITAL, match.distanceKm() == null
                ? "distance unavailable"
                : String.format(Locale.ENGLISH, "%.1f km away", match.distanceKm())));

        // averageRating is null when this donor has no reviews; StarRating renders
        // that as "No reviews yet" rather than zero stars.
        info.getChildren().addAll(name, details, new StarRating(match.averageRating(), true, 14));

        VBox status = new VBox(6);
        status.setAlignment(Pos.TOP_RIGHT);
        Label matchStatus = new Label(match.matchStatus().name());
        matchStatus.getStyleClass().addAll("chip", "chip-" + match.matchStatus().name().toLowerCase(Locale.ROOT));
        status.getChildren().addAll(matchStatus, new BadgeView(match.badgeTier()), profileAffordance(match));

        card.getChildren().addAll(info, status);

        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) openProfile(match);
        });

        setGraphic(card);
    }

    private Node avatar(MatchCandidate match) {
        Image photo = PhotoCache.getPhotoSync(match.donorId());
        if (photo != null) {
            ImageView view = new ImageView(photo);
            view.setFitWidth(48);
            view.setFitHeight(48);
            view.setPreserveRatio(false);
            // ImageView is not a Region, so the circular crop must be a clip, not CSS.
            view.setClip(new Circle(24, 24, 24));
            view.getStyleClass().add("avatar-photo");
            return view;
        }
        Label initials = new Label(DonorProfileDialog.initialsOf(match.donorName()));
        initials.getStyleClass().addAll("avatar-initials", "avatar-initials-sm");
        return initials;
    }

    private Node profileAffordance(MatchCandidate match) {
        HBox link = new HBox(3);
        link.setAlignment(Pos.CENTER_RIGHT);
        Label label = new Label("Profile");
        label.getStyleClass().add("match-card-hint");
        link.getChildren().addAll(label, Icons.icon(Icons.CHEVRON_RIGHT, 11, "match-card-hint-icon"));
        link.setStyle("-fx-cursor: hand;");
        link.setOnMouseClicked(event -> {
            // Stops the click from also reaching the card's double-click handler.
            event.consume();
            openProfile(match);
        });
        return link;
    }

    private void openProfile(MatchCandidate match) {
        if (getScene() == null) return;
        DonorProfileDialog.show(getScene().getWindow(), match.donorId(), match.matchStatus(), match.distanceKm());
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
