package com.bloodlink.controller;

import com.bloodlink.model.Role;
import com.bloodlink.model.User;
import com.bloodlink.service.AuthService;
import com.bloodlink.service.ServiceResult;
import com.bloodlink.util.SceneManager;
import com.bloodlink.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class LoginController {
    @FXML private ComboBox<Role> roleCombo;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button signInButton;

    private final AuthService authService = new AuthService();

    @FXML private void initialize() {
        roleCombo.getItems().setAll(Role.DONOR, Role.REQUESTER, Role.ADMIN);
        roleCombo.setValue(Role.DONOR);
        errorLabel.setText("");
        passwordField.setOnAction(event -> signIn());
    }

    @FXML private void signIn() {
        errorLabel.setText("");
        signInButton.setDisable(true);
        try {
            ServiceResult<User> result = authService.login(emailField.getText(), passwordField.getText(), roleCombo.getValue());
            if (!result.success()) {
                errorLabel.setText(result.message());
                return;
            }
            SessionManager.getInstance().setCurrentUser(result.data());
            SceneManager.showDashboard(result.data().getRole());
        } catch (Exception e) {
            errorLabel.setText("Sign in failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } finally {
            signInButton.setDisable(false);
        }
    }

    @FXML private void openRegistration() { SceneManager.showRegister(); }
}
