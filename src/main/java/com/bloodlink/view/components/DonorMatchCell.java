package com.bloodlink.view.components;

import com.bloodlink.model.DonorMatchView;
import com.bloodlink.util.PhotoCache;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

public class DonorMatchCell extends ListCell<DonorMatchView> {

    @Override
    protected void updateItem(DonorMatchView match, boolean empty) {
        super.updateItem(match, empty);
        
        if (empty || match == null) {
            setText(null);
            setGraphic(null);
            setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        } else {
            HBox card = new HBox(16);
            card.getStyleClass().add("content-card");
            card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e1e9e7; -fx-border-radius: 12; -fx-padding: 14; -fx-effect: dropshadow(gaussian, rgba(20,62,68,0.06), 10, 0.12, 0, 3); -fx-cursor: hand;");
            
            // Photo or Initials of requester
            Image photo = PhotoCache.getPhotoSync(match.requesterId());
            if (photo != null) {
                ImageView imageView = new ImageView(photo);
                imageView.setFitWidth(48);
                imageView.setFitHeight(48);
                imageView.setPreserveRatio(false);
                Circle clip = new Circle(24, 24, 24);
                imageView.setClip(clip);
                card.getChildren().add(imageView);
            } else {
                String initial = match.requesterName() != null && !match.requesterName().isEmpty() ? 
                        match.requesterName().substring(0, 1).toUpperCase() : "?";
                Label initials = new Label(initial);
                initials.setStyle("-fx-background-color: #dce9e6; -fx-text-fill: #164e54; -fx-font-size: 20px; -fx-font-weight: 800; -fx-alignment: center; -fx-background-radius: 50%; -fx-min-width: 48px; -fx-min-height: 48px;");
                card.getChildren().add(initials);
            }
            
            VBox infoBox = new VBox(4);
            
            Label nameLabel = new Label(match.requesterName() != null ? match.requesterName() : "Unknown Requester");
            nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #173f45;");
            
            Label detailsLabel = new Label(match.bloodGroup().name() + " • " + match.hospitalName() + " (" + match.district() + ")" +
                (match.distanceKm() != null ? String.format(" • %.1f km", match.distanceKm()) : ""));
            detailsLabel.setStyle("-fx-text-fill: #6f8387; -fx-font-size: 12px;");
            
            StarRating ratingBox = new StarRating(match.requesterRating() == null ? 0 : match.requesterRating(), true);
            
            infoBox.getChildren().addAll(nameLabel, detailsLabel, ratingBox);
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            VBox statusBox = new VBox(6);
            statusBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
            
            Label urgencyLabel = new Label(match.urgency().name());
            if (match.urgency().name().equals("CRITICAL")) {
                urgencyLabel.setStyle("-fx-text-fill: #d8414b; -fx-font-weight: 700; -fx-font-size: 11px;");
            } else {
                urgencyLabel.setStyle("-fx-text-fill: #b46b16; -fx-font-weight: 700; -fx-font-size: 11px;");
            }
            
            Label statusLabel = new Label(match.matchStatus().name());
            statusLabel.getStyleClass().addAll("pill-teal");
            
            statusBox.getChildren().addAll(statusLabel, urgencyLabel);
            
            card.getChildren().addAll(infoBox, spacer, statusBox);
            
            setGraphic(card);
            setStyle("-fx-background-color: transparent; -fx-padding: 0 0 10 0;");
        }
    }
}
