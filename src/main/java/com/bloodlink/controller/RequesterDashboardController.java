package com.bloodlink.controller;

import com.bloodlink.dao.HospitalDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.model.*;
import com.bloodlink.service.*;
import com.bloodlink.util.*;
import com.bloodlink.util.LogoManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import com.bloodlink.util.GeoIPService;

public final class RequesterDashboardController {
    @FXML private ImageView appLogoView;
    @FXML private Label welcomeLabel;
    @FXML private Label unreadLabel;
    @FXML private ComboBox<BloodGroup> bloodGroupCombo;
    @FXML private Spinner<Integer> unitsSpinner;
    @FXML private ComboBox<Urgency> urgencyCombo;
    @FXML private ComboBox<Hospital> hospitalCombo;
    @FXML private TextField requestDistrictField;
    @FXML private DatePicker deadlinePicker;
    @FXML private TextArea notesArea;
    @FXML private Label requestMessageLabel;

    @FXML private TableView<BloodRequest> requestTable;
    @FXML private TableColumn<BloodRequest, Long> requestIdColumn;
    @FXML private TableColumn<BloodRequest, BloodGroup> requestBloodColumn;
    @FXML private TableColumn<BloodRequest, Integer> unitsColumn;
    @FXML private TableColumn<BloodRequest, String> progressColumn;
    @FXML private TableColumn<BloodRequest, Urgency> urgencyColumn;
    @FXML private TableColumn<BloodRequest, String> hospitalColumn;
    @FXML private TableColumn<BloodRequest, String> districtColumn;
    @FXML private TableColumn<BloodRequest, LocalDate> deadlineColumn;
    @FXML private TableColumn<BloodRequest, RequestStatus> statusColumn;

    @FXML private ListView<MatchCandidate> matchList;
    @FXML private WebView mapView;
    
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
    @FXML private ImageView headerProfilePhotoView;
    @FXML private javafx.scene.image.ImageView profilePhotoView;
    @FXML private Label profileInitialsLabel;
    @FXML private Button uploadPhotoButton;
    @FXML private Button removePhotoButton;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label profileMessageLabel;
    @FXML private Label nidLabel;
    @FXML private Label emailLabel;
    @FXML private TextField guardianNameField;
    @FXML private TextField guardianPhoneField;

    private final RequestDAO requestDAO = new RequestDAO();
    private final HospitalDAO hospitalDAO = new HospitalDAO();
    private final RequestService requestService = new RequestService();
    private final MatchingService matchingService = new MatchingService();
    private final NotificationService notificationService = new NotificationService();
    private final ProfileService profileService = new ProfileService();
    private final ReviewService reviewService = new ReviewService();
    private Requester requester;
    private Timeline refreshTimeline;
    private boolean suppressHospitalSearch = false;
    private volatile boolean refreshInFlight = false;

    @FXML private void initialize() {
        if (!(SessionManager.getInstance().getCurrentUser() instanceof Requester currentRequester)) {
            throw new IllegalStateException("RequesterDashboardController loaded without an active Requester session.");
        }
        this.requester = currentRequester;
        welcomeLabel.setText(requester.getFullName());
        LogoManager.applyLogo(appLogoView);
        new ProfileService().loadPhoto(requester.getId()).ifPresent(bytes -> {
            try {
                headerProfilePhotoView.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)));
                profilePhotoView.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)));
            } catch (Exception ignored) {}
        });
        configureTables();
        PushClient.getInstance().connect(requester.getId());
        PushClient.getInstance().onRefresh(this::refreshAll);
        bloodGroupCombo.getItems().setAll(BloodGroup.values());
        urgencyCombo.getItems().setAll(Urgency.values());
        urgencyCombo.setValue(Urgency.URGENT);
        unitsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 1));
        requestDistrictField.setText(requester.getDistrict());
        deadlinePicker.setValue(LocalDate.now());
        configureHospitalPicker();
        configureTables();
        populateProfile();
        requestTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> loadMatches(newValue));
        notificationList.setOnMouseClicked(event -> markSelectedNotificationRead());
        refreshAll();
        int seconds = Math.max(5, AppConfig.getInt("ui.auto-refresh-seconds"));
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(seconds), event -> refreshAll()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
        
        initializeMap();
    }

    private void initializeMap() {
        if (mapView != null) {
            java.net.URL mapUrl = getClass().getResource("/com/bloodlink/view/map.html");
            if (mapUrl != null) {
                mapView.getEngine().load(mapUrl.toExternalForm());
                mapView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                        updateMapMarkers();
                    }
                });
                matchList.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                    updateMapMarkers();
                });
            }
        }
    }

    private void updateMapMarkers() {
        if (mapView == null || mapView.getEngine().getLoadWorker().getState() != javafx.concurrent.Worker.State.SUCCEEDED) return;
        mapView.getEngine().executeScript("clearMarkers();");
        MatchCandidate selected = matchList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.donorName() != null) {
            // For donor location, we use their district for now.
            // Since we don't have lat/lng in DB for donors, we can just center on Bangladesh
            // and log the match. A better approach would be geocoding the district.
            double lat = 23.8103, lng = 90.4125; 
            
            String title = selected.donorName().replace("'", "\\'");
            String script = String.format("addMarker(%f, %f, '%s', '%s', '%s'); fitBounds();", 
                                lat, lng, title, selected.district(), "Donor");
            mapView.getEngine().executeScript(script);
            mapView.getEngine().executeScript(String.format("setView(%f, %f, 13);", lat, lng));
        }
    }

    @FXML private void detectArea() {
        new Thread(() -> {
            Optional<GeoIPService.GeoLocation> locOpt = GeoIPService.detectLocation();
            javafx.application.Platform.runLater(() -> {
                if (locOpt.isPresent()) {
                    GeoIPService.GeoLocation loc = locOpt.get();
                    if (mapView != null && mapView.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
                        String script = String.format("addMarker(%f, %f, 'My Area', '%s', 'Requester'); setView(%f, %f, 12);",
                            loc.lat(), loc.lon(), loc.city().replace("'", "\\'"), loc.lat(), loc.lon());
                        mapView.getEngine().executeScript(script);
                    }
                    com.bloodlink.util.AlertUtil.info("Area Detected", "Detected location: " + loc.city() + ", " + loc.region());
                } else {
                    com.bloodlink.util.AlertUtil.error("Detection Failed", "Could not detect area from IP.");
                }
            });
        }).start();
    }


    /**
     * Searchable hospital picker: an editable ComboBox backed by HospitalDAO.search().
     * Selecting a real entry links the request to a curated hospital (enables real
     * distance matching); typing a hospital that is not in the directory is still
     * accepted as free text, matching the previous behavior, but that request will
     * show "distance unavailable" until it is later matched to a known hospital.
     */
    private void configureHospitalPicker() {
        hospitalCombo.setEditable(true);
        hospitalCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Hospital hospital) { return hospital == null ? "" : hospital.name(); }
            @Override public Hospital fromString(String text) { return null; }
        });
        searchHospitals("");
        hospitalCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (suppressHospitalSearch) return;
            searchHospitals(newText);
        });
        hospitalCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) return;
            suppressHospitalSearch = true;
            hospitalCombo.getEditor().setText(newValue.name());
            requestDistrictField.setText(newValue.district());
            suppressHospitalSearch = false;
        });
    }

    private void searchHospitals(String query) {
        try {
            hospitalCombo.getItems().setAll(hospitalDAO.search(query, 15));
        } catch (SQLException e) {
            // Search-as-you-type failure should not block the requester from typing a hospital name manually.
        }
    }

    private void configureTables() {
        requestIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().id()));
        requestBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        unitsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().unitsNeeded()));
        progressColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().unitsFulfilled() + " / " + v.getValue().unitsNeeded() + " units"));
        urgencyColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().urgency()));
        hospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        districtColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        deadlineColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().deadline()));
        statusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().status()));
        historyFromColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().fromStatus()));
        historyToColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().toStatus()));
        historyActorColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(
                v.getValue().changedByName() == null ? "System" : v.getValue().changedByName()));
        historyNoteColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().note()));
        historyTimeColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().changedAt()));

        urgencyColumn.setCellFactory(ChipTableCells.forValues());
        statusColumn.setCellFactory(ChipTableCells.forValues());
        historyFromColumn.setCellFactory(ChipTableCells.forValues());
        historyToColumn.setCellFactory(ChipTableCells.forValues());

        requestTable.setPlaceholder(emptyState("You have not submitted a blood request yet."));
        matchList.setPlaceholder(emptyState("Select a request to view ranked donor matches."));
        matchList.setCellFactory(lv -> new com.bloodlink.view.components.RequesterMatchCell());
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
        nidLabel.setText(requester.getNidNumber() != null ? requester.getNidNumber() : "Not Provided");
        emailLabel.setText(requester.getEmail() != null ? requester.getEmail() : "Not Provided");
        guardianNameField.setText(requester.getGuardianName());
        guardianPhoneField.setText(requester.getGuardianPhone());
        applyProfilePhoto();
    }

    /**
     * Same pattern as DonorDashboardController's version: loaded via the dedicated
     * ProfileService.loadPhoto(), never as part of the routine session fetch. Falls
     * back to an initials badge (no image asset needed) when no photo is set.
     */
    private void applyProfilePhoto() {
        java.util.Optional<byte[]> photo = profileService.loadPhoto(requester.getId());
        if (photo.isPresent()) {
            try {
                headerProfilePhotoView.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(photo.get())));
                profilePhotoView.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(photo.get())));
                profilePhotoView.setClip(new javafx.scene.shape.Circle(42, 42, 42));
                profilePhotoView.setVisible(true);
                profilePhotoView.setManaged(true);
                profileInitialsLabel.setVisible(false);
                profileInitialsLabel.setManaged(false);
                return;
            } catch (RuntimeException e) {
                // Stored bytes weren't a decodable image -- fall through to the initials badge below.
            }
        }
        headerProfilePhotoView.setImage(null);
        profilePhotoView.setImage(null);
        profilePhotoView.setVisible(false);
        profilePhotoView.setManaged(false);
        profileInitialsLabel.setVisible(true);
        profileInitialsLabel.setManaged(true);
        profileInitialsLabel.setText(initialsOf(requester.getFullName()));
    }

    private String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    @FXML private void uploadPhoto() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select a profile photo");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        java.io.File file = chooser.showOpenDialog(uploadPhotoButton.getScene().getWindow());
        if (file == null) return;
        BackgroundTasks.run(
                () -> profileService.updatePhoto(requester.getId(), java.nio.file.Files.readAllBytes(file.toPath())),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Could not read that file: " + error.getMessage()));
    }

    @FXML private void removePhoto() {
        BackgroundTasks.run(
                () -> profileService.updatePhoto(requester.getId(), null),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Photo could not be removed: " + error.getMessage()));
    }

    @FXML private void createRequest() {
        String typedHospitalName = hospitalCombo.getEditor().getText();
        Hospital selectedHospital = hospitalCombo.getValue();
        Long hospitalId = (selectedHospital != null && selectedHospital.name().equals(typedHospitalName))
                ? selectedHospital.id() : null;
        ServiceResult<Long> result = requestService.create(requester.getId(), bloodGroupCombo.getValue(), unitsSpinner.getValue(),
                urgencyCombo.getValue(), typedHospitalName, hospitalId, requestDistrictField.getText(), deadlinePicker.getValue(), notesArea.getText());
        requestMessageLabel.setText(result.message());
        if (result.success()) {
            hospitalCombo.getEditor().clear();
            hospitalCombo.setValue(null);
            notesArea.clear();
            refreshAll();
            requestTable.getItems().stream().filter(r -> r.id() == result.data()).findFirst()
                    .ifPresent(r -> requestTable.getSelectionModel().select(r));
        }
    }

    @FXML private void rematchSelected() {
        BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No request selected", "Select a request first."); return; }
        if (!(selected.status() == RequestStatus.PENDING || selected.status() == RequestStatus.MATCHED
                || selected.status() == RequestStatus.DECLINED || selected.status() == RequestStatus.ESCALATED
                || selected.status() == RequestStatus.ACCEPTED || selected.status() == RequestStatus.PARTIALLY_FULFILLED)) {
            AlertUtil.warning("Request cannot be rematched", "This request is already fulfilled or cancelled.");
            return;
        }
        ServiceResult<java.util.List<MatchCandidate>> result = matchingService.match(selected.id(), requester.getId());
        if (result.success()) AlertUtil.info("Matching complete", result.message()); else AlertUtil.error("Matching failed", result.message());
        refreshAll();
    }

    /**
     * A request can need several donors now, so confirmation happens per donor: select
     * the specific ACCEPTED row in the matched-donors table, not just the request.
     */
    @FXML private void confirmReceived() {
        BloodRequest selectedRequest = requestTable.getSelectionModel().getSelectedItem();
        MatchCandidate selectedDonor = matchList.getSelectionModel().getSelectedItem();
        if (selectedRequest == null || selectedDonor == null) {
            AlertUtil.warning("No donor selected", "Select the specific donor row (in Matched Donors) you want to confirm.");
            return;
        }
        if (selectedDonor.matchStatus() != MatchStatus.ACCEPTED) {
            AlertUtil.warning("Not awaiting confirmation", "This donor has not accepted, or is no longer awaiting confirmation.");
            return;
        }
        if (selectedDonor.requesterConfirmed()) {
            AlertUtil.info("Already confirmed", "You already confirmed this donor's donation. Waiting on their side.");
            return;
        }
        if (!AlertUtil.confirm("Confirm receipt", "Confirm that you received the donation from " + selectedDonor.donorName() + "?")) return;
        showResult(requestService.confirmReceived(selectedRequest.id(), requester.getId(), selectedDonor.donorId()));
        refreshAll();
    }

    /**
     * Reviewable per donor once THAT donor's handshake is fully confirmed -- not gated
     * on the whole request being FULFILLED, since other donors on the same request may
     * still be in progress. ReviewService independently enforces the same rule.
     */
    @FXML private void rateDonor() {
        BloodRequest selectedRequest = requestTable.getSelectionModel().getSelectedItem();
        MatchCandidate selectedDonor = matchList.getSelectionModel().getSelectedItem();
        if (selectedRequest == null || selectedDonor == null) {
            AlertUtil.warning("No donor selected", "Select the specific donor row (in Matched Donors) you want to rate.");
            return;
        }
        if (!(selectedDonor.donorConfirmed() && selectedDonor.requesterConfirmed())) {
            AlertUtil.warning("Not yet reviewable", "This donor's donation is not a verified completed donation yet.");
            return;
        }
        if (reviewService.hasReviewed(selectedRequest.id(), requester.getId())) {
            AlertUtil.info("Already reviewed", "You already submitted a review for this request.");
            return;
        }
        ReviewDialog.show("Rate donor", selectedDonor.donorName()).ifPresent(input -> {
            ServiceResult<Void> result = reviewService.submit(selectedRequest.id(), requester.getId(), input.rating(), input.tags(), input.comment());
            showResult(result);
        });
    }

    @FXML private void cancelSelected() {
        BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No request selected", "Select a request first."); return; }
        if (!AlertUtil.confirm("Cancel request", "Cancel request #" + selected.id() + "?")) return;
        showResult(requestService.cancel(selected.id(), requester.getId())); refreshAll();
    }

    /**
     * Runs the dashboard's periodic refresh off the JavaFX Application Thread.
     * {@code refreshInFlight} skips a tick rather than queuing another background
     * fetch if the previous one hasn't finished yet (e.g. a slow connection).
     */
    @FXML private void refreshAll() {
        if (refreshInFlight) return;
        refreshInFlight = true;
        BackgroundTasks.run(this::loadDashboardData,
                data -> { applyDashboardData(data); refreshInFlight = false; },
                error -> { requestMessageLabel.setText("Refresh failed: " + error.getMessage()); refreshInFlight = false; });
    }

    private RequesterDashboardData loadDashboardData() throws SQLException {
        return new RequesterDashboardData(
                requestDAO.findByRequester(requester.getId()),
                notificationService.list(requester.getId()),
                notificationService.unreadCount(requester.getId()));
    }

    private void applyDashboardData(RequesterDashboardData data) {
        Long selectedId = requestTable.getSelectionModel().getSelectedItem() == null ? null : requestTable.getSelectionModel().getSelectedItem().id();
        requestTable.setItems(FXCollections.observableArrayList(data.requests()));
        if (selectedId != null) requestTable.getItems().stream().filter(r -> r.id() == selectedId).findFirst()
                .ifPresent(r -> requestTable.getSelectionModel().select(r));
        notificationList.setItems(FXCollections.observableArrayList(data.notifications()));
        unreadLabel.setText(String.valueOf(data.unreadCount()));
    }

    private record RequesterDashboardData(java.util.List<BloodRequest> requests, java.util.List<Notification> notifications, long unreadCount) { }

    private void loadMatches(BloodRequest request) {
        if (request == null) {
            matchList.getItems().clear();
            historyTable.getItems().clear();
            return;
        }
        long requestId = request.id();
        BackgroundTasks.run(() -> new MatchDetails(requestDAO.findMatchesForRequest(requestId),
                        requestDAO.findStatusHistory(requestId, requester.getId())),
                details -> {
                    matchList.setItems(FXCollections.observableArrayList(details.matches()));
                    historyTable.setItems(FXCollections.observableArrayList(details.history()));
                },
                error -> requestMessageLabel.setText("Could not load request details: " + error.getMessage()));
    }

    private record MatchDetails(java.util.List<MatchCandidate> matches, java.util.List<RequestStatusHistoryEntry> history) { }

    @FXML private void saveProfile() {
        ServiceResult<User> result = profileService.updateProfile(requester.getId(), nameField.getText(), phoneField.getText(),
                profileDistrictField.getText(), addressArea.getText(), guardianNameField.getText(), guardianPhoneField.getText());
        if (result.success()) {
            requester.setFullName(result.data().getFullName()); requester.setPhone(result.data().getPhone());
            requester.setDistrict(result.data().getDistrict()); requester.setAddress(result.data().getAddress());
            requester.setGuardianName(result.data().getGuardianName()); requester.setGuardianPhone(result.data().getGuardianPhone());
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
        PushClient.getInstance().disconnect();
        SceneManager.logout();
    }

    @FXML private void changeProfilePhoto() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        java.io.File file = chooser.showOpenDialog(profilePhotoView.getScene().getWindow());
        if (file != null) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                ServiceResult<Void> result = new ProfileService().updatePhoto(requester.getId(), bytes);
                if (result.success()) {
                    headerProfilePhotoView.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)));
                    profilePhotoView.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)));
                    AlertUtil.info("Photo Updated", "Profile photo updated.");
                } else {
                    AlertUtil.error("Update Failed", result.message());
                }
            } catch (Exception e) {
                AlertUtil.error("Error", "Failed to read photo: " + e.getMessage());
            }
        }
    }
}
