package com.bloodlink.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.SVGPath;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class LogoManager {
    private static final String LOGO_PATH = "bloodlink_logo.png";
    private static Image currentLogo = null;
    
    // Maintain a list of ImageViews to update them dynamically if the logo changes
    private static final List<ImageView> registeredViews = new ArrayList<>();
    
    private LogoManager() {}
    
    static {
        loadLogo();
    }
    
    private static void loadLogo() {
        File file = new File(LOGO_PATH);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                currentLogo = new Image(fis);
            } catch (IOException e) {
                e.printStackTrace();
                currentLogo = null;
            }
        } else {
            currentLogo = null;
        }
    }
    
    public static void applyLogo(ImageView imageView) {
        if (!registeredViews.contains(imageView)) {
            registeredViews.add(imageView);
        }
        if (currentLogo != null) {
            imageView.setImage(currentLogo);
            imageView.setVisible(true);
        } else {
            // Default SVG is usually handled by keeping the SVG visible in FXML
            // For now, if we have a custom logo, we hide the SVG.
            // Since we pass the ImageView, we assume it's part of a StackPane where SVG is the other child.
            imageView.setImage(null);
            imageView.setVisible(false);
        }
        
        // Find the parent StackPane to toggle the SVGPath visibility if present
        if (imageView.getParent() instanceof StackPane stack) {
            for (javafx.scene.Node child : stack.getChildren()) {
                if (child instanceof SVGPath svgPath) {
                    svgPath.setVisible(currentLogo == null);
                }
            }
        }
    }
    
    public static void updateLogo(File imageFile) throws IOException {
        File dest = new File(LOGO_PATH);
        if (imageFile == null) {
            if (dest.exists()) {
                dest.delete();
            }
            currentLogo = null;
        } else {
            Files.copy(imageFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try (FileInputStream fis = new FileInputStream(dest)) {
                currentLogo = new Image(fis);
            }
        }
        
        // Notify all registered ImageViews
        for (ImageView view : registeredViews) {
            applyLogo(view);
        }
    }
}
