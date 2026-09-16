package com.bloodlink.util;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Puts an {@link Icons} glyph on each tab of a {@link TabPane}.
 * <p>
 * Done from Java rather than by embedding path data in the FXML so the icon set
 * has a single source of truth: changing a glyph in {@code Icons} updates every
 * screen, and 24-character path strings are not copy-pasted across three
 * dashboards. Only the tab's <em>graphic</em> is set, so the tab keeps its FXML
 * {@code text} and the label still comes from the view file.
 * <p>
 * Tabs are matched by their text. Any mismatch in either direction is logged
 * rather than thrown, because a renamed tab should lose its icon, not take the
 * dashboard down.
 */
public final class TabIcons {
    private static final Logger LOGGER = Logger.getLogger(TabIcons.class.getName());
    private static final double SIZE = 14;

    private TabIcons() { }

    /** @param iconsByTabText tab text to the {@link Icons} path constant for that tab */
    public static void apply(TabPane tabPane, Map<String, String> iconsByTabText) {
        if (tabPane == null) return;
        Set<String> unused = new HashSet<>(iconsByTabText.keySet());
        for (Tab tab : tabPane.getTabs()) {
            String pathData = iconsByTabText.get(tab.getText());
            if (pathData == null) {
                LOGGER.warning("No icon mapped for tab \"" + tab.getText() + "\"; it will render without one.");
                continue;
            }
            unused.remove(tab.getText());
            tab.setGraphic(Icons.icon(pathData, SIZE, "tab-icon"));
        }
        if (!unused.isEmpty()) {
            LOGGER.warning("Icons were mapped for tabs that do not exist: " + unused
                    + " (was a tab renamed in the FXML?)");
        }
    }
}
