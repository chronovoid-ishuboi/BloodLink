package com.bloodlink.sql;

import com.bloodlink.Main;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Applies {@code schema.sql} to a throwaway in-memory database and fails if any
 * statement is rejected.
 * <p>
 * This began life as {@code com.bloodlink.TestH2}, a {@code main()} method
 * sitting in {@code src/main/java} next to production code, which had to be run
 * by hand and so effectively never was. The check it performs is genuinely
 * useful -- it catches a syntax error or a broken constraint in the schema
 * without needing a MySQL server -- so it is now an actual test that runs on
 * every build.
 * <p>
 * H2 in MySQL mode is close to, but not identical to, MySQL. A pass here means
 * the schema is well-formed and internally consistent (tables, columns, keys and
 * constraints all resolve); it is not a substitute for running against the real
 * MySQL instance. {@code DBConnection} does accept {@code jdbc:h2:} URLs, so this
 * also covers the local-development path that uses them.
 */
class SchemaCompatibilityTest {

    /** Storage-engine clauses are MySQL-specific and meaningless to H2. */
    private static final String MYSQL_TABLE_OPTIONS =
            "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    @Test
    void schemaAppliesCleanly() throws Exception {
        Class.forName("org.h2.Driver");
        // A uniquely named in-memory database, so the test leaves nothing behind
        // and cannot collide with another test run in the same JVM.
        String url = "jdbc:h2:mem:schema_check_" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";

        List<String> statements = splitStatements(readResource("/com/bloodlink/sql/schema.sql"));
        assertFalse(statements.isEmpty(), "schema.sql produced no statements -- is the resource present?");

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                String cleaned = sql.replace(MYSQL_TABLE_OPTIONS, "").trim();
                if (cleaned.isBlank()) continue;
                assertDoesNotThrow(() -> statement.execute(cleaned),
                        () -> "schema.sql statement was rejected:\n" + abbreviate(cleaned));
            }
        }
    }

    private static String abbreviate(String sql) {
        return sql.length() <= 300 ? sql : sql.substring(0, 300) + "\n... (truncated)";
    }

    private static String readResource(String resource) throws Exception {
        try (InputStream input = Main.class.getResourceAsStream(resource)) {
            assertNotNull(input, "Missing SQL resource: " + resource);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append('\n');
                return out.toString();
            }
        }
    }

    /** Splits on trailing semicolons, skipping blank lines and {@code --} comments. */
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
