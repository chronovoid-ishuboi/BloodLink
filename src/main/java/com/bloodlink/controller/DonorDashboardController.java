package com.bloodlink.controller;

import com.bloodlink.dao.DonorDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.model.*;
import com.bloodlink.service.*;
import com.bloodlink.util.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.LocalDate;

public final class DonorDashboardController {
    @FXML private Label welcomeLabel;
    @FXML private Label bloodGroupLabel;
    @FXML private Label badgeLabel;
    @FXML private Label eligibilityLabel;
    @FXML private Label cooldownLabel;
    @FXML private ProgressBar cooldownProgress;
    @FXML private Label unreadLabel;
    @FXML private ComboBox<AvailabilityStatus> availabilityCombo;

    @FXML private TableView<DonorMatchView> matchTable;
    @FXML private TableColumn<DonorMatchView, Long> requestIdColumn;
    @FXML private TableColumn<DonorMatchView, BloodGroup> matchBloodColumn;
    @FXML private TableColumn<DonorMatchView, String> hospitalColumn;
    @FXML private TableColumn<DonorMatchView, String> matchDistrictColumn;
    @FXML private TableColumn<DonorMatchView, Urgency> urgencyColumn;
    @FXML private TableColumn<DonorMatchView, LocalDate> deadlineColumn;
    @FXML private TableColumn<DonorMatchView, RequestStatus> requestStatusColumn;
    @FXML private TableColumn<DonorMatchView, MatchStatus> matchStatusColumn;
    @FXML private TableColumn<DonorMatchView, Double> scoreColumn;

    @FXML private TableView<DonationRecord> donationTable;
    @FXML private TableColumn<DonationRecord, LocalDate> donationDateColumn;
    @FXML private TableColumn<DonationRecord, String> donationHospitalColumn;
    @FXML private TableColumn<DonationRecord, BloodGroup> donationBloodColumn;
    @FXML private TableColumn<DonationRecord, Integer> donationUnitsColumn;
    @FXML private TableColumn<DonationRecord, String> donationVerifiedColumn;

    @FXML private ListView<Notification> notificationList;

    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField districtField;
    @FXML private TextArea addressArea;
    @FXML private TextField weightField;
    @FXML private DatePicker lastDonationPicker;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label profileMessageLabel;

    private final DonorDAO donorDAO = new DonorDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final NotificationService notificationService = new NotificationService();
    private final DonorService donorService = new DonorService();
    private final RequestService requestService = new RequestService();
    private final ProfileService profileService = new ProfileService();
    private final EligibilityService eligibilityService = new EligibilityService();
    private Donor donor;
    private Timeline refreshTimeline;

    @FXML private void initialize() {
        if (!(SessionManager.getInstance().getCurrentUser() instanceof Donor currentDonor)) {
            SceneManager.showLogin(); return;
        }
        donor = currentDonor;
        configureTables();
        availabilityCombo.getItems().setAll(AvailabilityStatus.values());
        availabilityCombo.setValue(donor.getAvailabilityStatus());
        availabilityCombo.setOnAction(event -> updateAvailability());
        notificationList.setOnMouseClicked(event -> markSelectedNotificationRead());
        populateProfile();
        refreshAll();
        int seconds = Math.max(5, AppConfig.getInt("ui.auto-refresh-seconds"));
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(seconds), event -> refreshAll()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void configureTables() {
        requestIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().requestId()));
        matchBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        hospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        matchDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        urgencyColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().urgency()));
        deadlineColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().deadline()));
        requestStatusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().requestStatus()));
        matchStatusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().matchStatus()));
        scoreColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().score()));
        donationDateColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().donationDate()));
        donationHospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        donationBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        donationUnitsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().units()));
        donationVerifiedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().verified() ? "Verified" : "Pending"));

        urgencyColumn.setCellFactory(ChipTableCells.forValues());
        requestStatusColumn.setCellFactory(ChipTableCells.forValues());
        matchStatusColumn.setCellFactory(ChipTableCells.forValues());
        donationVerifiedColumn.setCellFactory(ChipTableCells.forValues());

        matchTable.setPlaceholder(emptyState("No matching emergency requests are waiting for you."));
        donationTable.setPlaceholder(emptyState("No verified donation history is available yet."));
        notificationList.setPlaceholder(emptyState("You have no notifications."));
    }

    private Label emptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        return label;
    }

    private void populateProfile() {
        welcomeLabel.setText("Welcome, " + donor.getFullName());
        bloodGroupLabel.setText(donor.getBloodGroup().toString());
        badgeLabel.setText(donor.getBadgeTier() + " donor");
        nameField.setText(donor.getFullName());
        phoneField.setText(donor.getPhone());
        districtField.setText(donor.getDistrict());
        addressArea.setText(donor.getAddress());
        weightField.setText(String.valueOf(donor.getWeightKg()));
        lastDonationPicker.setValue(donor.getLastDonationDate());
        updateEligibilityCard();
    }

    private void updateEligibilityCard() {
        EligibilityService.EligibilityResult result = eligibilityService.evaluate(donor);
        eligibilityLabel.setText(result.eligible() ? "READY" : "NOT ELIGIBLE");
        cooldownLabel.setText(result.reason());
        cooldownProgress.setProgress(result.cooldownDaysRemaining() == 0 ? 1.0 : 1.0 - result.cooldownDaysRemaining() / 56.0);
        eligibilityLabel.getStyleClass().removeAll("status-success", "status-warning");
        eligibilityLabel.getStyleClass().add(result.eligible() ? "status-success" : "status-warning");
    }

    @FXML private void refreshAll() {
        try {
            matchTable.setItems(FXCollections.observableArrayList(requestDAO.findMatchesForDonor(donor.getId())));
            donationTable.setItems(FXCollections.observableArrayList(donorDAO.findDonationHistory(donor.getId())));
            notificationList.setItems(FXCollections.observableArrayList(notificationService.list(donor.getId())));
            unreadLabel.setText(String.valueOf(notificationService.unreadCount(donor.getId())));
        } catch (Exception e) {
            profileMessageLabel.setText("Refresh failed: " + e.getMessage());
        }
    }

    @FXML private void acceptSelected() {
        DonorMatchView selected = matchTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (selected.matchStatus() != MatchStatus.NOTIFIED) { AlertUtil.warning("Already answered", "This match is no longer awaiting a response."); return; }
        if (!AlertUtil.confirm("Accept request", "Accept blood request #" + selected.requestId() + "?")) return;
        showResult(requestService.accept(selected.requestId(), donor.getId()));
        refreshAll();
    }

    @FXML private void declineSelected() {
        DonorMatchView selected = matchTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (!AlertUtil.confirm("Decline match", "Decline request #" + selected.requestId() + "?")) return;
        showResult(requestService.decline(selected.requestId(), donor.getId()));
        refreshAll();
    }

    private void updateAvailability() {
        AvailabilityStatus selected = availabilityCombo.getValue();
        if (selected == donor.getAvailabilityStatus()) return;
        ServiceResult<Void> result = donorService.updateAvailability(donor.getId(), selected);
        if (result.success()) donor.setAvailabilityStatus(selected);
        else AlertUtil.error("Update failed", result.message());
    }

    @FXML private void saveProfile() {
        ServiceResult<User> result = profileService.updateProfile(donor.getId(), nameField.getText(), phoneField.getText(),
                districtField.getText(), addressArea.getText());
        if (!result.success()) { profileMessageLabel.setText(result.message()); return; }
        donor.setFullName(result.data().getFullName()); donor.setPhone(result.data().getPhone());
        donor.setDistrict(result.data().getDistrict()); donor.setAddress(result.data().getAddress());
        profileMessageLabel.setText(result.message()); populateProfile();
    }

    @FXML private void saveHealth() {
        ServiceResult<Void> result = donorService.updateHealth(donor.getId(), weightField.getText(), lastDonationPicker.getValue());
        if (result.success()) {
            donor.setWeightKg(Double.parseDouble(weightField.getText().trim()));
            donor.setLastDonationDate(lastDonationPicker.getValue());
            updateEligibilityCard();
        }
        profileMessageLabel.setText(result.message());
    }

    @FXML private void changePassword() {
        ServiceResult<Void> result = profileService.changePassword(donor.getId(), oldPasswordField.getText(),
                newPasswordField.getText(), confirmPasswordField.getText());
        profileMessageLabel.setText(result.message());
        if (result.success()) { oldPasswordField.clear(); newPasswordField.clear(); confirmPasswordField.clear(); }
    }

    @FXML private void markAllNotificationsRead() {
        try { notificationService.markAllRead(donor.getId()); refreshAll(); }
        catch (SQLException e) { AlertUtil.error("Notification error", e.getMessage()); }
    }

    private void markSelectedNotificationRead() {
        Notification selected = notificationList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.read()) return;
        try { notificationService.markRead(selected.id(), donor.getId()); refreshAll(); }
        catch (SQLException e) { AlertUtil.error("Notification error", e.getMessage()); }
    }

    private void showResult(ServiceResult<Void> result) {
        if (result.success()) AlertUtil.info("Success", result.message()); else AlertUtil.error("Action failed", result.message());
    }

    @FXML private void logout() {
        if (refreshTimeline != null) refreshTimeline.stop();
        SceneManager.logout();
    }
}
