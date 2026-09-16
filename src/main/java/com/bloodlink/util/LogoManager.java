package com.bloodlink.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds the admin-replaceable app logo and keeps every {@link ImageView}
 * displaying it in sync. When no custom logo is set, the built-in teardrop
 * {@code SVGPath} beside each ImageView stays visible instead.
 *
 * <h2>Where the file lives</h2>
 * This used to read and write the bare relative path {@code "bloodlink_logo.png"},
 * which resolves against the JVM's working directory. That is the project root
 * when launched via {@code mvn javafx:run}, but something else entirely when the
 * app is started from a packaged jar, a desktop shortcut, or an IDE with a
 * different run directory -- so an uploaded logo would appear to save and then
 * silently vanish on the next launch, or be written into whatever folder the user
 * happened to start from.
 * <p>
 * It is now anchored to a per-user application-data directory that does not move:
 * {@code %APPDATA%\BloodLink} on Windows,
 * {@code ~/Library/Application Support/BloodLink} on macOS, and
 * {@code $XDG_DATA_HOME/BloodLink} (or {@code ~/.local/share/BloodLink}) elsewhere.
 * <p>
 * A logo previously saved next to the project root is still picked up: on first
 * load, if the app-data file does not exist but the legacy one does, it is
 * migrated across. Nothing is lost by the change.
 */
public final class LogoManager {
    private static final Logger LOGGER = Logger.getLogger(LogoManager.class.getName());
    private static final String LOGO_FILE_NAME = "bloodlink_logo.png";
    /** Kept only so an existing logo from the old working-directory behaviour is not orphaned. */
    private static final Path LEGACY_LOGO_PATH = Path.of(LOGO_FILE_NAME);

    private static Image currentLogo = null;

    // Views are re-applied to whenever the logo changes.
    private static final List<ImageView> registeredViews = new ArrayList<>();

    private LogoManager() { }

    static {
        loadLogo();
    }

    /** Absolute, stable location of the custom logo for this user. */
    static Path logoPath() {
        return appDataDirectory().resolve(LOGO_FILE_NAME);
    }

    private static Path appDataDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return (appData == null || appData.isBlank() ? Path.of(home) : Path.of(appData)).resolve("BloodLink");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "BloodLink");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        return (xdg == null || xdg.isBlank() ? Path.of(home, ".local", "share") : Path.of(xdg)).resolve("BloodLink");
    }

    private static void loadLogo() {
        Path path = logoPath();
        try {
            if (!Files.exists(path) && Files.exists(LEGACY_LOGO_PATH)) {
                Files.createDirectories(path.getParent());
                Files.copy(LEGACY_LOGO_PATH, path, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Migrated the existing logo from the working directory to " + path);
            }
            currentLogo = Files.exists(path) ? readImage(path) : null;
        } catch (IOException | RuntimeException e) {
            // A broken or unreadable logo falls back to the built-in vector mark.
            LOGGER.log(Level.WARNING, "Could not load the custom logo; using the built-in mark.", e);
            currentLogo = null;
        }
    }

    private static Image readImage(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Image image = new Image(in);
            return image.isError() ? null : image;
        }
    }

    public static void applyLogo(ImageView imageView) {
        if (imageView == null) return;
        if (!registeredViews.contains(imageView)) {
            registeredViews.add(imageView);
        }
        imageView.setImage(currentLogo);
        imageView.setVisible(currentLogo != null);

        // Each logo slot is a StackPane holding the built-in SVG mark behind the
        // ImageView; exactly one of the two is visible at a time.
        if (imageView.getParent() instanceof StackPane stack) {
            for (javafx.scene.Node child : stack.getChildren()) {
                if (child instanceof SVGPath svgPath) {
                    svgPath.setVisible(currentLogo == null);
                }
            }
        }
    }

    /** Pass {@code null} to delete the custom logo and fall back to the built-in mark. */
    public static void updateLogo(File imageFile) throws IOException {
        Path destination = logoPath();
        if (imageFile == null) {
            Files.deleteIfExists(destination);
            currentLogo = null;
        } else {
            Files.createDirectories(destination.getParent());
            Files.copy(imageFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            currentLogo = readImage(destination);
        }
        for (ImageView view : registeredViews) {
            applyLogo(view);
        }
    }
}
