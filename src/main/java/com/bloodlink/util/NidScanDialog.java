package com.bloodlink.util;

import com.bloodlink.model.NidExtraction;
import com.bloodlink.service.GeminiOcrService;
import com.bloodlink.service.OcrService;
import com.bloodlink.service.TesseractOcrService;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The "upload NID -&gt; OCR -&gt; review/edit -&gt; confirm" workflow the spec
 * requires: OCR output is never silently trusted, always shown to the user
 * for correction before it touches any real form field. The photographed
 * file is read once (by {@link OcrService}) and never copied, stored, or
 * logged anywhere by this class or its caller.
 * <p>
 * The {@link OcrService} used here is swappable -- change {@link #ocrService}
 * to a different implementation without touching any caller of {@link #show}.
 */
public final class NidScanDialog {
    private static final GeminiOcrService geminiOcrService = new GeminiOcrService();
    private static final OcrService tesseractOcrService = new TesseractOcrService();

    private NidScanDialog() { }

    public record NidReviewResult(String name, LocalDate birthDate, com.bloodlink.model.BloodGroup bloodGroup, String address, String nidNumber) { }

    /** Returns empty if the user cancels the file picker or the review dialog -- never partial/unconfirmed data. */
    public static Optional<NidReviewResult> show(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select front and back photos of your NID card");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        java.util.List<File> files = chooser.showOpenMultipleDialog(owner);
        if (files == null || files.isEmpty()) return Optional.empty();

        OcrService ocrService = geminiOcrService.isConfigured() ? geminiOcrService : tesseractOcrService;
        NidExtraction extraction = ocrService.extract(files);
        return reviewDialog(extraction).showAndWait();
    }

    private static Dialog<NidReviewResult> reviewDialog(NidExtraction extraction) {
        Dialog<NidReviewResult> dialog = new Dialog<>();
        dialog.setTitle("Review detected information");
        dialog.setHeaderText(extraction.success()
                ? "Check the information below before using it -- OCR can make mistakes."
                : "Automatic detection didn't work this time.");

        TextField nameField = new TextField(extraction.detectedName() == null ? "" : extraction.detectedName());
        nameField.setPromptText("Full name");
        DatePicker dobPicker = new DatePicker(extraction.detectedBirthDate());
        
        ComboBox<com.bloodlink.model.BloodGroup> bloodGroupCombo = new ComboBox<>();
        bloodGroupCombo.getItems().setAll(com.bloodlink.model.BloodGroup.values());
        if (extraction.detectedBloodGroup() != null) {
            bloodGroupCombo.setValue(extraction.detectedBloodGroup());
        }
        
        TextField addressField = new TextField(extraction.detectedAddress() == null ? "" : extraction.detectedAddress());
        addressField.setPromptText("Address");

        Label nidLabel = new Label(extraction.detectedNidNumberMasked() == null
                ? "NID number: not detected"
                : "NID number on card: " + extraction.detectedNidNumberMasked());
        nidLabel.setWrapText(true);

        Label statusLabel = new Label(extraction.success() ? "" : extraction.failureReason());
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add(extraction.success() ? "helper-text" : "error-text");

        VBox content = new VBox(10,
                new Label("Detected name (edit if wrong)"), nameField,
                new Label("Detected date of birth (edit if wrong)"), dobPicker,
                new Label("Detected blood group (edit if wrong)"), bloodGroupCombo,
                new Label("Detected address (edit if wrong)"), addressField,
                nidLabel, statusLabel);
        content.setPrefWidth(400);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Use This Information");

        dialog.setResultConverter(button ->
                button == ButtonType.OK ? new NidReviewResult(nameField.getText(), dobPicker.getValue(), bloodGroupCombo.getValue(), addressField.getText(), extraction.detectedNidNumber()) : null);
        return dialog;
    }
}
