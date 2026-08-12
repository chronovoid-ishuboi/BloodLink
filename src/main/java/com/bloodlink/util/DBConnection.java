package com.bloodlink.util;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnection {
    private static String activeUrl = null;
    private static String activeUser = null;
    private static String activePassword = null;
    private static boolean isEmbeddedFallback = false;
    private static boolean initialized = false;

    private DBConnection() { }

    public static synchronized Connection getConnection() throws SQLException {
        if (activeUrl == null) {
            determineConnection();
        }
        if (!initialized) {
            initialized = true;
            DatabaseSetup.ensureInitialized();
        }
        try {
            return DriverManager.getConnection(activeUrl, activeUser, activePassword);
        } catch (SQLException e) {
            if (!isEmbeddedFallback) {
                System.err.println("Primary DB connection failed: " + e.getMessage() + ". Falling back to local embedded database.");
                setupEmbeddedFallback();
                return DriverManager.getConnection(activeUrl, activeUser, activePassword);
            }
            throw e;
        }
    }

    public static synchronized Connection getRawConnection() throws SQLException {
        if (activeUrl == null) {
            determineConnection();
        }
        return DriverManager.getConnection(activeUrl, activeUser, activePassword);
    }

    private static void determineConnection() {
        String configuredUrl = AppConfig.get("db.url");
        String configuredUser = AppConfig.get("db.username");
        String configuredPassword = AppConfig.get("db.password");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(configuredUrl, configuredUser, configuredPassword)) {
                activeUrl = configuredUrl;
                activeUser = configuredUser;
                activePassword = configuredPassword;
                isEmbeddedFallback = false;
                System.out.println("Connected to primary database: " + configuredUrl);
                return;
            }
        } catch (Exception e) {
            System.out.println("Primary MySQL database unreachable (" + e.getMessage() + "). Switching to local persistent database.");
        }

        setupEmbeddedFallback();
    }

    private static void setupEmbeddedFallback() {
        File dbDir = new File("database");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        activeUrl = "jdbc:h2:file:./database/bloodlink_db;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE";
        activeUser = "sa";
        activePassword = "";
        isEmbeddedFallback = true;
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ignored) { }
    }

    public static boolean isEmbedded() {
        if (activeUrl == null) {
            determineConnection();
        }
        return isEmbeddedFallback;
    }

    public static boolean testConnection() {
        try (Connection conn = getRawConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}

