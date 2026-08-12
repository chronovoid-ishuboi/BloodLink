package com.bloodlink.dao;

import com.bloodlink.model.Notification;
import com.bloodlink.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class NotificationDAO {
    public List<Notification> findByUser(long userId, int limit) throws SQLException {
        String sql = """
                SELECT id, user_id, title, message, type, related_request_id, is_read, created_at
                FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT ?
                """;
        List<Notification> notifications = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) notifications.add(map(rs));
            }
        }
        return notifications;
    }

    public long unreadCount(long userId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=FALSE")) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    public void markRead(long notificationId, long userId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE notifications SET is_read=TRUE WHERE id=? AND user_id=?")) {
            statement.setLong(1, notificationId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    public void markAllRead(long userId) throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE notifications SET is_read=TRUE WHERE user_id=?")) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    public void create(long userId, String title, String message, String type, Long requestId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            create(connection, userId, title, message, type, requestId);
        }
    }

    public void create(Connection connection, long userId, String title, String message,
                       String type, Long requestId) throws SQLException {
        String sql = "INSERT INTO notifications(user_id,title,message,type,related_request_id) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, title);
            statement.setString(3, message);
            statement.setString(4, type);
            if (requestId == null) statement.setNull(5, Types.BIGINT); else statement.setLong(5, requestId);
            statement.executeUpdate();
        }
    }

    private Notification map(ResultSet rs) throws SQLException {
        long requestId = rs.getLong("related_request_id");
        boolean requestIdWasNull = rs.wasNull();
        return new Notification(rs.getLong("id"), rs.getLong("user_id"), rs.getString("title"),
                rs.getString("message"), rs.getString("type"), requestIdWasNull ? null : requestId,
                rs.getBoolean("is_read"), rs.getTimestamp("created_at").toLocalDateTime());
    }
}
