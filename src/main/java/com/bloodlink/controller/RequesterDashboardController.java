package com.bloodlink.controller;

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
import java.time.LocalDateTime;

public final class RequesterDashboardController {
    @FXML private Label welcomeLabel;
    @FXML private Label unreadLabel;
    @FXML private ComboBox<BloodGroup> bloodGroupCombo;
    @FXML private Spinner<Integer> unitsSpinner;
    @FXML private ComboBox<Urgency> urgencyCombo;
    @FXML private TextField hospitalField;
    @FXML private TextField requestDistrictField;
    @FXML private DatePicker deadlinePicker;
    @FXML private TextArea notesArea;
    @FXML private Label requestMessageLabel;

    @FXML private TableView<BloodRequest> requestTable;
    @FXML private TableColumn<BloodRequest, Long> requestIdColumn;
    @FXML private TableColumn<BloodRequest, BloodGroup> requestBloodColumn;
    @FXML private TableColumn<BloodRequest, Integer> unitsColumn;
    @FXML private TableColumn<BloodRequest, Urgency> urgencyColumn;
    @FXML private TableColumn<BloodRequest, String> hospitalColumn;
    @FXML private TableColumn<BloodRequest, String> districtColumn;
    @FXML private TableColumn<BloodRequest, LocalDate> deadlineColumn;
    @FXML private TableColumn<BloodRequest, RequestStatus> statusColumn;

    @FXML private TableView<MatchCandidate> matchTable;
    @FXML private TableColumn<MatchCandidate, String> donorNameColumn;
    @FXML private TableColumn<MatchCandidate, BloodGroup> donorBloodColumn;
    @FXML private TableColumn<MatchCandidate, String> donorDistrictColumn;
    @FXML private TableColumn<MatchCandidate, String> donorPhoneColumn;
    @FXML private TableColumn<MatchCandidate, BadgeTier> donorBadgeColumn;
    @FXML private TableColumn<MatchCandidate, Double> donorScoreColumn;
    @FXML private TableColumn<MatchCandidate, String> donorReasonColumn;

    @FXML private TableView<RequestStatusHistoryEntry> historyTable;
    @FXML private TableColumn<RequestStatusHistoryEntry, RequestStatus> historyFromColumn;
    @FXML private TableColumn<RequestStatusHistoryEntry, RequestStatus> historyToColumn;
    @FXML private TableColumn<RequestStatusHistoryEntry, String> historyActorColumn;
    @FXML private TableColumn<RequestStatusHistoryEntry, String> historyNoteColumn;
    @FXML private TableColumn<RequestStatusHistoryEntry, LocalDateTime> historyTimeColumn;

    @FXML private ListView<Notification> notificationList;
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField profileDistrictField;
    @FXML private TextArea addressArea;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label profileMessageLabel;

    private final RequestDAO requestDAO = new RequestDAO();
    private final RequestService requestService = new RequestService();
    private final MatchingService matchingService = new MatchingService();
    private final NotificationService notificationService = new NotificationService();
    private final ProfileService profileService = new ProfileService();
    private Requester requester;
    private Timeline refreshTimeline;

    @FXML private void initialize() {
        if (!(SessionManager.getInstance().getCurrentUser() instanceof Requester currentRequester)) {
            SceneManager.showLogin(); return;
        }
        requester = currentRequester;
        welcomeLabel.setText("Welcome, " + requester.getFullName());
        bloodGroupCombo.getItems().setAll(BloodGroup.values());
        urgencyCombo.getItems().setAll(Urgency.values());
        urgencyCombo.setValue(Urgency.URGENT);
        unitsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 1));
        requestDistrictField.setText(requester.getDistrict());
        deadlinePicker.setValue(LocalDate.now());
        configureTables();
        populateProfile();
        requestTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> loadMatches(newValue));
        notificationList.setOnMouseClicked(event -> markSelectedNotificationRead());
        refreshAll();
        int seconds = Math.max(5, AppConfig.getInt("ui.auto-refresh-seconds"));
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(seconds), event -> refreshAll()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void configureTables() {
        requestIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().id()));
        requestBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        unitsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().unitsNeeded()));
        urgencyColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().urgency()));
        hospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        districtColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        deadlineColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().deadline()));
        statusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().status()));
        donorNameColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().donorName()));
        donorBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        donorDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        donorPhoneColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().phone()));
        donorBadgeColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().badgeTier()));
        donorScoreColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().score()));
        donorReasonColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().reason()));
        historyFromColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().fromStatus()));
        historyToColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().toStatus()));
        historyActorColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(
                v.getValue().changedByName() == null ? "System" : v.getValue().changedByName()));
        historyNoteColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().note()));
        historyTimeColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().changedAt()));

        urgencyColumn.setCellFactory(ChipTableCells.forValues());
        statusColumn.setCellFactory(ChipTableCells.forValues());
        donorBadgeColumn.setCellFactory(ChipTableCells.forValues());
        historyFromColumn.setCellFactory(ChipTableCells.forValues());
        historyToColumn.setCellFactory(ChipTableCells.forValues());

        requestTable.setPlaceholder(emptyState("You have not submitted a blood request yet."));
        matchTable.setPlaceholder(emptyState("Select a request to view ranked donor matches."));
        historyTable.setPlaceholder(emptyState("Select a request to view its lifecycle history."));
        notificationList.setPlaceholder(emptyState("You have no notifications."));
    }

    private Label emptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        return label;
    }

    private void populateProfile() {
        nameField.setText(requester.getFullName()); phoneField.setText(requester.getPhone());
        profileDistrictField.setText(requester.getDistrict()); addressArea.setText(requester.getAddress());
    }

    @FXML private void createRequest() {
        ServiceResult<Long> result = requestService.create(requester.getId(), bloodGroupCombo.getValue(), unitsSpinner.getValue(),
                urgencyCombo.getValue(), hospitalField.getText(), requestDistrictField.getText(), deadlinePicker.getValue(), notesArea.getText());
        requestMessageLabel.setText(result.message());
        if (result.success()) {
            hospitalField.clear(); notesArea.clear();
            refreshAll();
            requestTable.getItems().stream().filter(r -> r.id() == result.data()).findFirst()
                    .ifPresent(r -> requestTable.getSelectionModel().select(r));
        }
    }

    @FXML private void rematchSelected() {
        BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No request selected", "Select a request first."); return; }
        if (!(selected.status() == RequestStatus.PENDING || selected.status() == RequestStatus.MATCHED
                || selected.status() == RequestStatus.DECLINED || selected.status() == RequestStatus.ESCALATED)) {
            AlertUtil.warning("Request cannot be rematched", "Only pending, matched, declined, or escalated requests can be matched again.");
            return;
        }
        ServiceResult<java.util.List<MatchCandidate>> result = matchingService.match(selected.id(), requester.getId());
        if (result.success()) AlertUtil.info("Matching complete", result.message()); else AlertUtil.error("Matching failed", result.message());
        refreshAll();
    }

    @FXML private void markFulfilled() {
        BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No request selected", "Select a request first."); return; }
        if (!AlertUtil.confirm("Confirm fulfillment", "Confirm that request #" + selected.id() + " was fulfilled?")) return;
        showResult(requestService.fulfill(selected.id(), requester.getId())); refreshAll();
    }

    @FXML private void cancelSelected() {
        BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No request selected", "Select a request first."); return; }
        if (!AlertUtil.confirm("Cancel request", "Cancel request #" + selected.id() + "?")) return;
        showResult(requestService.cancel(selected.id(), requester.getId())); refreshAll();
    }

    @FXML private void refreshAll() {
        try {
            Long selectedId = requestTable.getSelectionModel().getSelectedItem() == null ? null : requestTable.getSelectionModel().getSelectedItem().id();
            requestTable.setItems(FXCollections.observableArrayList(requestDAO.findByRequester(requester.getId())));
            if (selectedId != null) requestTable.getItems().stream().filter(r -> r.id() == selectedId).findFirst()
                    .ifPresent(r -> requestTable.getSelectionModel().select(r));
            notificationList.setItems(FXCollections.observableArrayList(notificationService.list(requester.getId())));
            unreadLabel.setText(String.valueOf(notificationService.unreadCount(requester.getId())));
        } catch (Exception e) { requestMessageLabel.setText("Refresh failed: " + e.getMessage()); }
    }

    private void loadMatches(BloodRequest request) {
        if (request == null) {
            matchTable.getItems().clear();
            historyTable.getItems().clear();
            return;
        }
        try {
            matchTable.setItems(FXCollections.observableArrayList(requestDAO.findMatchesForRequest(request.id())));
            historyTable.setItems(FXCollections.observableArrayList(requestDAO.findStatusHistory(request.id(), requester.getId())));
        } catch (SQLException e) {
            requestMessageLabel.setText("Could not load request details: " + e.getMessage());
        }
    }

    @FXML private void saveProfile() {
        ServiceResult<User> result = profileService.updateProfile(requester.getId(), nameField.getText(), phoneField.getText(),
                profileDistrictField.getText(), addressArea.getText());
        if (result.success()) {
            requester.setFullName(result.data().getFullName()); requester.setPhone(result.data().getPhone());
            requester.setDistrict(result.data().getDistrict()); requester.setAddress(result.data().getAddress());
            welcomeLabel.setText("Welcome, " + requester.getFullName());
        }
        profileMessageLabel.setText(result.message());
    }

    @FXML private void changePassword() {
        ServiceResult<Void> result = profileService.changePassword(requester.getId(), oldPasswordField.getText(),
                newPasswordField.getText(), confirmPasswordField.getText());
        profileMessageLabel.setText(result.message());
        if (result.success()) { oldPasswordField.clear(); newPasswordField.clear(); confirmPasswordField.clear(); }
    }

    @FXML private void markAllNotificationsRead() {
        try { notificationService.markAllRead(requester.getId()); refreshAll(); }
        catch (SQLException e) { AlertUtil.error("Notification error", e.getMessage()); }
    }

    private void markSelectedNotificationRead() {
        Notification selected = notificationList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.read()) return;
        try { notificationService.markRead(selected.id(), requester.getId()); refreshAll(); }
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
