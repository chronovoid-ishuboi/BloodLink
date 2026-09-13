package com.bloodlink.view.components;

import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.control.Label;
import javafx.geometry.Pos;

public class StarRating extends HBox {
    
    private static final String STAR_PATH = "M12,17.27L18.18,21L16.54,13.97L22,9.24L14.81,8.62L12,2L9.19,8.62L2,9.24L7.45,13.97L5.82,21L12,17.27Z";

    public StarRating(double rating) {
        this(rating, false);
    }
    
    public StarRating(double rating, boolean showNumber) {
        super(4); // spacing
        setAlignment(Pos.CENTER_LEFT);
        
        // Ensure rating is between 0 and 5
        rating = Math.max(0, Math.min(5, rating));
        
        for (int i = 1; i <= 5; i++) {
            SVGPath star = new SVGPath();
            star.setContent(STAR_PATH);
            
            // Very simple scaling for a 24x24 path to make it smaller
            star.setScaleX(0.7);
            star.setScaleY(0.7);
            
            // Determine if filled, half, or empty
            if (rating >= i) {
                star.setStyle("-fx-fill: #f5b041;"); // Solid yellow/orange
            } else if (rating >= i - 0.5) {
                // To do a true half star requires a clipping path or complex CSS.
                // For simplicity, we just use a slightly muted fill for half stars.
                star.setStyle("-fx-fill: #f8c471;");
            } else {
                star.setStyle("-fx-fill: #e1e9e7;"); // Empty/gray
            }
            
            getChildren().add(star);
        }
        
        if (showNumber) {
            Label ratingLabel = new Label(String.format("%.1f", rating));
            ratingLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #587176; -fx-font-weight: 700; -fx-padding: 0 0 0 4;");
            getChildren().add(ratingLabel);
        }
    }
}
