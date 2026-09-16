package com.bloodlink.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

public final class AlertUtil {
    private AlertUtil() { }

    public static void info(String title, String message) { show(Alert.AlertType.INFORMATION, title, message); }
    public static void error(String title, String message) { show(Alert.AlertType.ERROR, title, message); }
    public static void warning(String title, String message) { show(Alert.AlertType.WARNING, title, message); }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);
        applyTheme(alert.getDialogPane());
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    /**
     * Attaches the app stylesheet to a dialog.
     * <p>
     * A {@code DialogPane} is shown in its own window and inherits nothing from
     * the scene that opened it. Every dialog in this app already tags nodes with
     * classes like {@code error-text} and {@code helper-text}, but none of them
     * attached the stylesheet, so those classes resolved to no rule at all and
     * the styling silently did nothing. One shared helper, so a new dialog gets
     * it by calling one line rather than by remembering a URL lookup.
     */
    public static void applyTheme(DialogPane pane) {
        if (pane == null) return;
        java.net.URL stylesheet = AlertUtil.class.getResource("/com/bloodlink/css/theme.css");
        if (stylesheet != null && !pane.getStylesheets().contains(stylesheet.toExternalForm())) {
            pane.getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        applyTheme(alert.getDialogPane());
        alert.showAndWait();
    }
}
