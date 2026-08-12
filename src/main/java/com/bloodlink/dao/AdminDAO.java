package com.bloodlink.dao;

import com.bloodlink.model.*;
import com.bloodlink.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

public final class AdminDAO {
    public DashboardStats loadStats() throws SQLException {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM users WHERE role='DONOR' AND active=TRUE) total_donors,
                  (SELECT COUNT(*) FROM blood_requests WHERE status='PENDING') pending_requests,
                  (SELECT COUNT(*) FROM blood_requests WHERE status NOT IN ('FULFILLED','CANCELLED')) active_requests,
                  COALESCE((SELECT 100.0*SUM(CASE WHEN status='FULFILLED' THEN 1 ELSE 0 END)/NULLIF(COUNT(*),0) FROM blood_requests),0) fulfillment_rate
                """;
        try (Connection connection = DBConnection.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return new DashboardStats(rs.getLong("total_donors"), rs.getLong("pending_requests"),
                    rs.getLong("active_requests"), rs.getDouble("fulfillment_rate"));
        }
    }

    public Map<BloodGroup, Long> requestsByBloodGroup() throws SQLException {
        EnumMap<BloodGroup, Long> data = new EnumMap<>(BloodGroup.class);
        for (BloodGroup group : BloodGroup.values())
            data.put(group, 0L);
        try (Connection connection = DBConnection.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement
                        .executeQuery("SELECT blood_group,COUNT(*) total FROM blood_requests GROUP BY blood_group")) {
            while (rs.next())
                data.put(BloodGroup.valueOf(rs.getString(1)), rs.getLong(2));
        }
        return data;
    }

    public Map<YearMonth, Long> monthlyRequests(int months) throws SQLException {
        LinkedHashMap<YearMonth, Long> data = new LinkedHashMap<>();
        YearMonth start = YearMonth.now().minusMonths(months - 1L);
        for (int i = 0; i < months; i++)
            data.put(start.plusMonths(i), 0L);
        String sql = "SELECT created_at FROM blood_requests WHERE created_at >= ?";
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(start.atDay(1).atStartOfDay()));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timestamp cat = rs.getTimestamp("created_at");
                    if (cat != null) {
                        YearMonth ym = YearMonth.from(cat.toLocalDateTime());
                        if (data.containsKey(ym)) {
                            data.put(ym, data.get(ym) + 1);
                        }
                    }
                }
            }
        }
        return data;
    }

    public Map<RequestStatus, Long> requestsByStatus() throws SQLException {
        EnumMap<RequestStatus, Long> data = new EnumMap<>(RequestStatus.class);
        try (Connection connection = DBConnection.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement
                        .executeQuery("SELECT status,COUNT(*) total FROM blood_requests GROUP BY status")) {
            while (rs.next())
                data.put(RequestStatus.valueOf(rs.getString(1)), rs.getLong(2));
        }
        return data;
    }

    public List<AdminUserRow> findUsers(String search) throws SQLException {
        String sql = """
                SELECT id,full_name,email,role,district,approved,active,created_at FROM users
                WHERE (?='' OR LOWER(full_name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(district) LIKE ?)
                ORDER BY created_at DESC
                """;
        String term = search == null ? "" : search.trim().toLowerCase();
        String like = "%" + term + "%";
        List<AdminUserRow> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, term);
            statement.setString(2, like);
            statement.setString(3, like);
            statement.setString(4, like);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timestamp cat = rs.getTimestamp("created_at");
                    LocalDateTime created = cat != null ? cat.toLocalDateTime() : LocalDateTime.now();
                    rows.add(new AdminUserRow(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"),
                            Role.valueOf(rs.getString("role")), rs.getString("district"), rs.getBoolean("approved"),
                            rs.getBoolean("active"), created));
                }
            }
        }
        return rows;
    }

    public List<BloodRequest> findRequests(String search) throws SQLException {
        String sql = """
                SELECT br.id,br.requester_id,u.full_name AS requester_name,br.blood_group,br.units_needed,
                       br.urgency,br.hospital_name,br.district,br.deadline,br.notes,br.status,
                       br.accepted_donor_id,br.created_at,br.updated_at
                FROM blood_requests br JOIN users u ON u.id=br.requester_id
                WHERE (?='' OR LOWER(u.full_name) LIKE ? OR LOWER(br.hospital_name) LIKE ? OR LOWER(br.district) LIKE ?)
                ORDER BY br.created_at DESC
                """;
        String term = search == null ? "" : search.trim().toLowerCase();
        String like = "%" + term + "%";
        List<BloodRequest> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, term);
            statement.setString(2, like);
            statement.setString(3, like);
            statement.setString(4, like);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long donorId = rs.getLong("accepted_donor_id");
                    boolean donorIdWasNull = rs.wasNull();
                    Timestamp cat = rs.getTimestamp("created_at");
                    Timestamp uat = rs.getTimestamp("updated_at");
                    LocalDateTime created = cat != null ? cat.toLocalDateTime() : LocalDateTime.now();
                    LocalDateTime updated = uat != null ? uat.toLocalDateTime() : LocalDateTime.now();
                    rows.add(new BloodRequest(rs.getLong("id"), rs.getLong("requester_id"),
                            rs.getString("requester_name"),
                            BloodGroup.valueOf(rs.getString("blood_group")), rs.getInt("units_needed"),
                            Urgency.valueOf(rs.getString("urgency")), rs.getString("hospital_name"),
                            rs.getString("district"),
                            rs.getObject("deadline", LocalDate.class), rs.getString("notes"),
                            RequestStatus.valueOf(rs.getString("status")),
                            donorIdWasNull ? null : donorId, created, updated));
                }
            }
        }
        return rows;
    }

    public List<DemandRow> demandRows() throws SQLException {
        String sql = """
                SELECT bg.blood_group,
                       (SELECT COUNT(*)
                        FROM blood_requests br
                        WHERE br.blood_group = bg.blood_group
                          AND br.status IN ('PENDING', 'MATCHED', 'ESCALATED')
                       ) AS pending_requests,

                       (SELECT COUNT(*)
                        FROM donor_profiles d
                        JOIN users u ON u.id = d.user_id
                        WHERE d.blood_group = bg.blood_group
                          AND d.availability_status = 'AVAILABLE'
                          AND u.approved = TRUE
                          AND u.active = TRUE
                       ) AS available_donors

                FROM (
                    SELECT 'O_NEGATIVE' AS blood_group
                    UNION ALL SELECT 'O_POSITIVE'
                    UNION ALL SELECT 'A_NEGATIVE'
                    UNION ALL SELECT 'A_POSITIVE'
                    UNION ALL SELECT 'B_NEGATIVE'
                    UNION ALL SELECT 'B_POSITIVE'
                    UNION ALL SELECT 'AB_NEGATIVE'
                    UNION ALL SELECT 'AB_POSITIVE'
                ) AS bg

                ORDER BY CASE bg.blood_group
                    WHEN 'O_NEGATIVE' THEN 1
                    WHEN 'O_POSITIVE' THEN 2
                    WHEN 'A_NEGATIVE' THEN 3
                    WHEN 'A_POSITIVE' THEN 4
                    WHEN 'B_NEGATIVE' THEN 5
                    WHEN 'B_POSITIVE' THEN 6
                    WHEN 'AB_NEGATIVE' THEN 7
                    WHEN 'AB_POSITIVE' THEN 8
                    ELSE 9 END
                """;
        List<DemandRow> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next())
                rows.add(new DemandRow(BloodGroup.valueOf(rs.getString(1)), rs.getLong(2), rs.getLong(3)));
        }
        return rows;
    }

    public List<AuditEntry> auditEntries(int limit) throws SQLException {
        String sql = """
                SELECT a.id,a.actor_user_id,u.full_name actor_name,a.action,a.entity_type,a.entity_id,a.details,a.created_at
                FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_user_id ORDER BY a.created_at DESC LIMIT ?
                """;
        List<AuditEntry> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long actor = rs.getLong("actor_user_id");
                    boolean actorWasNull = rs.wasNull();
                    long entity = rs.getLong("entity_id");
                    boolean entityWasNull = rs.wasNull();
                    Timestamp cat = rs.getTimestamp("created_at");
                    LocalDateTime created = cat != null ? cat.toLocalDateTime() : LocalDateTime.now();
                    rows.add(new AuditEntry(rs.getLong("id"), actorWasNull ? null : actor, rs.getString("actor_name"),
                            rs.getString("action"), rs.getString("entity_type"), entityWasNull ? null : entity,
                            rs.getString("details"), created));
                }
            }
        }
        return rows;
    }
}
