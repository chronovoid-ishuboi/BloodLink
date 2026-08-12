package com.bloodlink.controller;

import com.bloodlink.dao.AdminDAO;
import com.bloodlink.model.*;
import com.bloodlink.service.AdminService;
import com.bloodlink.service.RequestService;
import com.bloodlink.service.ServiceResult;
import com.bloodlink.util.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class AdminDashboardController {
    @FXML private Label welcomeLabel;
    @FXML private Label totalDonorsLabel;
    @FXML private Label pendingRequestsLabel;
    @FXML private Label activeRequestsLabel;
    @FXML private Label fulfillmentRateLabel;
    @FXML private Label statusMessageLabel;
    @FXML private BarChart<String, Number> demandChart;
    @FXML private LineChart<String, Number> monthlyChart;
    @FXML private PieChart statusChart;

    @FXML private TextField userSearchField;
    @FXML private TableView<AdminUserRow> userTable;
    @FXML private TableColumn<AdminUserRow, Long> userIdColumn;
    @FXML private TableColumn<AdminUserRow, String> userNameColumn;
    @FXML private TableColumn<AdminUserRow, String> userEmailColumn;
    @FXML private TableColumn<AdminUserRow, Role> userRoleColumn;
    @FXML private TableColumn<AdminUserRow, String> userDistrictColumn;
    @FXML private TableColumn<AdminUserRow, String> userApprovedColumn;
    @FXML private TableColumn<AdminUserRow, String> userActiveColumn;
    @FXML private TableColumn<AdminUserRow, LocalDateTime> userCreatedColumn;

    @FXML private TextField requestSearchField;
    @FXML private TableView<BloodRequest> requestTable;
    @FXML private TableColumn<BloodRequest, Long> requestIdColumn;
    @FXML private TableColumn<BloodRequest, String> requesterColumn;
    @FXML private TableColumn<BloodRequest, BloodGroup> requestBloodColumn;
    @FXML private TableColumn<BloodRequest, Integer> requestUnitsColumn;
    @FXML private TableColumn<BloodRequest, Urgency> requestUrgencyColumn;
    @FXML private TableColumn<BloodRequest, String> requestHospitalColumn;
    @FXML private TableColumn<BloodRequest, String> requestDistrictColumn;
    @FXML private TableColumn<BloodRequest, RequestStatus> requestStatusColumn;
    @FXML private TableColumn<BloodRequest, LocalDateTime> requestCreatedColumn;

    @FXML private TableView<DemandRow> demandTable;
    @FXML private TableColumn<DemandRow, BloodGroup> demandBloodColumn;
    @FXML private TableColumn<DemandRow, Long> demandPendingColumn;
    @FXML private TableColumn<DemandRow, Long> demandAvailableColumn;
    @FXML private TableColumn<DemandRow, Long> demandGapColumn;

    @FXML private TableView<AuditEntry> auditTable;
    @FXML private TableColumn<AuditEntry, LocalDateTime> auditTimeColumn;
    @FXML private TableColumn<AuditEntry, String> auditActorColumn;
    @FXML private TableColumn<AuditEntry, String> auditActionColumn;
    @FXML private TableColumn<AuditEntry, String> auditEntityColumn;
    @FXML private TableColumn<AuditEntry, String> auditDetailsColumn;

    private final AdminDAO adminDAO = new AdminDAO();
    private final AdminService adminService = new AdminService();
    private final RequestService requestService = new RequestService();
    private Admin admin;
    private Timeline refreshTimeline;

    @FXML private void initialize() {
        if (!(SessionManager.getInstance().getCurrentUser() instanceof Admin currentAdmin)) {
            SceneManager.showLogin(); return;
        }
        admin = currentAdmin;
        welcomeLabel.setText("Administrator — " + admin.getFullName());
        configureTables();
        userSearchField.textProperty().addListener((obs, oldValue, newValue) -> loadUsers());
        requestSearchField.textProperty().addListener((obs, oldValue, newValue) -> loadRequests());
        refreshAll();
        int seconds = Math.max(8, AppConfig.getInt("ui.auto-refresh-seconds"));
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(seconds), event -> refreshAll()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void configureTables() {
        userIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().id()));
        userNameColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().fullName()));
        userEmailColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().email()));
        userRoleColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().role()));
        userDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        userApprovedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().approved() ? "Approved" : "Pending"));
        userActiveColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().active() ? "Active" : "Suspended"));
        userCreatedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().createdAt()));

        requestIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().id()));
        requesterColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().requesterName()));
        requestBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        requestUnitsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().unitsNeeded()));
        requestUrgencyColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().urgency()));
        requestHospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        requestDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        requestStatusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().status()));
        requestCreatedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().createdAt()));

        demandBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        demandPendingColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().pendingRequests()));
        demandAvailableColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().availableDonors()));
        demandGapColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().pendingRequests() - v.getValue().availableDonors()));

        auditTimeColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().createdAt()));
        auditActorColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().actorName() == null ? "System" : v.getValue().actorName()));
        auditActionColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().action()));
        auditEntityColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().entityType() + (v.getValue().entityId() == null ? "" : " #" + v.getValue().entityId())));
        auditDetailsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().details()));

        userRoleColumn.setCellFactory(ChipTableCells.forValues());
        userApprovedColumn.setCellFactory(ChipTableCells.forValues());
        userActiveColumn.setCellFactory(ChipTableCells.forValues());
        requestUrgencyColumn.setCellFactory(ChipTableCells.forValues());
        requestStatusColumn.setCellFactory(ChipTableCells.forValues());

        userTable.setPlaceholder(emptyState("No users match this search."));
        requestTable.setPlaceholder(emptyState("No blood requests match this search."));
        demandTable.setPlaceholder(emptyState("No demand data is available yet."));
        auditTable.setPlaceholder(emptyState("No audit events have been recorded yet."));
    }

    private Label emptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        return label;
    }

    @FXML private void refreshAll() {
        try {
            DashboardStats stats = adminDAO.loadStats();
            totalDonorsLabel.setText(String.valueOf(stats.totalDonors()));
            pendingRequestsLabel.setText(String.valueOf(stats.pendingRequests()));
            activeRequestsLabel.setText(String.valueOf(stats.activeRequests()));
            fulfillmentRateLabel.setText(String.format("%.1f%%", stats.fulfillmentRate()));
            loadCharts(); loadUsers(); loadRequests();
            demandTable.setItems(FXCollections.observableArrayList(adminDAO.demandRows()));
            auditTable.setItems(FXCollections.observableArrayList(adminDAO.auditEntries(200)));
            statusMessageLabel.setText("Last refreshed successfully");
        } catch (Exception e) {
            statusMessageLabel.setText("Refresh failed: " + e.getMessage());
        }
    }

    private void loadCharts() throws SQLException {
        demandChart.getData().clear();
        XYChart.Series<String, Number> demandSeries = new XYChart.Series<>();
        demandSeries.setName("All requests");
        for (Map.Entry<BloodGroup, Long> entry : adminDAO.requestsByBloodGroup().entrySet())
            demandSeries.getData().add(new XYChart.Data<>(entry.getKey().toString(), entry.getValue()));
        demandChart.getData().add(demandSeries);

        monthlyChart.getData().clear();
        XYChart.Series<String, Number> monthlySeries = new XYChart.Series<>();
        monthlySeries.setName("Requests");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yy");
        for (Map.Entry<YearMonth, Long> entry : adminDAO.monthlyRequests(6).entrySet())
            monthlySeries.getData().add(new XYChart.Data<>(entry.getKey().format(formatter), entry.getValue()));
        monthlyChart.getData().add(monthlySeries);

        statusChart.getData().clear();
        adminDAO.requestsByStatus().forEach((status, count) -> statusChart.getData().add(new PieChart.Data(status.name(), count)));
    }

    private void loadUsers() {
        try { userTable.setItems(FXCollections.observableArrayList(adminDAO.findUsers(userSearchField.getText()))); }
        catch (SQLException e) { statusMessageLabel.setText(e.getMessage()); }
    }

    private void loadRequests() {
        try { requestTable.setItems(FXCollections.observableArrayList(adminDAO.findRequests(requestSearchField.getText()))); }
        catch (SQLException e) { statusMessageLabel.setText(e.getMessage()); }
    }

    @FXML private void approveSelectedUser() {
        AdminUserRow selected = selectedUser(); if (selected == null) return;
        showResult(adminService.setApproved(selected.id(), true, admin.getId())); refreshAll();
    }

    @FXML private void suspendSelectedUser() {
        AdminUserRow selected = selectedUser(); if (selected == null) return;
        if (!AlertUtil.confirm("Suspend user", "Suspend " + selected.fullName() + "?")) return;
        showResult(adminService.setActive(selected.id(), false, admin.getId())); refreshAll();
    }

    @FXML private void activateSelectedUser() {
        AdminUserRow selected = selectedUser(); if (selected == null) return;
        showResult(adminService.setActive(selected.id(), true, admin.getId())); refreshAll();
    }

    @FXML private void resetSelectedPassword() {
        AdminUserRow selected = selectedUser(); if (selected == null) return;
        PasswordDialog.show("Reset password", "Set a temporary password for " + selected.fullName())
                .ifPresent(password -> showResult(adminService.resetPassword(selected.id(), password, admin.getId())));
    }

    @FXML private void escalateSelectedRequest() {
        BloodRequest selected = selectedRequest(); if (selected == null) return;
        showResult(requestService.adminTransition(selected.id(), admin.getId(), RequestStatus.ESCALATED, "Manually escalated by admin"));
        refreshAll();
    }

    @FXML private void closeSelectedRequest() {
        BloodRequest selected = selectedRequest(); if (selected == null) return;
        if (!AlertUtil.confirm("Close request", "Close request #" + selected.id() + " as cancelled?")) return;
        showResult(requestService.adminTransition(selected.id(), admin.getId(), RequestStatus.CANCELLED, "Closed by admin"));
        refreshAll();
    }

    private AdminUserRow selectedUser() {
        AdminUserRow selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) AlertUtil.warning("No user selected", "Select a user first.");
        else if (selected.role() == Role.ADMIN) { AlertUtil.warning("Protected account", "Administrator accounts cannot be changed here."); return null; }
        return selected;
    }

    private BloodRequest selectedRequest() {
        BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) AlertUtil.warning("No request selected", "Select a request first.");
        return selected;
    }

    private void showResult(ServiceResult<Void> result) {
        if (result.success()) AlertUtil.info("Success", result.message()); else AlertUtil.error("Action failed", result.message());
    }

    @FXML private void logout() {
        if (refreshTimeline != null) refreshTimeline.stop();
        SceneManager.logout();
    }
}
