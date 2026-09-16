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
import java.util.List;
import java.util.function.Consumer;

/**
 * The "upload NID -&gt; OCR -&gt; review/edit -&gt; confirm" workflow the spec
 * requires: OCR output is never silently trusted, always shown to the user
 * for correction before it touches any real form field. The photographed
 * file is read once (by {@link OcrService}) and never copied, stored, or
 * logged anywhere by this class or its caller.
 * <p>
 * The {@link OcrService} used here is swappable -- Gemini when an API key is
 * configured, otherwise local Tesseract -- without touching any caller of
 * {@link #show}.
 * <p>
 * <b>The OCR pass runs off the JavaFX Application Thread.</b> It previously ran
 * inline, which froze the entire UI for the duration: a multi-second native
 * Tesseract call, or a full network round-trip with two base64-encoded photos in
 * the request body. On a slow connection that is long enough for the window to
 * be reported as not responding. This is the same rule the rest of the app
 * already follows for database work via {@link BackgroundTasks}; the scan path
 * was simply never brought in line with it. Because the work is now
 * asynchronous, {@link #show} reports its result through a callback rather than
 * returning an {@code Optional}.
 */
public final class NidScanDialog {
    private static final GeminiOcrService geminiOcrService = new GeminiOcrService();
    private static final OcrService tesseractOcrService = new TesseractOcrService();

    private NidScanDialog() { }

    public record NidReviewResult(String name, LocalDate birthDate, com.bloodlink.model.BloodGroup bloodGroup,
                                  String address, String nidNumber) { }

    /**
     * Runs the scan-and-review flow.
     *
     * @param onConfirmed invoked on the JavaFX Application Thread with the values the
     *                     user confirmed. Not invoked at all if they cancel the file
     *                     picker or the review dialog, so partial or unconfirmed OCR
     *                     output can never reach a form field.
     */
    public static void show(Window owner, Consumer<NidReviewResult> onConfirmed) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select front and back photos of your NID card");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        List<File> files = chooser.showOpenMultipleDialog(owner);
        if (files == null || files.isEmpty()) return;

        OcrService ocrService = geminiOcrService.isConfigured() ? geminiOcrService : tesseractOcrService;

        Alert progress = progressDialog(owner);
        progress.show();

        BackgroundTasks.run(
                () -> ocrService.extract(files),
                extraction -> {
                    progress.close();
                    reviewDialog(extraction).showAndWait().ifPresent(onConfirmed);
                },
                error -> {
                    progress.close();
                    // Still open the review dialog: a failed scan must leave the user
                    // able to type their details in, never dead-end them.
                    reviewDialog(NidExtraction.failure("The scan could not be completed ("
                            + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())
                            + "). You can enter your details manually below."))
                            .showAndWait().ifPresent(onConfirmed);
                });
    }

    private static Alert progressDialog(Window owner) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Scanning NID");
        alert.setHeaderText("Reading your card…");
        ProgressIndicator spinner = new ProgressIndicator();
        VBox content = new VBox(12, spinner, new Label("This can take a few seconds."));
        content.setAlignment(javafx.geometry.Pos.CENTER);
        alert.getDialogPane().setContent(content);
        AlertUtil.applyTheme(alert.getDialogPane());
        return alert;
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

        // Blood group is the one field on this card where a misread is dangerous
        // rather than merely annoying, so it is called out explicitly. OCR is a
        // form-fill shortcut, never evidence of a blood group -- see NidExtraction.
        Label bloodGroupWarning = new Label(
                "Check this against your own records. A scanned blood group is never treated as verified.");
        bloodGroupWarning.setWrapText(true);
        bloodGroupWarning.getStyleClass().add("helper-text");

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
                new Label("Detected blood group (edit if wrong)"), bloodGroupCombo, bloodGroupWarning,
                new Label("Detected address (edit if wrong)"), addressField,
                nidLabel, statusLabel);
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Use This Information");
        AlertUtil.applyTheme(dialog.getDialogPane());

        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new NidReviewResult(nameField.getText(), dobPicker.getValue(), bloodGroupCombo.getValue(),
                        addressField.getText(), extraction.detectedNidNumber())
                : null);
        return dialog;
    }

}
