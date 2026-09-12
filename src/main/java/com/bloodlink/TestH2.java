package com.bloodlink;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TestH2 {
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                String script = readResource("/com/bloodlink/sql/schema.sql");
                List<String> statements = splitStatements(script);
                for (String sql : statements) {
                    if (!sql.isBlank()) {
                        sql = sql.replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
                        System.out.println("Executing: " + sql.substring(0, Math.min(sql.length(), 60)) + "...");
                        stmt.execute(sql);
                    }
                }
                System.out.println("Success! Schema is compatible with H2 in MySQL mode.");
            }
        }
    }
    
    private static String readResource(String resource) throws Exception {
        try (InputStream input = TestH2.class.getResourceAsStream(resource)) {
            if (input == null) throw new Exception("Missing SQL resource: " + resource);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines().reduce("", (a, b) -> a + b + System.lineSeparator());
            }
        }
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                statements.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) statements.add(current.toString().trim());
        return statements;
    }
}
