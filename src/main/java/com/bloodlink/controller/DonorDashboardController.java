package com.bloodlink.controller;

import com.bloodlink.dao.DonorDAO;
import com.bloodlink.dao.HospitalDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.model.*;
import com.bloodlink.service.*;
import com.bloodlink.util.LogoManager;
import com.bloodlink.util.*;
import com.bloodlink.view.components.BadgeView;
import com.bloodlink.view.components.EmptyState;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DonorDashboardController {
    @FXML private ImageView appLogoView;
    @FXML private Label welcomeLabel;
    @FXML private Label bloodGroupLabel;
    @FXML private Label profileBloodGroupLabel;
    @FXML private TabPane workspaceTabs;
    @FXML private Label badgeLabel;
    @FXML private Label eligibilityLabel;
    @FXML private Label cooldownLabel;
    @FXML private ProgressBar cooldownProgress;
    @FXML private Label unreadLabel;
    @FXML private ComboBox<AvailabilityStatus> availabilityCombo;

    @FXML private Label impactDonationsLabel;
    @FXML private Label impactUnitsLabel;
    @FXML private Label impactHospitalsLabel;
    @FXML private Label impactSinceLabel;
    @FXML private Label impactRatingLabel;

    @FXML private TableView<HospitalWithDistance> nearbyHospitalTable;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalNameColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalDistrictColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalAreaColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalPhoneColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalDistanceColumn;

    @FXML private ListView<DonorMatchView> matchList;
    @FXML private WebView mapView;

    @FXML private TableView<DonationRecord> donationTable;
    @FXML private TableColumn<DonationRecord, LocalDate> donationDateColumn;
    @FXML private TableColumn<DonationRecord, String> donationHospitalColumn;
    @FXML private TableColumn<DonationRecord, BloodGroup> donationBloodColumn;
    @FXML private TableColumn<DonationRecord, Integer> donationUnitsColumn;
    @FXML private TableColumn<DonationRecord, String> donationVerifiedColumn;
    @FXML private TableColumn<DonationRecord, String> donationReviewColumn;

    @FXML private ListView<Notification> notificationList;

    @FXML private TextField nameField;
    @FXML private Label nidLabel;
    @FXML private TextField phoneField;
    @FXML private Label emailLabel;
    @FXML private TextField guardianNameField;
    @FXML private TextField guardianPhoneField;
    @FXML private TextField districtField;
    @FXML private TextArea addressArea;
    @FXML private ImageView headerProfilePhotoView;
    @FXML private ImageView profilePhotoView;
    @FXML private Label profileInitialsLabel;
    @FXML private Button uploadPhotoButton;
    @FXML private Button removePhotoButton;
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
    @FXML private ComboBox<Hospital> referenceHospitalCombo;
    @FXML private Label referenceHospitalHelperLabel;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label profileMessageLabel;

    private final DonorDAO donorDAO = new DonorDAO();
    private final HospitalDAO hospitalDAO = new HospitalDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final NotificationService notificationService = new NotificationService();
    private final DonorService donorService = new DonorService();
    private final RequestService requestService = new RequestService();
    private final ProfileService profileService = new ProfileService();
    private final EligibilityService eligibilityService = new EligibilityService();
    private final ReviewService reviewService = new ReviewService();
    private final LocationService locationService = new LocationService();
    private Donor donor;
    private Timeline refreshTimeline;
    private volatile boolean refreshInFlight = false;
    private java.util.Set<Long> reviewedRequestIds = java.util.Set.of();
    private boolean suppressReferenceHospitalSearch = false;

    @FXML private void initialize() {
        if (!(SessionManager.getInstance().getCurrentUser() instanceof Donor currentDonor)) {
            SceneManager.showLogin(); return;
        }
        this.donor = currentDonor;
        welcomeLabel.setText(donor.getFullName());
        TabIcons.apply(workspaceTabs, java.util.Map.of(
                "Overview", Icons.HOME,
                "My Impact", Icons.HEART,
                "Nearby Hospitals", Icons.HOSPITAL,
                "Matched Requests", Icons.DROPLET,
                "Donation History", Icons.CLOCK,
                "Notifications", Icons.BELL,
                "Profile", Icons.USER));
        LogoManager.applyLogo(appLogoView);
        new ProfileService().loadPhoto(donor.getId()).ifPresent(bytes -> {
            try {
                headerProfilePhotoView.setImage(new Image(new ByteArrayInputStream(bytes)));
                profilePhotoView.setImage(new Image(new ByteArrayInputStream(bytes)));
            } catch (Exception ignored) {}
        });
        configureTables();
        configureReferenceHospitalPicker();
        PushClient.getInstance().connect(donor.getId());
        PushClient.getInstance().onRefresh(this::refreshAll);
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
        DonorMatchView selected = matchList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.hospitalName() != null) {
            // Use hospital district as proxy for coordinates (since we don't have real DB coords in this demo)
            // Ideally, we'd query the Hospital model for lat/lng
            // We'll just put a pin in the center of Bangladesh for now if no coordinates are found
            // Or better, let's look up the hospital object if possible
            double lat = 23.8103, lng = 90.4125; 
            try {
                Hospital h = hospitalDAO.findByName(selected.hospitalName());
                if (h != null) {
                    lat = h.latitude();
                    lng = h.longitude();
                }
            } catch (SQLException e) {}
            
            String title = selected.hospitalName().replace("'", "\\'");
            String script = String.format("addMarker(%f, %f, '%s', '%s', '%s'); fitBounds();", 
                                lat, lng, title, selected.district(), "Hospital");
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
                        String script = String.format("addMarker(%f, %f, 'My Area', '%s', 'Donor'); setView(%f, %f, 12);",
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


    private void configureTables() {

        donationDateColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().donationDate()));
        donationHospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        donationBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        donationUnitsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().units()));
        donationVerifiedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().verified() ? "Verified" : "Pending"));
        donationReviewColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(
                v.getValue().requestId() != null && reviewedRequestIds.contains(v.getValue().requestId()) ? "Rated" : "Not rated yet"));


        donationVerifiedColumn.setCellFactory(ChipTableCells.forValues());

        matchList.setPlaceholder(emptyState("No matching emergency requests are waiting for you."));
        matchList.setCellFactory(lv -> new com.bloodlink.view.components.DonorMatchCell());
        donationTable.setPlaceholder(emptyState("No verified donation history is available yet."));
        notificationList.setPlaceholder(emptyState("You have no notifications."));
        nearbyHospitalNameColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospital().name()));
        nearbyHospitalDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospital().district()));
        nearbyHospitalAreaColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospital().area()));
        nearbyHospitalPhoneColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(
                v.getValue().hospital().phone() == null || v.getValue().hospital().phone().isBlank() ? "—" : v.getValue().hospital().phone()));
        nearbyHospitalDistanceColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(formatDistance(v.getValue().distanceKm())));
        nearbyHospitalTable.setPlaceholder(emptyState("No hospitals are in the directory yet."));
    }

    private String formatDistance(Double distanceKm) {
        return distanceKm == null ? "—" : String.format("~%.1f km", distanceKm);
    }

    private String formatRating(Double averageRating, long reviewCount) {
        return averageRating == null ? "No reviews yet" : String.format("\u2605 %.1f (%d)", averageRating, reviewCount);
    }



    /**
     * Same searchable-picker pattern as the requester's hospital field, repurposed so
     * a donor can choose their own precise location stand-in -- see LocationService.
     */
    private void configureReferenceHospitalPicker() {
        referenceHospitalCombo.setEditable(true);
        referenceHospitalCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Hospital hospital) { return hospital == null ? "" : hospital.name(); }
            @Override public Hospital fromString(String text) { return null; }
        });
        searchReferenceHospitals("");
        referenceHospitalCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (suppressReferenceHospitalSearch) return;
            searchReferenceHospitals(newText);
        });
    }

    private void searchReferenceHospitals(String query) {
        try {
            referenceHospitalCombo.getItems().setAll(hospitalDAO.search(query, 15));
        } catch (SQLException e) {
            // Search-as-you-type failure should not block the donor from using the field.
        }
    }

    /**
     * Renders the donor's tier through {@link BadgeView} instead of printing the
     * raw enum constant, which is what {@code badgeTier + " donor"} used to put on
     * screen ("PLATINUM donor"). The label text, icon and colour all come from
     * the editable badge manifest.
     */
    private void applyBadge() {
        badgeLabel.setText(null);
        badgeLabel.setGraphic(new BadgeView(donor.getBadgeTier(), 22, false));
    }

    private javafx.scene.Node emptyState(String text) {
        return EmptyState.of(text);
    }

    private javafx.scene.Node emptyState(String title, String hint) {
        return EmptyState.of(title, hint);
    }

    private void populateProfile() {
        welcomeLabel.setText("Welcome, " + donor.getFullName());
        String bloodGroup = donor.getBloodGroup() == null ? "—" : donor.getBloodGroup().toString();
        bloodGroupLabel.setText(bloodGroup);
        profileBloodGroupLabel.setText(bloodGroup);
        applyBadge();
        
        // Personal Information
        nameField.setText(donor.getFullName());
        String nid = donor.getNidNumber();
        if (nid != null && nid.length() > 4) {
            nidLabel.setText("*" + nid.substring(nid.length() - 4));
        } else {
            nidLabel.setText(nid != null ? nid : "Not Provided");
        }
        
        // Contact Information
        phoneField.setText(donor.getPhone());
        emailLabel.setText(donor.getEmail());
        guardianNameField.setText(donor.getGuardianName());
        guardianPhoneField.setText(donor.getGuardianPhone());
        districtField.setText(donor.getDistrict());
        addressArea.setText(donor.getAddress());
        
        // Health & Eligibility
        weightField.setText(String.valueOf(donor.getWeightKg()));
        heightField.setText(donor.getHeightCm() != null ? String.valueOf(donor.getHeightCm()) : "");
        lastDonationPicker.setValue(donor.getLastDonationDate());
        
        // Screening
        chronicConditionsField.setText(donor.getChronicConditions());
        recentSurgeryCheck.setSelected(donor.getRecentSurgeryDetails() != null && !donor.getRecentSurgeryDetails().isBlank());
        recentSurgeryDetailsField.setText(donor.getRecentSurgeryDetails());
        recentTattooCheck.setSelected(donor.getRecentTattooDetails() != null && !donor.getRecentTattooDetails().isBlank());
        recentTattooDetailsField.setText(donor.getRecentTattooDetails());
        currentMedicationsCheck.setSelected(donor.getCurrentMedicationsDetails() != null && !donor.getCurrentMedicationsDetails().isBlank());
        currentMedicationsDetailsField.setText(donor.getCurrentMedicationsDetails());
        recentIllnessCheck.setSelected(donor.getRecentIllnessDetails() != null && !donor.getRecentIllnessDetails().isBlank());
        recentIllnessDetailsField.setText(donor.getRecentIllnessDetails());
        recentPregnancyCheck.setSelected(donor.getRecentPregnancyDetails() != null && !donor.getRecentPregnancyDetails().isBlank());
        recentPregnancyDetailsField.setText(donor.getRecentPregnancyDetails());

        populateReferenceHospital();
        applyProfilePhoto();
        updateEligibilityCard();
    }

    /**
     * Loaded via the dedicated ProfileService.loadPhoto(), never as part of the
     * routine donor/session fetch -- see UserDAO.findPhoto's Javadoc for why. Falls
     * back to an initials badge (no image asset, no image-generation tool available
     * in this environment -- this is a real, working substitute, not a placeholder)
     * when no photo is set or the stored bytes can't be decoded as an image.
     */
    private void applyProfilePhoto() {
        java.util.Optional<byte[]> photo = profileService.loadPhoto(donor.getId());
        if (photo.isPresent()) {
            try {
                headerProfilePhotoView.setImage(new Image(new ByteArrayInputStream(photo.get())));
                profilePhotoView.setImage(new Image(new ByteArrayInputStream(photo.get())));
                profilePhotoView.setClip(new Circle(42, 42, 42));
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
        profileInitialsLabel.setText(initialsOf(donor.getFullName()));
    }

    private String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    @FXML private void uploadPhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a profile photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(uploadPhotoButton.getScene().getWindow());
        if (file == null) return;
        BackgroundTasks.run(
                () -> profileService.updatePhoto(donor.getId(), Files.readAllBytes(file.toPath())),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Could not read that file: " + error.getMessage()));
    }

    @FXML private void removePhoto() {
        BackgroundTasks.run(
                () -> profileService.updatePhoto(donor.getId(), null),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Photo could not be removed: " + error.getMessage()));
    }

    private void populateReferenceHospital() {
        suppressReferenceHospitalSearch = true;
        if (donor.getReferenceHospitalId() == null) {
            referenceHospitalCombo.setValue(null);
            referenceHospitalCombo.getEditor().clear();
            referenceHospitalHelperLabel.setText("Not set -- distance in your matches uses your district instead.");
        } else {
            try {
                hospitalDAO.findById(donor.getReferenceHospitalId()).ifPresentOrElse(
                        hospital -> {
                            referenceHospitalCombo.setValue(hospital);
                            referenceHospitalCombo.getEditor().setText(hospital.name());
                            referenceHospitalHelperLabel.setText("Distance in your matches is measured from here.");
                        },
                        () -> referenceHospitalHelperLabel.setText("Your saved reference hospital is no longer active; distance falls back to your district."));
            } catch (SQLException e) {
                referenceHospitalHelperLabel.setText("Could not load your reference hospital: " + e.getMessage());
            }
        }
        suppressReferenceHospitalSearch = false;
    }

    private void updateEligibilityCard() {
        EligibilityService.EligibilityResult result = eligibilityService.evaluate(donor);
        eligibilityLabel.setText(result.eligible() ? "READY" : "NOT ELIGIBLE");
        cooldownLabel.setText(result.reason());
        cooldownProgress.setProgress(result.cooldownDaysRemaining() == 0 ? 1.0 : 1.0 - result.cooldownDaysRemaining() / 56.0);
        eligibilityLabel.getStyleClass().removeAll("status-success", "status-warning");
        eligibilityLabel.getStyleClass().add(result.eligible() ? "status-success" : "status-warning");
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
                error -> { profileMessageLabel.setText("Refresh failed: " + error.getMessage()); refreshInFlight = false; });
    }

    private DonorDashboardData loadDashboardData() throws SQLException {
        java.util.List<DonationRecord> donations = donorDAO.findDonationHistory(donor.getId());
        return new DonorDashboardData(
                requestDAO.findMatchesForDonor(donor.getId(), donor.getDistrict(), donor.getReferenceHospitalId()),
                donations,
                notificationService.list(donor.getId()),
                notificationService.unreadCount(donor.getId()),
                reviewService.reviewedRequestIdsBy(donor.getId()),
                reviewService.reputationOf(donor.getId()),
                loadNearbyHospitals());
    }

    /**
     * The general "browse hospitals" view the matched-requests list can't cover, since
     * that only ever shows hospitals tied to an actual active request. Sorted by
     * distance from the donor (nulls -- unknown distance -- sorted last, never treated
     * as "far" the way the matching radius filter treats them, since this is just a
     * browsing list, not an inclusion decision).
     */
    private java.util.List<HospitalWithDistance> loadNearbyHospitals() throws SQLException {
        java.util.List<HospitalWithDistance> rows = new java.util.ArrayList<>();
        for (Hospital hospital : hospitalDAO.findAll()) {
            Double distanceKm = locationService.distanceKm(donor.getDistrict(), donor.getReferenceHospitalId(),
                    hospital.latitude(), hospital.longitude()).orElse(null);
            rows.add(new HospitalWithDistance(hospital, distanceKm));
        }
        rows.sort(java.util.Comparator.comparing(HospitalWithDistance::distanceKm,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return rows;
    }

    private record HospitalWithDistance(Hospital hospital, Double distanceKm) { }

    private void applyDashboardData(DonorDashboardData data) {
        matchList.setItems(FXCollections.observableArrayList(data.matches()));
        donationTable.setItems(FXCollections.observableArrayList(data.donations()));
        notificationList.setItems(FXCollections.observableArrayList(data.notifications()));
        unreadLabel.setText(String.valueOf(data.unreadCount()));
        reviewedRequestIds = data.reviewedRequestIds();
        donationTable.refresh();
        applyImpactSummary(data.donations(), data.reputation());
        nearbyHospitalTable.setItems(FXCollections.observableArrayList(data.nearbyHospitals()));
    }

    /**
     * Computed from the donation history already fetched every refresh, rather than a
     * separate aggregate query -- this data is already in memory, so there's no reason
     * to hit the database again just to summarize it. Every figure here is a real,
     * verifiable count from donation_history; deliberately no "lives saved" style
     * multiplier, since that's an estimate this app has no basis to assert as fact
     * about a specific donor's specific donations.
     */
    private void applyImpactSummary(java.util.List<DonationRecord> donations, ReputationSummary reputation) {
        if (donations.isEmpty()) {
            impactDonationsLabel.setText("0");
            impactUnitsLabel.setText("0");
            impactHospitalsLabel.setText("0");
            impactSinceLabel.setText("No verified donations yet");
        } else {
            int totalUnits = donations.stream().mapToInt(DonationRecord::units).sum();
            long distinctHospitals = donations.stream().map(DonationRecord::hospitalName).distinct().count();
            LocalDate first = donations.stream().map(DonationRecord::donationDate)
                    .min(LocalDate::compareTo).orElse(null);
            impactDonationsLabel.setText(String.valueOf(donations.size()));
            impactUnitsLabel.setText(String.valueOf(totalUnits));
            impactHospitalsLabel.setText(String.valueOf(distinctHospitals));
            impactSinceLabel.setText(first == null ? "—" : "Donating since " + first);
        }
        impactRatingLabel.setText(formatRating(reputation.hasReviews() ? reputation.averageRating() : null, reputation.reviewCount()));
    }

    private record DonorDashboardData(java.util.List<DonorMatchView> matches, java.util.List<DonationRecord> donations,
                                      java.util.List<Notification> notifications, long unreadCount,
                                      java.util.Set<Long> reviewedRequestIds, ReputationSummary reputation,
                                      java.util.List<HospitalWithDistance> nearbyHospitals) { }

    @FXML private void acceptSelected() {
        DonorMatchView selected = matchList.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (selected.matchStatus() != MatchStatus.NOTIFIED) { AlertUtil.warning("Already answered", "This match is no longer awaiting a response."); return; }
        if (!AlertUtil.confirm("Accept request", "Accept blood request #" + selected.requestId() + "?")) return;
        showResult(requestService.accept(selected.requestId(), donor.getId()));
        refreshAll();
    }

    @FXML private void declineSelected() {
        DonorMatchView selected = matchList.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (!AlertUtil.confirm("Decline match", "Decline request #" + selected.requestId() + "?")) return;
        showResult(requestService.decline(selected.requestId(), donor.getId()));
        refreshAll();
    }

    /**
     * The donor's half of the two-sided handshake. A donor can only confirm a request
     * they personally accepted and that is still sitting in ACCEPTED or
     * PARTIALLY_FULFILLED (another donor on the same multi-unit request may have
     * already completed their own handshake, moving the overall status along, while
     * this donor's own match is still waiting) -- both checked here and, more
     * importantly, again in RequestDAO.confirmDonorSide, since this button being
     * visible is not itself authorization.
     */
    @FXML private void confirmDonated() {
        DonorMatchView selected = matchList.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (selected.matchStatus() != MatchStatus.ACCEPTED
                || !(selected.requestStatus() == RequestStatus.ACCEPTED || selected.requestStatus() == RequestStatus.PARTIALLY_FULFILLED)) {
            AlertUtil.warning("Not ready to confirm", "You can only confirm a donation for a request you've accepted that is still awaiting confirmation.");
            return;
        }
        if (selected.donorConfirmed()) {
            AlertUtil.info("Already confirmed", "You already confirmed this donation. Waiting on the requester's side.");
            return;
        }
        if (!AlertUtil.confirm("Confirm donation", "Confirm that you donated blood for request #" + selected.requestId() + "?")) return;
        showResult(requestService.confirmDonated(selected.requestId(), donor.getId()));
        refreshAll();
    }

    /**
     * Rating happens from the Donation History tab, since that's where FULFILLED
     * requests are visible to a donor -- findMatchesForDonor deliberately excludes
     * FULFILLED requests once the handshake completes.
     */
    @FXML private void rateRequester() {
        DonationRecord selected = donationTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No donation selected", "Select a donation record first."); return; }
        if (selected.requestId() == null) {
            AlertUtil.warning("Not reviewable", "This donation record isn't linked to a BloodLink request.");
            return;
        }
        if (reviewedRequestIds.contains(selected.requestId())) {
            AlertUtil.info("Already reviewed", "You already rated the requester for this donation.");
            return;
        }
        ReviewDialog.show("Rate requester", "the requester").ifPresent(input -> {
            ServiceResult<Void> result = reviewService.submit(selected.requestId(), donor.getId(), input.rating(), input.tags(), input.comment());
            showResult(result);
            if (result.success()) refreshAll();
        });
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
                districtField.getText(), addressArea.getText(), guardianNameField.getText(), guardianPhoneField.getText());
        if (!result.success()) { profileMessageLabel.setText(result.message()); return; }
        donor.setFullName(result.data().getFullName()); donor.setPhone(result.data().getPhone());
        donor.setDistrict(result.data().getDistrict()); donor.setAddress(result.data().getAddress());
        donor.setGuardianName(result.data().getGuardianName()); donor.setGuardianPhone(result.data().getGuardianPhone());
        profileMessageLabel.setText(result.message()); populateProfile();
    }

    @FXML private void saveHealth() {
        ServiceResult<Void> result = donorService.updateHealth(
                donor.getId(),
                weightField.getText(),
                heightField.getText(),
                lastDonationPicker.getValue(),
                chronicConditionsField.getText(),
                recentSurgeryCheck.isSelected() ? recentSurgeryDetailsField.getText() : null,
                recentTattooCheck.isSelected() ? recentTattooDetailsField.getText() : null,
                currentMedicationsCheck.isSelected() ? currentMedicationsDetailsField.getText() : null,
                recentIllnessCheck.isSelected() ? recentIllnessDetailsField.getText() : null,
                recentPregnancyCheck.isSelected() ? recentPregnancyDetailsField.getText() : null
        );
        if (result.success()) {
            if (!weightField.getText().isBlank()) donor.setWeightKg(Double.parseDouble(weightField.getText().trim()));
            if (!heightField.getText().isBlank()) donor.setHeightCm(Double.parseDouble(heightField.getText().trim()));
            donor.setLastDonationDate(lastDonationPicker.getValue());
            donor.setChronicConditions(chronicConditionsField.getText());
            donor.setRecentSurgeryDetails(recentSurgeryCheck.isSelected() ? recentSurgeryDetailsField.getText() : null);
            donor.setRecentTattooDetails(recentTattooCheck.isSelected() ? recentTattooDetailsField.getText() : null);
            donor.setCurrentMedicationsDetails(currentMedicationsCheck.isSelected() ? currentMedicationsDetailsField.getText() : null);
            donor.setRecentIllnessDetails(recentIllnessCheck.isSelected() ? recentIllnessDetailsField.getText() : null);
            donor.setRecentPregnancyDetails(recentPregnancyCheck.isSelected() ? recentPregnancyDetailsField.getText() : null);
            updateEligibilityCard();
        }
        profileMessageLabel.setText(result.message());
    }

    /**
     * Saves whichever hospital the donor picked from the searchable list as their
     * reference point. Free-typed text that doesn't match a real selection is
     * rejected rather than silently ignored, since an unresolved reference would
     * leave the donor thinking their distance is precise when it fell back silently.
     */
    @FXML private void saveReferenceHospital() {
        Hospital selected = referenceHospitalCombo.getValue();
        String typedText = referenceHospitalCombo.getEditor().getText();
        if (typedText == null || typedText.isBlank()) {
            applyReferenceHospitalResult(donorService.updateReferenceHospital(donor.getId(), null));
            return;
        }
        if (selected == null || !selected.name().equals(typedText)) {
            profileMessageLabel.setText("Pick a hospital from the dropdown list, or clear the field to remove your reference hospital.");
            return;
        }
        applyReferenceHospitalResult(donorService.updateReferenceHospital(donor.getId(), selected.id()));
    }

    @FXML private void clearReferenceHospital() {
        referenceHospitalCombo.setValue(null);
        referenceHospitalCombo.getEditor().clear();
        applyReferenceHospitalResult(donorService.updateReferenceHospital(donor.getId(), null));
    }

    private void applyReferenceHospitalResult(ServiceResult<Void> result) {
        if (result.success()) donor.setReferenceHospitalId(referenceHospitalCombo.getValue() == null ? null : referenceHospitalCombo.getValue().id());
        profileMessageLabel.setText(result.message());
        populateReferenceHospital();
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
        PushClient.getInstance().disconnect();
        SceneManager.logout();
    }

    @FXML private void changeProfilePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(profilePhotoView.getScene().getWindow());
        if (file != null) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                ServiceResult<Void> result = new ProfileService().updatePhoto(donor.getId(), bytes);
                if (result.success()) {
                    headerProfilePhotoView.setImage(new Image(new ByteArrayInputStream(bytes)));
                    profilePhotoView.setImage(new Image(new ByteArrayInputStream(bytes)));
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
