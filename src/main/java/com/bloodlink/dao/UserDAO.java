package com.bloodlink.dao;

import com.bloodlink.model.*;
import com.bloodlink.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public final class UserDAO {
    private static final String USER_SELECT = """
            SELECT u.id, u.full_name, u.email, u.password_hash, u.phone, u.district, u.address,
                   u.role, u.approved, u.active, u.created_at, u.nid_number, u.guardian_name, u.guardian_phone,
                   d.blood_group, d.birth_date, d.weight_kg, d.last_donation_date,
                   d.availability_status, d.verified_donation_count, d.reference_hospital_id,
                   d.height_cm, d.chronic_conditions, d.recent_surgery, d.recent_surgery_details,
                   d.recent_tattoo, d.recent_tattoo_details, d.current_medications, d.current_medications_details,
                   d.recent_illness, d.recent_illness_details, d.recent_pregnancy, d.recent_pregnancy_details
            FROM users u
            LEFT JOIN donor_profiles d ON d.user_id = u.id
            """;

    public Optional<User> findByEmail(String email) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(USER_SELECT + " WHERE LOWER(u.email) = LOWER(?)")) {
            statement.setString(1, email.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        }
    }

    public Optional<User> findById(long id) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(USER_SELECT + " WHERE u.id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        }
    }

    public String findPasswordHash(long userId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT password_hash FROM users WHERE id = ?")) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new SQLException("User not found.");
                return rs.getString(1);
            }
        }
    }

    public boolean emailExists(String email) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM users WHERE LOWER(email)=LOWER(?)")) {
            statement.setString(1, email.trim());
            try (ResultSet rs = statement.executeQuery()) { return rs.next(); }
        }
    }

    /**
     * Deliberately separate from USER_SELECT above: photo bytes are never part of the
     * routine user/donor fetch used for matching, search, or dashboard lists -- only
     * called once, explicitly, when a specific profile is actually being displayed.
     */
    public Optional<byte[]> findPhoto(long userId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT photo FROM users WHERE id=?")) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.ofNullable(rs.getBytes("photo"));
            }
        }
    }

    /** Pass null to remove an existing photo. Size limits are enforced by the caller (ProfileService), not here. */
    public void updatePhoto(long userId, byte[] photoBytes) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET photo=? WHERE id=?")) {
                if (photoBytes == null) statement.setNull(1, Types.BLOB); else statement.setBytes(1, photoBytes);
                statement.setLong(2, userId);
                if (statement.executeUpdate() == 0) throw new SQLException("User not found.");
                new AuditDAO().log(connection, userId, "UPDATE_PHOTO", "USER", userId,
                        photoBytes == null ? "Photo removed" : "Photo updated (" + photoBytes.length + " bytes)");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public long register(RegistrationData data, String passwordHash) throws SQLException {
        String userSql = """
                INSERT INTO users(full_name, email, password_hash, phone, district, address, role, approved, active, nid_number, guardian_name, guardian_phone, photo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?)
                """;
        String donorSql = """
                INSERT INTO donor_profiles(user_id, blood_group, birth_date, weight_kg, last_donation_date,
                                           availability_status, verified_donation_count, height_cm, chronic_conditions,
                                           recent_surgery, recent_surgery_details, recent_tattoo, recent_tattoo_details,
                                           current_medications, current_medications_details, recent_illness, recent_illness_details,
                                           recent_pregnancy, recent_pregnancy_details)
                VALUES (?, ?, ?, ?, ?, 'BUSY', 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement statement = connection.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, data.fullName().trim());
                    statement.setString(2, data.email().trim().toLowerCase());
                    statement.setString(3, passwordHash);
                    statement.setString(4, data.phone().trim());
                    statement.setString(5, data.district().trim());
                    statement.setString(6, data.address() == null ? "" : data.address().trim());
                    statement.setString(7, data.role().name());
                    statement.setBoolean(8, data.role() == Role.REQUESTER);
                    statement.setString(9, data.nidNumber() == null ? null : data.nidNumber().trim());
                    statement.setString(10, data.guardianName() == null ? null : data.guardianName().trim());
                    statement.setString(11, data.guardianPhone() == null ? null : data.guardianPhone().trim());
                    if (data.profilePhoto() == null) statement.setNull(12, Types.BLOB); else statement.setBytes(12, data.profilePhoto());
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Registration did not return a user ID.");
                        id = keys.getLong(1);
                    }
                }
                if (data.role() == Role.DONOR) {
                    try (PreparedStatement statement = connection.prepareStatement(donorSql)) {
                        statement.setLong(1, id);
                        statement.setString(2, data.bloodGroup().name());
                        statement.setObject(3, data.birthDate());
                        statement.setDouble(4, data.weightKg());
                        statement.setObject(5, data.lastDonationDate());
                        if (data.heightCm() != null) statement.setDouble(6, data.heightCm()); else statement.setNull(6, Types.DECIMAL);
                        statement.setString(7, data.chronicConditions());
                        statement.setBoolean(8, data.recentSurgery());
                        statement.setString(9, data.recentSurgeryDetails());
                        statement.setBoolean(10, data.recentTattoo());
                        statement.setString(11, data.recentTattooDetails());
                        statement.setBoolean(12, data.currentMedications());
                        statement.setString(13, data.currentMedicationsDetails());
                        statement.setBoolean(14, data.recentIllness());
                        statement.setString(15, data.recentIllnessDetails());
                        statement.setBoolean(16, data.recentPregnancy());
                        statement.setString(17, data.recentPregnancyDetails());
                        statement.executeUpdate();
                    }
                }
                new AuditDAO().log(connection, id, "REGISTER", "USER", id, "Registered as " + data.role());
                connection.commit();
                return id;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void updateProfile(long userId, String fullName, String phone, String district, String address, String guardianName, String guardianPhone) throws SQLException {
        String sql = "UPDATE users SET full_name=?, phone=?, district=?, address=?, guardian_name=?, guardian_phone=? WHERE id=?";
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, fullName.trim());
                statement.setString(2, phone.trim());
                statement.setString(3, district.trim());
                statement.setString(4, address == null ? "" : address.trim());
                statement.setString(5, guardianName == null ? "" : guardianName.trim());
                statement.setString(6, guardianPhone == null ? "" : guardianPhone.trim());
                statement.setLong(7, userId);
                if (statement.executeUpdate() == 0) throw new SQLException("User profile not found.");
                new AuditDAO().log(connection, userId, "UPDATE_PROFILE", "USER", userId, "Contact profile updated");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void updatePassword(long userId, String passwordHash) throws SQLException {
        updatePassword(userId, passwordHash, userId, "CHANGE_PASSWORD");
    }

    public void resetPassword(long userId, String passwordHash, long actorId) throws SQLException {
        updatePassword(userId, passwordHash, actorId, "ADMIN_RESET_PASSWORD");
    }

    private void updatePassword(long userId, String passwordHash, long actorId, String action) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET password_hash=? WHERE id=?")) {
                statement.setString(1, passwordHash);
                statement.setLong(2, userId);
                if (statement.executeUpdate() == 0) throw new SQLException("User not found.");
                new AuditDAO().log(connection, actorId, action, "USER", userId, "Password hash replaced");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void setApproved(long userId, boolean approved, long actorId) throws SQLException {
        updateFlag(userId, "approved", approved, actorId, approved ? "APPROVE_USER" : "REVOKE_APPROVAL");
    }

    public void setActive(long userId, boolean active, long actorId) throws SQLException {
        updateFlag(userId, "active", active, actorId, active ? "ACTIVATE_USER" : "SUSPEND_USER");
    }

    private void updateFlag(long userId, String column, boolean value, long actorId, String action) throws SQLException {
        String sql = "UPDATE users SET " + column + "=? WHERE id=? AND role <> 'ADMIN'";
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBoolean(1, value);
                statement.setLong(2, userId);
                int changed = statement.executeUpdate();
                if (changed == 0) throw new SQLException("User could not be updated.");
                new AuditDAO().log(connection, actorId, action, "USER", userId, column + "=" + value);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String name = rs.getString("full_name");
        String email = rs.getString("email");
        String phone = rs.getString("phone");
        String district = rs.getString("district");
        String address = rs.getString("address");
        Role role = Role.valueOf(rs.getString("role"));
        boolean approved = rs.getBoolean("approved");
        boolean active = rs.getBoolean("active");
        LocalDateTime created = rs.getTimestamp("created_at").toLocalDateTime();
        String nidNumber = rs.getString("nid_number");
        String guardianName = rs.getString("guardian_name");
        String guardianPhone = rs.getString("guardian_phone");
        return switch (role) {
            case DONOR -> {
                long referenceHospitalIdValue = rs.getLong("reference_hospital_id");
                boolean referenceHospitalIdWasNull = rs.wasNull();
                Double heightCmValue = rs.getDouble("height_cm");
                boolean heightCmWasNull = rs.wasNull();
                yield new Donor(id, name, email, phone, district, address, approved, active, created, nidNumber, guardianName, guardianPhone,
                        BloodGroup.valueOf(rs.getString("blood_group")), rs.getObject("birth_date", LocalDate.class),
                        rs.getDouble("weight_kg"), rs.getObject("last_donation_date", LocalDate.class),
                        AvailabilityStatus.valueOf(rs.getString("availability_status")),
                        rs.getInt("verified_donation_count"),
                        referenceHospitalIdWasNull ? null : referenceHospitalIdValue,
                        heightCmWasNull ? null : heightCmValue,
                        rs.getString("chronic_conditions"),
                        rs.getBoolean("recent_surgery"),
                        rs.getString("recent_surgery_details"),
                        rs.getBoolean("recent_tattoo"),
                        rs.getString("recent_tattoo_details"),
                        rs.getBoolean("current_medications"),
                        rs.getString("current_medications_details"),
                        rs.getBoolean("recent_illness"),
                        rs.getString("recent_illness_details"),
                        rs.getBoolean("recent_pregnancy"),
                        rs.getString("recent_pregnancy_details"));
            }
            case REQUESTER -> new Requester(id, name, email, phone, district, address, approved, active, created, nidNumber, guardianName, guardianPhone);
            case ADMIN -> new Admin(id, name, email, phone, district, address, approved, active, created, nidNumber, guardianName, guardianPhone);
        };
    }
}
