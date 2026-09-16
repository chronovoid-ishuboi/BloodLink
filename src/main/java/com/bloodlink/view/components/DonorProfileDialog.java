package com.bloodlink.view.components;

import com.bloodlink.model.*;
import com.bloodlink.service.DonorProfileService;
import com.bloodlink.service.ServiceResult;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.Icons;
import com.bloodlink.util.PhotoCache;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The rich donor profile a requester or admin can open from a match card or the
 * admin Users table -- the "who am I actually calling at 2am" screen, in the
 * spirit of a ride-share driver card: photo, badge, rating, availability, blood
 * group, then the evidence behind it (donation timeline and reviews).
 * <p>
 * Built as a component rendered into its own modal {@link Stage} rather than as
 * a sixth FXML view, so every caller opens the same profile with one line and
 * there is no per-caller controller to keep in sync. Same spirit as
 * {@link ActivityFeed}: a self-contained node that owns its own layout.
 * <p>
 * Two rules this screen must not break, both inherited from the model layer:
 * <ul>
 *   <li>A donor with no reviews renders "No reviews yet", never zero stars --
 *       the rule {@code MatchCandidate} and {@code ReputationSummary} document.</li>
 *   <li>A {@code null} distance renders as unavailable, never as a made-up number.</li>
 * </ul>
 * Contact visibility is decided by {@link DonorProfileService} from the live
 * session; this class only renders whichever of the two states it is handed.
 */
public final class DonorProfileDialog {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private DonorProfileDialog() { }

    /**
     * Opens the profile for {@code donorId}, loading off the JavaFX Application
     * Thread and showing a placeholder until it arrives, so a slow connection
     * never freezes the dashboard behind it.
     *
     * @param owner       window to centre on and block
     * @param donorId     donor to show
     * @param matchStatus this donor's status on the request being viewed, or
     *                     {@code null} when opened outside a match context
     * @param distanceKm  distance from the match context, or {@code null}
     */
    public static void show(Window owner, long donorId, MatchStatus matchStatus, Double distanceKm) {
        Stage stage = newStage(owner);
        VBox shell = new VBox();
        shell.getStyleClass().add("profile-dialog");

        ScrollPane scroller = new ScrollPane();
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        VBox body = new VBox(18);
        body.setPadding(new Insets(20, 24, 24, 24));
        body.getChildren().add(loadingPlaceholder());
        scroller.setContent(body);
        shell.getChildren().add(scroller);

        stage.setScene(themedScene(shell));
        stage.show();

        DonorProfileService service = new DonorProfileService();
        BackgroundTasks.run(() -> service.load(donorId, matchStatus, distanceKm),
                result -> render(stage, shell, body, result),
                error -> body.getChildren().setAll(
                        message("Could not load this profile", error.getMessage() == null
                                ? error.getClass().getSimpleName() : error.getMessage())));
    }

    /**
     * Shows a profile that has already been loaded, skipping the fetch. Exists so
     * the same rendering can be exercised without a database behind it.
     */
    public static Stage showProfile(Window owner, DonorProfileView profile) {
        Stage stage = newStage(owner);
        VBox shell = new VBox();
        shell.getStyleClass().add("profile-dialog");
        ScrollPane scroller = new ScrollPane();
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroller, Priority.ALWAYS);
        VBox body = new VBox(18);
        body.setPadding(new Insets(20, 24, 24, 24));
        scroller.setContent(body);
        shell.getChildren().add(scroller);
        stage.setScene(themedScene(shell));
        render(stage, shell, body, ServiceResult.success("Profile loaded.", profile));
        stage.show();
        return stage;
    }

    private static Stage newStage(Window owner) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.setTitle("Donor profile");
        return stage;
    }

    private static Scene themedScene(VBox shell) {
        Scene scene = new Scene(shell, 560, 720);
        java.net.URL stylesheet = DonorProfileDialog.class.getResource("/com/bloodlink/css/theme.css");
        // Dialogs live in their own Stage, so they do not inherit the dashboard's
        // stylesheet -- without this the style classes below would silently do nothing.
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        return scene;
    }

    private static void render(Stage stage, VBox shell, VBox body, ServiceResult<DonorProfileView> result) {
        if (!result.success() || result.data() == null) {
            body.getChildren().setAll(message("Profile unavailable", result.message()));
            return;
        }
        DonorProfileView profile = result.data();
        stage.setTitle(profile.fullName() + " — donor profile");

        // The header band sits outside the scroll area so it stays put while the
        // evidence below scrolls.
        shell.getChildren().add(0, header(profile));

        body.getChildren().setAll(
                quickStats(profile),
                contactSection(profile),
                donationTimeline(profile.donations()),
                reviewSection(profile));
    }

    // --- Header -------------------------------------------------------------

    private static Node header(DonorProfileView profile) {
        HBox band = new HBox(18);
        band.getStyleClass().add("profile-header");
        band.setAlignment(Pos.CENTER_LEFT);

        band.getChildren().add(avatar(profile.donorId(), profile.fullName()));

        VBox identity = new VBox(8);
        HBox.setHgrow(identity, Priority.ALWAYS);

        Label name = new Label(profile.fullName());
        name.getStyleClass().add("profile-name");
        name.setWrapText(true);

        HBox badgeRow = new HBox(10);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        badgeRow.getChildren().addAll(new BadgeView(profile.badgeTier(), 18, true), availabilityPill(profile));

        identity.getChildren().addAll(name, badgeRow, ratingRow(profile));

        // Blood group is the single fact a requester scans for, so it gets the
        // largest, highest-contrast treatment on the screen.
        Label bloodGroup = new Label(profile.bloodGroup() == null ? "—" : profile.bloodGroup().getDisplayName());
        bloodGroup.getStyleClass().add("chip-blood-group");

        band.getChildren().addAll(identity, bloodGroup);
        return band;
    }

    private static Node avatar(long donorId, String fullName) {
        Image photo = PhotoCache.getPhotoSync(donorId);
        if (photo != null) {
            ImageView view = new ImageView(photo);
            view.setFitWidth(84);
            view.setFitHeight(84);
            view.setPreserveRatio(false);
            // ImageView is not a Region, so the circular crop is a clip, not CSS.
            view.setClip(new Circle(42, 42, 42));
            view.getStyleClass().add("avatar-photo");
            return view;
        }
        Label initials = new Label(initialsOf(fullName));
        initials.getStyleClass().add("avatar-initials");
        return initials;
    }

    private static Node availabilityPill(DonorProfileView profile) {
        AvailabilityStatus status = profile.availabilityStatus();
        Label pill = new Label(status == null ? "Unknown" : status.getLabel());
        pill.getStyleClass().addAll("availability-pill",
                "availability-" + (status == null ? "out-of-town" : status.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        return pill;
    }

    private static Node ratingRow(DonorProfileView profile) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        if (!profile.hasReviews()) {
            // Never 0 stars for "no data" -- see ReputationSummary's Javadoc.
            Label none = new Label("No reviews yet");
            none.getStyleClass().add("profile-subtle");
            row.getChildren().add(none);
            return row;
        }
        Label count = new Label("(" + profile.reviewCount() + (profile.reviewCount() == 1 ? " review)" : " reviews)"));
        count.getStyleClass().add("profile-subtle");
        row.getChildren().addAll(new StarRating(profile.averageRating(), true, 16), count);
        return row;
    }

    // --- Quick stats --------------------------------------------------------

    private static Node quickStats(DonorProfileView profile) {
        HBox strip = new HBox(12);
        strip.getChildren().addAll(
                statTile(Icons.DROPLET, String.valueOf(profile.verifiedDonationCount()), "DONATIONS"),
                statTile(Icons.CALENDAR, profile.memberSince() == null ? "—" : profile.memberSince().format(MONTH), "MEMBER SINCE"),
                statTile(Icons.MAP_PIN, blankToDash(profile.district()), "DISTRICT"),
                statTile(Icons.HOSPITAL,
                        profile.distanceKm() == null ? "—" : String.format(Locale.ENGLISH, "%.1f km", profile.distanceKm()),
                        profile.distanceKm() == null ? "DISTANCE N/A" : "DISTANCE"));
        for (Node tile : strip.getChildren()) HBox.setHgrow(tile, Priority.ALWAYS);
        return strip;
    }

    private static Node statTile(String iconPath, String value, String label) {
        VBox tile = new VBox();
        tile.getStyleClass().add("profile-stat-tile");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("profile-stat-value");
        Label captionLabel = new Label(label);
        captionLabel.getStyleClass().add("profile-stat-label");
        tile.getChildren().addAll(Icons.icon(iconPath, 18, "profile-icon"), valueLabel, captionLabel);
        return tile;
    }

    // --- Contact ------------------------------------------------------------

    private static Node contactSection(DonorProfileView profile) {
        VBox card = new VBox(12);
        card.getStyleClass().add("content-card");
        card.getChildren().add(sectionTitle(Icons.PHONE, "Contact"));

        if (!profile.contactVisible()) {
            VBox locked = new VBox(8);
            locked.getStyleClass().add("contact-locked");
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            Label reason = new Label(profile.contactLockedReason());
            reason.getStyleClass().add("contact-locked-text");
            reason.setWrapText(true);
            HBox.setHgrow(reason, Priority.ALWAYS);
            row.getChildren().addAll(Icons.icon(Icons.CLOCK, 18, "profile-icon-muted"), reason);
            locked.getChildren().add(row);
            card.getChildren().add(locked);
            return card;
        }

        card.getChildren().addAll(
                contactRow(Icons.PHONE, blankToDash(profile.phone())),
                contactRow(Icons.MAIL, blankToDash(profile.email())),
                contactRow(Icons.MAP_PIN, blankToDash(profile.address())));
        return card;
    }

    private static Node contactRow(String iconPath, String value) {
        HBox row = new HBox(10);
        row.getStyleClass().add("contact-row");
        Label label = new Label(value);
        label.getStyleClass().add("contact-value");
        label.setWrapText(true);
        row.getChildren().addAll(Icons.icon(iconPath, 15, "profile-icon-muted"), label);
        return row;
    }

    // --- Donation history ---------------------------------------------------

    private static Node donationTimeline(List<DonationRecord> donations) {
        VBox card = new VBox(12);
        card.getStyleClass().add("content-card");
        card.getChildren().add(sectionTitle(Icons.DROPLET, "Donation history"));

        if (donations.isEmpty()) {
            card.getChildren().add(emptyState("No donations recorded yet",
                    "Verified donations appear here once a handshake is confirmed."));
            return card;
        }

        VBox timeline = new VBox(0);
        for (int i = 0; i < donations.size(); i++) {
            timeline.getChildren().add(timelineEntry(donations.get(i), i == donations.size() - 1));
        }
        card.getChildren().add(timeline);
        return card;
    }

    private static Node timelineEntry(DonationRecord record, boolean last) {
        HBox row = new HBox(12);

        // The rail: a node on the line, with the connector omitted on the last entry.
        VBox rail = new VBox(0);
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setMinWidth(14);
        rail.setMaxWidth(14);
        javafx.scene.shape.Circle node = new javafx.scene.shape.Circle(5);
        node.getStyleClass().add(record.verified() ? "timeline-node" : "timeline-node-unverified");
        VBox.setMargin(node, new Insets(14, 0, 0, 0));
        Region connector = new Region();
        connector.getStyleClass().add("timeline-rail");
        VBox.setVgrow(connector, Priority.ALWAYS);
        rail.getChildren().add(node);
        if (!last) rail.getChildren().add(connector);

        VBox card = new VBox(4);
        card.getStyleClass().add("timeline-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        VBox.setMargin(card, new Insets(0, 0, 10, 0));

        Label date = new Label(record.donationDate() == null ? "Date unknown" : record.donationDate().format(DAY));
        date.getStyleClass().add("timeline-date");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label hospital = new Label(blankToDash(record.hospitalName()));
        hospital.getStyleClass().add("timeline-title");
        hospital.setWrapText(true);
        titleRow.getChildren().add(hospital);
        if (record.verified()) titleRow.getChildren().add(Icons.icon(Icons.SHIELD_CHECK, 15, "profile-icon"));

        Label meta = new Label((record.bloodGroup() == null ? "" : record.bloodGroup().getDisplayName() + " • ")
                + record.units() + (record.units() == 1 ? " unit" : " units")
                + (record.verified() ? "" : " • awaiting verification"));
        meta.getStyleClass().add("timeline-meta");

        card.getChildren().addAll(date, titleRow, meta);

        VBox cardWrapper = new VBox(card);
        HBox.setHgrow(cardWrapper, Priority.ALWAYS);
        row.getChildren().addAll(rail, cardWrapper);
        return row;
    }

    // --- Reviews ------------------------------------------------------------

    private static Node reviewSection(DonorProfileView profile) {
        VBox card = new VBox(12);
        card.getStyleClass().add("content-card");
        card.getChildren().add(sectionTitle(Icons.STAR, "Reviews"));

        if (profile.reviews().isEmpty()) {
            card.getChildren().add(emptyState("No reviews yet",
                    "Reviews can only be left after a verified, completed donation."));
            return card;
        }
        for (Review review : profile.reviews()) card.getChildren().add(reviewCard(review));
        return card;
    }

    private static Node reviewCard(Review review) {
        VBox card = new VBox(8);
        card.getStyleClass().add("review-card");

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        Label author = new Label(review.reviewerName() == null || review.reviewerName().isBlank()
                ? "BloodLink member" : review.reviewerName());
        author.getStyleClass().add("review-author");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label date = new Label(review.createdAt() == null ? "" : review.createdAt().format(DAY));
        date.getStyleClass().add("review-date");
        head.getChildren().addAll(author, new StarRating(review.rating()), spacer, date);
        card.getChildren().add(head);

        if (review.tags() != null && !review.tags().isEmpty()) {
            FlowPane tags = new FlowPane(6, 6);
            for (ReviewTag tag : review.tags()) {
                Label chip = new Label(tag.toString());
                chip.getStyleClass().addAll("chip", "chip-" + tag.name().toLowerCase(Locale.ROOT).replace('_', '-'));
                tags.getChildren().add(chip);
            }
            card.getChildren().add(tags);
        }

        if (review.comment() != null && !review.comment().isBlank()) {
            Label comment = new Label(review.comment());
            comment.getStyleClass().add("review-comment");
            comment.setWrapText(true);
            card.getChildren().add(comment);
        }
        return card;
    }

    // --- Shared bits --------------------------------------------------------

    private static Node sectionTitle(String iconPath, String text) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(text);
        label.getStyleClass().add("profile-section-title");
        row.getChildren().addAll(Icons.icon(iconPath, 16, "profile-icon"), label);
        return row;
    }

    /**
     * A drawn empty state rather than an italic "no results" line: the app's
     * teardrop motif, greyed, above a title and a sentence saying what would put
     * something here.
     */
    private static Node emptyState(String title, String hint) {
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);
        StackPane art = new StackPane(
                Icons.icon(Icons.DROPLET, 46, "empty-state-art"),
                Icons.pulseLine(30, "empty-state-pulse"));
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("empty-state-hint");
        hintLabel.setWrapText(true);
        hintLabel.setAlignment(Pos.CENTER);
        box.getChildren().addAll(art, titleLabel, hintLabel);
        return box;
    }

    private static Node loadingPlaceholder() {
        VBox box = new VBox(10);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);
        Label label = new Label("Loading profile…");
        label.getStyleClass().add("empty-state-title");
        box.getChildren().addAll(Icons.pulseLine(60, "pulse-line-muted"), label);
        return box;
    }

    private static Node message(String title, String detail) {
        VBox box = new VBox(8);
        box.getStyleClass().add("empty-state-box");
        box.setAlignment(Pos.CENTER);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");
        Label detailLabel = new Label(detail == null ? "" : detail);
        detailLabel.getStyleClass().add("empty-state-hint");
        detailLabel.setWrapText(true);
        box.getChildren().addAll(Icons.icon(Icons.X_CIRCLE, 40, "empty-state-art"), titleLabel, detailLabel);
        return box;
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }
}
