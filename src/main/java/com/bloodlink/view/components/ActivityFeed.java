package com.bloodlink.view.components;

import com.bloodlink.dao.AdminDAO;
import com.bloodlink.model.AuditEntry;
import com.bloodlink.dao.PagedResult;
import com.bloodlink.util.BackgroundTasks;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.LocalDateTime;

public class ActivityFeed extends ScrollPane {
    private final VBox feedContainer;
    private final AdminDAO adminDAO = new AdminDAO();
    private Timeline refreshTimeline;

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
        for (AuditEntry entry : result.items()) {
            VBox card = new VBox(5);
            card.getStyleClass().add("content-card");
            card.setPadding(new Insets(10));
            
            Text actorTxt = new Text((entry.actorName() == null ? "System" : entry.actorName()) + " ");
            actorTxt.setStyle("-fx-font-weight: bold; -fx-fill: -text-primary;");
            Text actionTxt = new Text(entry.action() + " ");
            actionTxt.setStyle("-fx-fill: -text-secondary;");
            Text detailTxt = new Text(entry.details());
            detailTxt.setStyle("-fx-fill: -text-primary;");
            
            TextFlow flow = new TextFlow(actorTxt, actionTxt, detailTxt);
            
            Label timeLabel = new Label(formatRelativeTime(entry.createdAt()));
            timeLabel.getStyleClass().add("helper-text");
            
            card.getChildren().addAll(flow, timeLabel);
            feedContainer.getChildren().add(card);
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
