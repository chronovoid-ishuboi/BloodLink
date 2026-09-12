package com.bloodlink.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * All BloodLink data lives in MySQL. There is intentionally no
 * embedded-database fallback: if MySQL is unreachable, every call here
 * fails loudly and immediately rather than silently switching to a
 * different, differently-populated database.
 * <p>
 * An earlier version of this class fell back to an embedded H2 file
 * database whenever the configured MySQL connection failed. That was
 * removed -- it could make the app appear to work correctly while quietly
 * writing to a local H2 file instead of {@code bloodlink_db}, which would
 * look identical in the UI but show nothing changing in MySQL Workbench.
 * If you see the {@link SQLException} below, fix the MySQL connection
 * (service running, {@code DB_URL}/{@code DB_USERNAME}/{@code DB_PASSWORD}
 * correct for this session) rather than routing around it.
 */
public final class DBConnection {
    private static volatile boolean driverLoaded = false;
    private static volatile boolean schemaInitialized = false;

    private DBConnection() { }

    public static synchronized Connection getConnection() throws SQLException {
        ensureDriverLoaded();
        if (!schemaInitialized) {
            schemaInitialized = true;
            DatabaseSetup.ensureInitialized();
        }
        return openConnection();
    }

    /** Like {@link #getConnection()} but never triggers schema initialization -- used by setup code itself. */
    public static synchronized Connection getRawConnection() throws SQLException {
        ensureDriverLoaded();
        return openConnection();
    }

    private static Connection openConnection() throws SQLException {
        String url = AppConfig.get("db.url");
        try {
            return DriverManager.getConnection(url, AppConfig.get("db.username"), AppConfig.get("db.password"));
        } catch (SQLException e) {
            throw new SQLException("Could not connect to the BloodLink MySQL database at " + url
                    + ". Confirm the MySQL80 service is running and that DB_URL, DB_USERNAME, and "
                    + "DB_PASSWORD are set correctly for this session.", e);
        }
    }

    private static void ensureDriverLoaded() throws SQLException {
        if (driverLoaded) return;
        try {
            String url = AppConfig.get("db.url");
            if (url != null && url.startsWith("jdbc:h2:")) {
                Class.forName("org.h2.Driver");
            } else {
                Class.forName("com.mysql.cj.jdbc.Driver");
            }
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            // If the driver is missing, BloodLink cannot function.
            // We throw a SQLException to be handled by the caller, usually resulting
            // in an Alert pointing the user to check their classpath or dependencies.
            throw new SQLException("Database driver not found on classpath.", e);
        }
    }

    public static boolean testConnection() {
        try (Connection connection = getRawConnection()) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
