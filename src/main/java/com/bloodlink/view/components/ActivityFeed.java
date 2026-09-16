package com.bloodlink.view.components;

import com.bloodlink.dao.AdminDAO;
import com.bloodlink.model.AuditEntry;
import com.bloodlink.dao.PagedResult;
import com.bloodlink.util.BackgroundTasks;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import com.bloodlink.util.Icons;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.LocalDateTime;

public class ActivityFeed extends ScrollPane {
    private final VBox feedContainer;
    private final AdminDAO adminDAO = new AdminDAO();
    private final Timeline refreshTimeline;

    public ActivityFeed() {
        this.setFitToWidth(true);
        this.setPrefViewportHeight(200);
        this.getStyleClass().add("transparent-scroll");

        feedContainer = new VBox(10);
        feedContainer.setPadding(new Insets(10));
        this.setContent(feedContainer);

        // Auto-refresh every 15 seconds
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(15), e -> refreshFeed()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
        
        refreshFeed();
    }

    /**
     * Stops the 15-second refresh loop.
     * <p>
     * The timeline runs INDEFINITE and nothing used to stop it: on logout the
     * admin controller stopped its own poller but this one kept querying the
     * audit log forever, and a second admin session would start another
     * alongside it. The {@code fx:id} on this node in the FXML existed but had no
     * matching controller field, so there was no handle to call this on -- that
     * field is now present, and logout calls this.
     */
    public void stop() {
        refreshTimeline.stop();
    }

    private void refreshFeed() {
        BackgroundTasks.run(() -> {
            try {
                return adminDAO.auditEntries(1);
            } catch (SQLException ex) {
                return null;
            }
        }, data -> {
            if (data != null) {
                updateView(data);
            }
        }, error -> {});
    }

    private void updateView(PagedResult<AuditEntry> result) {
        feedContainer.getChildren().clear();
        if (result.items().isEmpty()) {
            feedContainer.getChildren().add(EmptyState.of("No activity yet",
                    "Approvals, matches and confirmations show up here as they happen."));
            return;
        }
        for (AuditEntry entry : result.items()) {
            feedContainer.getChildren().add(entryCard(entry));
        }
    }

    private HBox entryCard(AuditEntry entry) {
        HBox card = new HBox(12);
        card.getStyleClass().add("feed-card");
        card.setAlignment(Pos.CENTER_LEFT);

        EventKind kind = EventKind.of(entry.action());
        StackPane well = new StackPane(Icons.icon(kind.iconPath, 15, "feed-icon" + kind.suffix));
        well.getStyleClass().addAll("feed-icon-well", "feed-icon-well" + kind.suffix);
        card.getChildren().add(well);

        VBox body = new VBox(4);
        HBox.setHgrow(body, Priority.ALWAYS);

        // Style classes rather than inline -fx-fill: these previously referenced
        // -text-primary/-text-secondary, which were not defined anywhere, so the
        // colours silently fell back. They are real tokens in theme.css now.
        Text actorTxt = new Text((entry.actorName() == null ? "System" : entry.actorName()) + " ");
        actorTxt.getStyleClass().add("feed-actor");
        Text actionTxt = new Text(prettify(entry.action()) + " ");
        actionTxt.getStyleClass().add("feed-action");
        Text detailTxt = new Text(entry.details() == null ? "" : entry.details());
        detailTxt.getStyleClass().add("feed-detail");

        Label timeLabel = new Label(formatRelativeTime(entry.createdAt()));
        timeLabel.getStyleClass().add("helper-text");

        body.getChildren().addAll(new TextFlow(actorTxt, actionTxt, detailTxt), timeLabel);
        card.getChildren().add(body);
        return card;
    }

    /** APPROVE_USER -> "approved user", so the feed reads as a sentence. */
    private static String prettify(String action) {
        if (action == null) return "";
        return action.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    /**
     * Maps an audit action onto an icon and a semantic colour. Matching on
     * substrings rather than an exhaustive list keeps a newly added audit action
     * rendering sensibly (as a neutral event) instead of crashing or blanking.
     */
    private enum EventKind {
        APPROVED(Icons.CHECK_CIRCLE, "-success"),
        REJECTED(Icons.X_CIRCLE, "-danger"),
        MATCHED(Icons.DROPLET, "-info"),
        PENDING(Icons.CLOCK, "-pending"),
        NEUTRAL(Icons.LIST, "");

        final String iconPath;
        final String suffix;

        EventKind(String iconPath, String suffix) {
            this.iconPath = iconPath;
            this.suffix = suffix;
        }

        static EventKind of(String action) {
            if (action == null) return NEUTRAL;
            String value = action.toUpperCase(java.util.Locale.ROOT);
            if (value.contains("APPROVE") || value.contains("ACTIVATE") || value.contains("CONFIRM")
                    || value.contains("FULFIL")) return APPROVED;
            if (value.contains("SUSPEND") || value.contains("CANCEL") || value.contains("DECLINE")
                    || value.contains("REJECT") || value.contains("CLOSE")) return REJECTED;
            if (value.contains("MATCH") || value.contains("REQUEST") || value.contains("DONAT")) return MATCHED;
            if (value.contains("ESCALAT") || value.contains("NOTIF") || value.contains("RESET")) return PENDING;
            return NEUTRAL;
        }
    }
    
    private String formatRelativeTime(LocalDateTime time) {
        if (time == null) return "Unknown";
        java.time.Duration diff = java.time.Duration.between(time, LocalDateTime.now());
        long days = diff.toDays();
        long hours = diff.toHours();
        long mins = diff.toMinutes();
        if (days > 0) return days + "d ago";
        if (hours > 0) return hours + "h ago";
        if (mins > 0) return mins + "m ago";
        return "Just now";
    }
}
