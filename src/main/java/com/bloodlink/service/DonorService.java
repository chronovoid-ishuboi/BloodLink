package com.bloodlink.service;

import com.bloodlink.dao.DonorDAO;
import com.bloodlink.model.AvailabilityStatus;
import com.bloodlink.util.ValidationUtil;

import java.sql.SQLException;
import java.time.LocalDate;

public final class DonorService {
    private final DonorDAO donorDAO = new DonorDAO();

    public ServiceResult<Void> updateAvailability(long donorId, AvailabilityStatus status) {
        if (status == null) return ServiceResult.failure("Choose an availability status.");
        try {
            donorDAO.updateAvailability(donorId, status);
            return ServiceResult.success("Availability changed to " + status + ".", null);
        } catch (SQLException e) {
            return ServiceResult.failure("Availability could not be updated: " + e.getMessage());
        }
    }

    public ServiceResult<Void> updateHealth(long donorId, String weightText, LocalDate lastDonationDate) {
        try {
            double weight = Double.parseDouble(weightText.trim());
            if (weight < 35 || weight > 250) return ServiceResult.failure("Weight must be between 35 and 250 kg.");
            if (lastDonationDate != null && lastDonationDate.isAfter(LocalDate.now()))
                return ServiceResult.failure("Last donation date cannot be in the future.");
            donorDAO.updateHealthProfile(donorId, weight, lastDonationDate);
            return ServiceResult.success("Health and cooldown information updated.", null);
        } catch (NumberFormatException e) {
            return ServiceResult.failure("Enter a valid numeric weight.");
        } catch (SQLException e) {
            return ServiceResult.failure("Health profile could not be updated: " + e.getMessage());
        }
    }
}
