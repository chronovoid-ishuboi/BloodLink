package com.bloodlink.service;

import com.bloodlink.dao.UserDAO;
import com.bloodlink.model.User;
import com.bloodlink.util.PasswordUtil;
import com.bloodlink.util.ValidationUtil;

import java.sql.SQLException;
import java.util.List;

public final class ProfileService {
    private final UserDAO userDAO = new UserDAO();

    public ServiceResult<User> updateProfile(long userId, String fullName, String phone, String district, String address) {
        if (ValidationUtil.isBlank(fullName) || fullName.trim().length() < 3) return ServiceResult.failure("Enter your full name.");
        if (!ValidationUtil.isValidPhone(phone)) return ServiceResult.failure("Enter a valid Bangladeshi mobile number.");
        if (ValidationUtil.isBlank(district)) return ServiceResult.failure("District is required.");
        try {
            userDAO.updateProfile(userId, fullName, phone, district, address);
            User updated = userDAO.findById(userId).orElseThrow(() -> new SQLException("User not found after update."));
            return ServiceResult.success("Profile updated.", updated);
        } catch (SQLException e) {
            return ServiceResult.failure("Profile could not be updated: " + e.getMessage());
        }
    }

    public ServiceResult<Void> changePassword(long userId, String oldPassword, String newPassword, String confirmation) {
        if (ValidationUtil.isBlank(oldPassword)) return ServiceResult.failure("Enter your current password.");
        List<String> errors = ValidationUtil.validatePassword(newPassword);
        if (!errors.isEmpty()) return ServiceResult.failure(String.join("\n", errors));
        if (!newPassword.equals(confirmation)) return ServiceResult.failure("New password confirmation does not match.");
        try {
            if (!PasswordUtil.verify(oldPassword, userDAO.findPasswordHash(userId)))
                return ServiceResult.failure("Current password is incorrect.");
            userDAO.updatePassword(userId, PasswordUtil.hash(newPassword));
            return ServiceResult.success("Password changed securely.", null);
        } catch (SQLException e) {
            return ServiceResult.failure("Password could not be changed: " + e.getMessage());
        }
    }
}
