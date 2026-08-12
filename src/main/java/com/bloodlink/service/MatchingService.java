package com.bloodlink.service;

import com.bloodlink.dao.DonorDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.model.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MatchingService {
    private final DonorDAO donorDAO = new DonorDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final EligibilityService eligibilityService = new EligibilityService();

    public ServiceResult<List<MatchCandidate>> match(long requestId, long requesterId) {
        try {
            BloodRequest request = requestDAO.findById(requestId)
                    .orElseThrow(() -> new SQLException("Request not found."));
            if (request.requesterId() != requesterId) {
                throw new SQLException("You do not own this request.");
            }
            List<MatchCandidate> candidates = new ArrayList<>();
            for (Donor donor : donorDAO.findAvailableDonors()) {
                EligibilityService.EligibilityResult eligibility = eligibilityService.evaluate(donor);
                if (!eligibility.eligible() || !donor.getBloodGroup().canDonateTo(request.bloodGroup())) continue;
                double score = calculateScore(request, donor);
                String reason = buildReason(request, donor);
                candidates.add(new MatchCandidate(donor.getId(), donor.getFullName(), donor.getBloodGroup(),
                        donor.getDistrict(), donor.getPhone(), score, reason, donor.getAvailabilityStatus(), donor.getBadgeTier()));
            }
            candidates.sort(Comparator.comparingDouble(MatchCandidate::score).reversed()
                    .thenComparing(MatchCandidate::donorName));
            List<MatchCandidate> topCandidates = candidates.stream().limit(8).toList();
            requestDAO.saveMatches(requestId, requesterId, topCandidates);
            return ServiceResult.success(topCandidates.isEmpty() ? "No eligible donor is available yet."
                    : topCandidates.size() + " eligible donor(s) matched.", topCandidates);
        } catch (SQLException e) {
            return ServiceResult.failure("Matching failed: " + e.getMessage());
        }
    }

    private double calculateScore(BloodRequest request, Donor donor) {
        double score = 30;
        if (donor.getBloodGroup() == request.bloodGroup()) score += 20;
        if (donor.getDistrict().equalsIgnoreCase(request.district())) score += 35;
        if (donor.getLastDonationDate() == null) score += 10;
        else score += Math.min(10, ChronoUnit.DAYS.between(donor.getLastDonationDate(), LocalDate.now()) / 30.0);
        score += Math.min(5, donor.getVerifiedDonationCount() * 0.5);
        score += request.urgency().getWeight();
        return Math.round(score * 10.0) / 10.0;
    }

    private String buildReason(BloodRequest request, Donor donor) {
        List<String> reasons = new ArrayList<>();
        reasons.add(donor.getBloodGroup() == request.bloodGroup() ? "exact blood group" : "compatible blood group");
        reasons.add(donor.getDistrict().equalsIgnoreCase(request.district()) ? "same district" : "different district");
        reasons.add(donor.getLastDonationDate() == null ? "no recent donation" : "cooldown complete");
        if (donor.getVerifiedDonationCount() > 0) reasons.add(donor.getVerifiedDonationCount() + " verified donation(s)");
        return String.join(", ", reasons);
    }
}
