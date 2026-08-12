package com.bloodlink.service;

import com.bloodlink.dao.RequestDAO;
import com.bloodlink.model.*;
import com.bloodlink.util.ValidationUtil;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public final class RequestService {
    private final RequestDAO requestDAO = new RequestDAO();
    private final MatchingService matchingService = new MatchingService();

    public ServiceResult<Long> create(long requesterId, BloodGroup group, int units, Urgency urgency,
                                      String hospital, String district, LocalDate deadline, String notes) {
        if (group == null || urgency == null) return ServiceResult.failure("Choose a blood group and urgency.");
        if (units < 1 || units > 20) return ServiceResult.failure("Units needed must be between 1 and 20.");
        if (ValidationUtil.isBlank(hospital) || ValidationUtil.isBlank(district))
            return ServiceResult.failure("Hospital and district are required.");
        if (deadline == null || deadline.isBefore(LocalDate.now()))
            return ServiceResult.failure("Deadline must be today or a future date.");
        try {
            long id = requestDAO.create(requesterId, group, units, urgency, hospital, district, deadline, notes);
            ServiceResult<List<MatchCandidate>> matching = matchingService.match(id, requesterId);
            String message = "Request #" + id + " created. " + matching.message();
            return ServiceResult.success(message, id);
        } catch (SQLException e) {
            return ServiceResult.failure("Request could not be created: " + e.getMessage());
        }
    }

    public ServiceResult<Void> accept(long requestId, long donorId) {
        return execute(() -> requestDAO.acceptMatch(requestId, donorId), "Request accepted.");
    }

    public ServiceResult<Void> decline(long requestId, long donorId) {
        return execute(() -> requestDAO.declineMatch(requestId, donorId), "Match declined.");
    }

    public ServiceResult<Void> fulfill(long requestId, long requesterId) {
        return execute(() -> requestDAO.fulfill(requestId, requesterId), "Request marked fulfilled and donation verified.");
    }

    public ServiceResult<Void> cancel(long requestId, long requesterId) {
        return execute(() -> requestDAO.cancel(requestId, requesterId), "Request cancelled.");
    }

    public ServiceResult<Void> adminTransition(long requestId, long adminId, RequestStatus target, String note) {
        return execute(() -> requestDAO.adminTransition(requestId, adminId, target, note), "Request updated to " + target + ".");
    }

    private ServiceResult<Void> execute(SqlAction action, String successMessage) {
        try {
            action.run();
            return ServiceResult.success(successMessage, null);
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    @FunctionalInterface private interface SqlAction { void run() throws SQLException; }
}
