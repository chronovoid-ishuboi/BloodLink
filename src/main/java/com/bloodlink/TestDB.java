package com.bloodlink;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestDB {
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:file:./bloodlink_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE";
        try (Connection conn = DriverManager.getConnection(url, "root", "")) {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, email, password_hash FROM users")) {
                while (rs.next()) {
                    System.out.println(rs.getLong("id") + " | " + rs.getString("email") + " | " + rs.getString("password_hash"));
                }
            } catch (Exception e) {
                System.out.println("Error querying users: " + e.getMessage());
            }
        }
    }
}
