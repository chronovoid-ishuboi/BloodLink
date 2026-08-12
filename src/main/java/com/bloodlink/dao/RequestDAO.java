package com.bloodlink.dao;

import com.bloodlink.model.*;
import com.bloodlink.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RequestDAO {
    public long create(long requesterId, BloodGroup bloodGroup, int units, Urgency urgency,
                       String hospital, String district, LocalDate deadline, String notes) throws SQLException {
        String sql = """
                INSERT INTO blood_requests(requester_id,blood_group,units_needed,urgency,hospital_name,district,deadline,notes,status)
                VALUES(?,?,?,?,?,?,?,?, 'PENDING')
                """;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, requesterId);
                statement.setString(2, bloodGroup.name());
                statement.setInt(3, units);
                statement.setString(4, urgency.name());
                statement.setString(5, hospital.trim());
                statement.setString(6, district.trim());
                statement.setObject(7, deadline);
                statement.setString(8, notes == null ? "" : notes.trim());
                statement.executeUpdate();
                long id;
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Request ID was not generated.");
                    id = keys.getLong(1);
                }
                insertHistory(connection, id, null, RequestStatus.PENDING, requesterId, "Emergency request submitted");
                new AuditDAO().log(connection, requesterId, "CREATE_REQUEST", "BLOOD_REQUEST", id, bloodGroup + ", " + units + " unit(s)");
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

    public Optional<BloodRequest> findById(long requestId) throws SQLException {
        String sql = baseSelect() + " WHERE br.id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet rs = statement.executeQuery()) { return rs.next() ? Optional.of(mapRequest(rs)) : Optional.empty(); }
        }
    }

    public List<BloodRequest> findByRequester(long requesterId) throws SQLException {
        String sql = baseSelect() + " WHERE br.requester_id=? ORDER BY br.created_at DESC";
        List<BloodRequest> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requesterId);
            try (ResultSet rs = statement.executeQuery()) { while (rs.next()) rows.add(mapRequest(rs)); }
        }
        return rows;
    }

    public List<RequestStatusHistoryEntry> findStatusHistory(long requestId, long requesterId) throws SQLException {
        String sql = """
                SELECT h.id,h.request_id,h.from_status,h.to_status,u.full_name changed_by_name,h.note,h.changed_at
                FROM request_status_history h
                JOIN blood_requests br ON br.id=h.request_id
                LEFT JOIN users u ON u.id=h.changed_by
                WHERE h.request_id=? AND br.requester_id=?
                ORDER BY h.changed_at DESC,h.id DESC
                """;
        List<RequestStatusHistoryEntry> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            statement.setLong(2, requesterId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String from = rs.getString("from_status");
                    rows.add(new RequestStatusHistoryEntry(rs.getLong("id"), rs.getLong("request_id"),
                            from == null ? null : RequestStatus.valueOf(from),
                            RequestStatus.valueOf(rs.getString("to_status")),
                            rs.getString("changed_by_name"), rs.getString("note"),
                            rs.getTimestamp("changed_at").toLocalDateTime()));
                }
            }
        }
        return rows;
    }

    public List<MatchCandidate> findMatchesForRequest(long requestId) throws SQLException {
        String sql = """
                SELECT rm.donor_id,u.full_name,d.blood_group,u.district,u.phone,rm.match_score,rm.match_reason,
                       d.availability_status,d.verified_donation_count
                FROM request_matches rm
                JOIN users u ON u.id=rm.donor_id
                JOIN donor_profiles d ON d.user_id=rm.donor_id
                WHERE rm.request_id=? ORDER BY rm.match_score DESC
                """;
        List<MatchCandidate> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) rows.add(new MatchCandidate(rs.getLong("donor_id"), rs.getString("full_name"),
                        BloodGroup.valueOf(rs.getString("blood_group")), rs.getString("district"), rs.getString("phone"),
                        rs.getDouble("match_score"), rs.getString("match_reason"),
                        AvailabilityStatus.valueOf(rs.getString("availability_status")),
                        BadgeTier.fromDonationCount(rs.getInt("verified_donation_count"))));
            }
        }
        return rows;
    }

    public List<DonorMatchView> findMatchesForDonor(long donorId) throws SQLException {
        String sql = """
                SELECT br.id,br.blood_group,br.hospital_name,br.district,br.urgency,br.deadline,br.status,
                       rm.status AS match_status,rm.match_score
                FROM request_matches rm JOIN blood_requests br ON br.id=rm.request_id
                WHERE rm.donor_id=? AND br.status NOT IN ('FULFILLED','CANCELLED')
                ORDER BY CASE br.urgency WHEN 'CRITICAL' THEN 1 WHEN 'URGENT' THEN 2 WHEN 'NORMAL' THEN 3 ELSE 4 END, br.deadline, rm.match_score DESC
                """;
        List<DonorMatchView> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) rows.add(new DonorMatchView(rs.getLong("id"), BloodGroup.valueOf(rs.getString("blood_group")),
                        rs.getString("hospital_name"), rs.getString("district"), Urgency.valueOf(rs.getString("urgency")),
                        rs.getObject("deadline", LocalDate.class), RequestStatus.valueOf(rs.getString("status")),
                        MatchStatus.valueOf(rs.getString("match_status")), rs.getDouble("match_score")));
            }
        }
        return rows;
    }

    public void saveMatches(long requestId, long requesterId, List<MatchCandidate> candidates) throws SQLException {
        String delete = "DELETE FROM request_matches WHERE request_id=? AND status <> 'ACCEPTED'";
        String insert = """
                INSERT INTO request_matches(request_id,donor_id,match_score,match_reason,status)
                VALUES(?,?,?,?, 'NOTIFIED')
                """;
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (request.requesterId() != requesterId) throw new SQLException("You do not own this request.");
                if (!(request.status() == RequestStatus.PENDING || request.status() == RequestStatus.MATCHED
                        || request.status() == RequestStatus.DECLINED || request.status() == RequestStatus.ESCALATED)) {
                    throw new SQLException("Only pending, matched, declined, or escalated requests can be matched again.");
                }
                try (PreparedStatement statement = connection.prepareStatement(delete)) {
                    statement.setLong(1, requestId);
                    statement.executeUpdate();
                }
                for (MatchCandidate candidate : candidates) {
                    try (PreparedStatement statement = connection.prepareStatement(insert)) {
                        statement.setLong(1, requestId);
                        statement.setLong(2, candidate.donorId());
                        statement.setDouble(3, candidate.score());
                        statement.setString(4, candidate.reason());
                        statement.executeUpdate();
                    }
                    new NotificationDAO().create(connection, candidate.donorId(), "Urgent blood match",
                            "Request #" + requestId + " matches your profile. Review and respond.", "MATCH", requestId);
                }
                RequestStatus targetStatus;
                if (candidates.isEmpty()) {
                    targetStatus = request.status() == RequestStatus.ESCALATED ? RequestStatus.ESCALATED : RequestStatus.PENDING;
                } else {
                    targetStatus = RequestStatus.MATCHED;
                }
                if (targetStatus != request.status()) {
                    updateStatus(connection, requestId, targetStatus, null);
                    insertHistory(connection, requestId, request.status(), targetStatus, requesterId,
                            candidates.isEmpty() ? "No eligible donor found during rematch"
                                    : candidates.size() + " eligible donor(s) matched");
                }
                if (!candidates.isEmpty()) {
                    new NotificationDAO().create(connection, requesterId, "Donors matched",
                            candidates.size() + " eligible donor(s) were ranked for request #" + requestId + ".", "MATCH", requestId);
                } else {
                    new NotificationDAO().create(connection, requesterId, "No eligible donor yet",
                            "Request #" + requestId + " remains open and can be matched again later.", "MATCH", requestId);
                }
                new AuditDAO().log(connection, requesterId, "RUN_MATCHING", "BLOOD_REQUEST", requestId,
                        candidates.size() + " candidate(s) saved");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void acceptMatch(long requestId, long donorId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (!(request.status() == RequestStatus.MATCHED || request.status() == RequestStatus.ESCALATED))
                    throw new SQLException("This request is not accepting donor responses.");
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE request_matches SET status='ACCEPTED',responded_at=CURRENT_TIMESTAMP " +
                                "WHERE request_id=? AND donor_id=? AND status='NOTIFIED'")) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, donorId);
                    if (statement.executeUpdate() == 0) throw new SQLException("This match is expired or was already answered.");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE request_matches SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP " +
                                "WHERE request_id=? AND donor_id<>? AND status='NOTIFIED'")) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, donorId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE blood_requests SET status='ACCEPTED',accepted_donor_id=? WHERE id=?")) {
                    statement.setLong(1, donorId);
                    statement.setLong(2, requestId);
                    statement.executeUpdate();
                }
                insertHistory(connection, requestId, request.status(), RequestStatus.ACCEPTED, donorId, "Donor accepted the request");
                new NotificationDAO().create(connection, request.requesterId(), "Donor accepted",
                        "A donor accepted request #" + requestId + ". Open the matched donor list for contact details.", "RESPONSE", requestId);
                new NotificationDAO().create(connection, donorId, "Response confirmed",
                        "You accepted request #" + requestId + ". Please coordinate with the requester.", "RESPONSE", requestId);
                new AuditDAO().log(connection, donorId, "ACCEPT_MATCH", "BLOOD_REQUEST", requestId, "Donor accepted match");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void declineMatch(long requestId, long donorId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (!(request.status() == RequestStatus.MATCHED || request.status() == RequestStatus.ESCALATED))
                    throw new SQLException("This request is not accepting donor responses.");
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE request_matches SET status='DECLINED',responded_at=CURRENT_TIMESTAMP WHERE request_id=? AND donor_id=? AND status='NOTIFIED'")) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, donorId);
                    if (statement.executeUpdate() == 0) throw new SQLException("This match is expired or was already answered.");
                }
                long remaining;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM request_matches WHERE request_id=? AND status='NOTIFIED'")) {
                    statement.setLong(1, requestId);
                    try (ResultSet rs = statement.executeQuery()) { rs.next(); remaining = rs.getLong(1); }
                }
                if (remaining == 0) {
                    updateStatus(connection, requestId, RequestStatus.DECLINED, null);
                    insertHistory(connection, requestId, request.status(), RequestStatus.DECLINED, donorId,
                            "All notified donors declined or expired");
                }
                new NotificationDAO().create(connection, request.requesterId(), "Donor declined",
                        remaining == 0
                                ? "All current matches declined request #" + requestId + ". Run matching again or ask an admin to escalate it."
                                : "One donor declined request #" + requestId + ". " + remaining + " response(s) remain pending.",
                        "RESPONSE", requestId);
                new AuditDAO().log(connection, donorId, "DECLINE_MATCH", "BLOOD_REQUEST", requestId,
                        remaining + " notified match(es) remain");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void fulfill(long requestId, long requesterId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (request.requesterId() != requesterId) throw new SQLException("You do not own this request.");
                if (request.status() != RequestStatus.ACCEPTED || request.acceptedDonorId() == null)
                    throw new SQLException("Only an accepted request can be marked fulfilled.");
                updateStatus(connection, requestId, RequestStatus.FULFILLED, request.acceptedDonorId());
                insertHistory(connection, requestId, request.status(), RequestStatus.FULFILLED, requesterId, "Requester confirmed fulfillment");
                String donationSql = """
                        INSERT INTO donation_history(donor_id,request_id,donation_date,hospital_name,blood_group,units,verified)
                        VALUES(?,?,CURRENT_DATE,?,?,?,TRUE)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(donationSql)) {
                    statement.setLong(1, request.acceptedDonorId());
                    statement.setLong(2, requestId);
                    statement.setString(3, request.hospitalName());
                    statement.setString(4, request.bloodGroup().name());
                    statement.setInt(5, request.unitsNeeded());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE donor_profiles SET last_donation_date=CURRENT_DATE,verified_donation_count=verified_donation_count+1,availability_status='BUSY' WHERE user_id=?")) {
                    statement.setLong(1, request.acceptedDonorId());
                    statement.executeUpdate();
                }
                new NotificationDAO().create(connection, request.acceptedDonorId(), "Donation verified",
                        "Request #" + requestId + " was fulfilled. Your donation count and cooldown were updated.", "FULFILLED", requestId);
                new AuditDAO().log(connection, requesterId, "FULFILL_REQUEST", "BLOOD_REQUEST", requestId, "Donation verified");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void cancel(long requestId, long requesterId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (request.requesterId() != requesterId) throw new SQLException("You do not own this request.");
                if (request.status() == RequestStatus.FULFILLED || request.status() == RequestStatus.CANCELLED)
                    throw new SQLException("This request is already closed.");
                updateStatus(connection, requestId, RequestStatus.CANCELLED, request.acceptedDonorId());
                expireOpenMatches(connection, requestId);
                insertHistory(connection, requestId, request.status(), RequestStatus.CANCELLED, requesterId,
                        "Requester cancelled the request");
                if (request.acceptedDonorId() != null) {
                    new NotificationDAO().create(connection, request.acceptedDonorId(), "Request cancelled",
                            "The requester cancelled request #" + requestId + ".", "CANCELLED", requestId);
                }
                new AuditDAO().log(connection, requesterId, "CANCEL_REQUEST", "BLOOD_REQUEST", requestId,
                        "Requester cancelled the request");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void adminTransition(long requestId, long adminId, RequestStatus target, String note) throws SQLException {
        if (target != RequestStatus.ESCALATED && target != RequestStatus.CANCELLED)
            throw new IllegalArgumentException("Unsupported admin transition.");
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BloodRequest request = lockRequest(connection, requestId);
                if (request.status() == RequestStatus.FULFILLED || request.status() == RequestStatus.CANCELLED)
                    throw new SQLException("Closed requests cannot be changed.");
                if (target == RequestStatus.ESCALATED && request.status() == RequestStatus.ACCEPTED)
                    throw new SQLException("An accepted request does not need escalation.");
                updateStatus(connection, requestId, target, request.acceptedDonorId());
                if (target == RequestStatus.CANCELLED) expireOpenMatches(connection, requestId);
                insertHistory(connection, requestId, request.status(), target, adminId, note);
                new NotificationDAO().create(connection, request.requesterId(), "Request updated by admin",
                        "Request #" + requestId + " is now " + target + ".", "ADMIN", requestId);
                if (target == RequestStatus.CANCELLED && request.acceptedDonorId() != null) {
                    new NotificationDAO().create(connection, request.acceptedDonorId(), "Request closed by admin",
                            "Request #" + requestId + " was closed by an administrator.", "ADMIN", requestId);
                }
                new AuditDAO().log(connection, adminId, "ADMIN_" + target, "BLOOD_REQUEST", requestId, note);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private void expireOpenMatches(Connection connection, long requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE request_matches SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP " +
                        "WHERE request_id=? AND status='NOTIFIED'")) {
            statement.setLong(1, requestId);
            statement.executeUpdate();
        }
    }

    private BloodRequest lockRequest(Connection connection, long requestId) throws SQLException {
        String sql = baseSelect() + " WHERE br.id=? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new SQLException("Request not found.");
                return mapRequest(rs);
            }
        }
    }

    private void updateStatus(Connection connection, long requestId, RequestStatus status, Long donorId) throws SQLException {
        String sql = "UPDATE blood_requests SET status=?,accepted_donor_id=? WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            if (donorId == null) statement.setNull(2, Types.BIGINT); else statement.setLong(2, donorId);
            statement.setLong(3, requestId);
            statement.executeUpdate();
        }
    }

    private void insertHistory(Connection connection, long requestId, RequestStatus from, RequestStatus to,
                               Long changedBy, String note) throws SQLException {
        String sql = "INSERT INTO request_status_history(request_id,from_status,to_status,changed_by,note) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            if (from == null) statement.setNull(2, Types.VARCHAR); else statement.setString(2, from.name());
            statement.setString(3, to.name());
            if (changedBy == null) statement.setNull(4, Types.BIGINT); else statement.setLong(4, changedBy);
            statement.setString(5, note);
            statement.executeUpdate();
        }
    }

    private String baseSelect() {
        return """
                SELECT br.id,br.requester_id,u.full_name AS requester_name,br.blood_group,br.units_needed,
                       br.urgency,br.hospital_name,br.district,br.deadline,br.notes,br.status,
                       br.accepted_donor_id,br.created_at,br.updated_at
                FROM blood_requests br JOIN users u ON u.id=br.requester_id
                """;
    }

    private BloodRequest mapRequest(ResultSet rs) throws SQLException {
        long donorId = rs.getLong("accepted_donor_id");
        boolean donorIdWasNull = rs.wasNull();
        return new BloodRequest(rs.getLong("id"), rs.getLong("requester_id"), rs.getString("requester_name"),
                BloodGroup.valueOf(rs.getString("blood_group")), rs.getInt("units_needed"),
                Urgency.valueOf(rs.getString("urgency")), rs.getString("hospital_name"), rs.getString("district"),
                rs.getObject("deadline", LocalDate.class), rs.getString("notes"), RequestStatus.valueOf(rs.getString("status")),
                donorIdWasNull ? null : donorId, rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
