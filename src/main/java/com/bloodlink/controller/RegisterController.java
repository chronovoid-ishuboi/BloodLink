package com.bloodlink.controller;

import com.bloodlink.model.*;
import com.bloodlink.service.AuthService;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.LogoManager;
import com.bloodlink.util.NidScanDialog;
import com.bloodlink.util.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Stream;

public final class RegisterController {
    @FXML private ComboBox<Role> roleCombo;
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private TextField districtField;
    @FXML private TextArea addressArea;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private PasswordField nidNumberField;
    @FXML private TextField guardianNameField;
    @FXML private TextField guardianPhoneField;
    @FXML private VBox donorFields;
    @FXML private ComboBox<BloodGroup> bloodGroupCombo;
    @FXML private DatePicker birthDatePicker;
    @FXML private TextField weightField;
    @FXML private TextField heightField;
    @FXML private DatePicker lastDonationPicker;
    @FXML private TextField chronicConditionsField;
    @FXML private CheckBox recentSurgeryCheck;
    @FXML private TextField recentSurgeryDetailsField;
    @FXML private CheckBox recentTattooCheck;
    @FXML private TextField recentTattooDetailsField;
    @FXML private CheckBox currentMedicationsCheck;
    @FXML private TextField currentMedicationsDetailsField;
    @FXML private CheckBox recentIllnessCheck;
    @FXML private TextField recentIllnessDetailsField;
    @FXML private CheckBox recentPregnancyCheck;
    @FXML private TextField recentPregnancyDetailsField;
    @FXML private Label errorLabel;
    @FXML private Button createButton;
    @FXML private Button scanNidButton;
    
    @FXML private ImageView profilePhotoView;
    @FXML private Label profileInitialsLabel;
    @FXML private Label stepDot1;
    @FXML private Label stepDot2;
    @FXML private Label stepDot3;
    
    private byte[] profilePhotoBytes = null;

    private final AuthService authService = new AuthService();

    @FXML private ImageView appLogoView;

    @FXML private void initialize() {
        roleCombo.getItems().setAll(Role.DONOR, Role.REQUESTER);
        roleCombo.setValue(Role.DONOR);
        bloodGroupCombo.getItems().setAll(BloodGroup.values());
        errorLabel.setText("");
        LogoManager.applyLogo(appLogoView);
        roleCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateDonorFields());
        updateDonorFields();
        setupToggleField(recentSurgeryCheck, recentSurgeryDetailsField);
        setupToggleField(recentTattooCheck, recentTattooDetailsField);
        setupToggleField(currentMedicationsCheck, currentMedicationsDetailsField);
        setupToggleField(recentIllnessCheck, recentIllnessDetailsField);
        setupToggleField(recentPregnancyCheck, recentPregnancyDetailsField);
        setupStepIndicator();
    }

    /**
     * Lights the step dots as each section of the form is filled in. The form is
     * one long scroll rather than a wizard, so this is progress feedback on a
     * 237-line form, not navigation -- nothing is gated on it, and a user can
     * still fill the sections in any order.
     */
    private void setupStepIndicator() {
        List<TextInputControl> identity = List.of(fullNameField);
        List<TextInputControl> contact = List.of(emailField, phoneField, districtField, passwordField);
        List<TextInputControl> health = List.of(weightField);

        Runnable refresh = () -> {
            setStepDone(stepDot1, allFilled(identity) && birthDatePicker.getValue() != null);
            setStepDone(stepDot2, allFilled(contact));
            // Requesters have no health section, so step 3 is complete for them by definition.
            setStepDone(stepDot3, roleCombo.getValue() != Role.DONOR || allFilled(health));
        };

        Stream.of(identity, contact, health).flatMap(List::stream)
                .forEach(field -> field.textProperty().addListener((obs, old, value) -> refresh.run()));
        birthDatePicker.valueProperty().addListener((obs, old, value) -> refresh.run());
        roleCombo.valueProperty().addListener((obs, old, value) -> refresh.run());
        refresh.run();
    }

    private static boolean allFilled(List<TextInputControl> fields) {
        return fields.stream().allMatch(field -> field.getText() != null && !field.getText().isBlank());
    }

    private static void setStepDone(Label dot, boolean done) {
        dot.getStyleClass().removeAll("step-dot-active");
        if (done) dot.getStyleClass().add("step-dot-active");
    }
    
    private void setupToggleField(CheckBox checkBox, TextField detailField) {
        detailField.visibleProperty().bind(checkBox.selectedProperty());
        detailField.managedProperty().bind(checkBox.selectedProperty());
    }

    private void updateDonorFields() {
        boolean donor = roleCombo.getValue() == Role.DONOR;
        donorFields.setVisible(donor);
        donorFields.setManaged(donor);
    }

    /**
     * Registration is a database write, so this now runs off the JavaFX
     * Application Thread like every other DB-triggered action in the app --
     * the same fix applied to dashboard polling, just never carried back to
     * this screen until now.
     */
    @FXML private void createAccount() {
        Double weight = null;
        Double height = null;
        if (roleCombo.getValue() == Role.DONOR) {
            if (!weightField.getText().isBlank()) {
                try { weight = Double.parseDouble(weightField.getText().trim()); }
                catch (NumberFormatException e) { errorLabel.setText("Weight must be numeric."); return; }
            }
            if (!heightField.getText().isBlank()) {
                try { height = Double.parseDouble(heightField.getText().trim()); }
                catch (NumberFormatException e) { errorLabel.setText("Height must be numeric."); return; }
            }
        }
        RegistrationData data = new RegistrationData(roleCombo.getValue(), fullNameField.getText(), emailField.getText(),
                phoneField.getText(), districtField.getText(), addressArea.getText(), passwordField.getText(),
                nidNumberField.getText(), guardianNameField.getText(), guardianPhoneField.getText(),
                bloodGroupCombo.getValue(), birthDatePicker.getValue(), weight, lastDonationPicker.getValue(),
                height, chronicConditionsField.getText(),
                recentSurgeryCheck.isSelected(), recentSurgeryDetailsField.getText(),
                recentTattooCheck.isSelected(), recentTattooDetailsField.getText(),
                currentMedicationsCheck.isSelected(), currentMedicationsDetailsField.getText(),
                recentIllnessCheck.isSelected(), recentIllnessDetailsField.getText(),
                recentPregnancyCheck.isSelected(), recentPregnancyDetailsField.getText(),
                profilePhotoBytes);
        String confirmPassword = confirmPasswordField.getText();
        errorLabel.setText("");
        createButton.setDisable(true);
        BackgroundTasks.run(() -> authService.register(data, confirmPassword),
                result -> {
                    createButton.setDisable(false);
                    if (!result.success()) { errorLabel.setText(result.message()); return; }
                    AlertUtil.info("Account created", result.message());
                    SceneManager.showLogin();
                },
                error -> {
                    createButton.setDisable(false);
                    errorLabel.setText("Registration failed: " + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
                });
    }

    @FXML private void backToLogin() { SceneManager.showLogin(); }

    /**
     * Optional identity-registration assist, per the spec's required workflow:
     * upload -> OCR -> user reviews/edits -> user confirms -> only then does
     * anything touch a real form field. Nothing is auto-accepted and no
     * "verified" flag is ever set: this is a form-fill shortcut.
     * <p>
     * <b>Blood group is among the fields this pre-fills</b>, from the value the
     * user confirmed in the review dialog. The previous version of this comment
     * claimed it never did, which was simply not what the code below does --
     * worth correcting rather than leaving, because it is exactly the field where
     * a reader would want the documentation to be accurate. The review dialog
     * flags that field specifically, and the value stays editable on this form
     * afterwards; per {@link com.bloodlink.model.NidExtraction}, a scanned blood
     * group is never treated as proof of anything.
     * <p>
     * The scan itself runs off the JavaFX Application Thread, so this hands
     * {@link NidScanDialog} a callback rather than waiting on a return value.
     */
    @FXML private void scanNid() {
        NidScanDialog.show(scanNidButton.getScene().getWindow(), result -> {
            if (result.name() != null && !result.name().isBlank()) fullNameField.setText(result.name());
            if (result.birthDate() != null) birthDatePicker.setValue(result.birthDate());
            if (result.bloodGroup() != null) bloodGroupCombo.setValue(result.bloodGroup());
            if (result.address() != null && !result.address().isBlank()) addressArea.setText(result.address());
            if (result.nidNumber() != null && !result.nidNumber().isBlank()) nidNumberField.setText(result.nidNumber());
        });
    }

    @FXML private void uploadPhoto() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Profile Photo");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        java.io.File selectedFile = fileChooser.showOpenDialog(fullNameField.getScene().getWindow());
        if (selectedFile != null) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(selectedFile.toPath());
                if (bytes.length > 5 * 1024 * 1024) {
                    com.bloodlink.util.AlertUtil.error("File too large", "Profile photo must be smaller than 5 MB.");
                    return;
                }
                this.profilePhotoBytes = bytes;
                javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes));
                profilePhotoView.setImage(img);
                profilePhotoView.setVisible(true);
                profileInitialsLabel.setVisible(false);
            } catch (java.io.IOException e) {
                com.bloodlink.util.AlertUtil.error("Upload failed", "Could not read the selected image file.");
            }
        }
    }

    @FXML private void removePhoto() {
        this.profilePhotoBytes = null;
        profilePhotoView.setImage(null);
        profilePhotoView.setVisible(false);
        profileInitialsLabel.setText(fullNameField.getText().isBlank() ? "?" : fullNameField.getText().substring(0, 1).toUpperCase());
        profileInitialsLabel.setVisible(true);
    }
}
