package com.bloodlink.service;

import com.bloodlink.dao.DonorDAO;
import com.bloodlink.dao.UserDAO;
import com.bloodlink.model.*;
import com.bloodlink.util.SessionManager;

import java.sql.SQLException;
import java.util.List;

/**
 * Builds the read-only {@link DonorProfileView} shown by the donor profile
 * dialog. Additive and read-only: it composes existing DAO and service reads and
 * writes nothing, so matching, eligibility, the handshake and reviews are
 * untouched.
 *
 * <h2>Who may see a donor's contact details</h2>
 * A phone number is the one genuinely sensitive field on this screen, so the
 * decision is made <b>here, from the live session</b>, and never from a flag the
 * calling screen passes in. A screen cannot grant itself access, and a future
 * caller cannot get it wrong by omission.
 * <p>
 * The rule:
 * <ul>
 *   <li><b>Admins</b> see full contact details. They already can -- the admin
 *       Users table lists every user's email -- so the profile dialog is not a
 *       new disclosure. The claim is verified against the database via
 *       {@link AuthorizationService#requireAdmin}, not taken from the session
 *       object alone.</li>
 *   <li><b>A donor viewing their own profile</b> sees their own details.</li>
 *   <li><b>A requester</b> sees contact details only once that donor has
 *       {@link MatchStatus#ACCEPTED} their request. Being ranked as a candidate
 *       is not consent to be phoned.</li>
 *   <li><b>Everyone else</b> gets a locked contact block explaining when it
 *       unlocks.</li>
 * </ul>
 * Note that {@link MatchCandidate} already carries {@code phone} for every
 * candidate regardless of status; it simply was never rendered. This class is
 * what makes that restraint an enforced rule rather than an accident of the
 * previous UI never having a place to show it.
 */
public final class DonorProfileService {
    private final UserDAO userDAO = new UserDAO();
    private final DonorDAO donorDAO = new DonorDAO();
    private final ReviewService reviewService = new ReviewService();
    private final AuthorizationService authorizationService = new AuthorizationService();

    /** How many reviews the profile dialog shows at once. */
    private static final int REVIEW_LIMIT = 20;

    /**
     * Loads a donor's profile for the currently signed-in viewer.
     *
     * @param donorId     the donor to display
     * @param matchStatus this donor's match status on the request the viewer is
     *                     looking at, or {@code null} when opened outside a match
     *                     context (e.g. from the admin Users table)
     * @param distanceKm  distance from the match context, or {@code null}
     */
    public ServiceResult<DonorProfileView> load(long donorId, MatchStatus matchStatus, Double distanceKm) {
        try {
            User user = userDAO.findById(donorId).orElse(null);
            if (user == null) return ServiceResult.failure("That donor account no longer exists.");
            if (!(user instanceof Donor donor)) return ServiceResult.failure("That account is not a donor.");

            ReputationSummary reputation = reviewService.reputationOf(donorId);
            List<DonationRecord> donations = donorDAO.findDonationHistory(donorId);
            List<Review> reviews = reviewService.reviewsReceivedBy(donorId, REVIEW_LIMIT);

            boolean contactVisible = maySeeContact(donorId, matchStatus);
            return ServiceResult.success("Profile loaded.", new DonorProfileView(
                    donor.getId(), donor.getFullName(), donor.getBloodGroup(), donor.getDistrict(),
                    contactVisible ? donor.getAddress() : null,
                    donor.getAvailabilityStatus(), donor.getBadgeTier(), donor.getVerifiedDonationCount(),
                    donor.getCreatedAt(),
                    reputation.hasReviews() ? reputation.averageRating() : null, reputation.reviewCount(),
                    distanceKm,
                    contactVisible ? donor.getPhone() : null,
                    contactVisible ? donor.getEmail() : null,
                    contactVisible, contactVisible ? null : lockedReason(matchStatus),
                    donations, reviews));
        } catch (SQLException e) {
            return ServiceResult.failure("Could not load this donor's profile: " + e.getMessage());
        }
    }

    private boolean maySeeContact(long donorId, MatchStatus matchStatus) {
        User viewer = SessionManager.getInstance().getCurrentUser();
        if (viewer == null) return false;
        if (viewer.getId() == donorId) return true;
        if (viewer.getRole() == Role.ADMIN) {
            try {
                authorizationService.requireAdmin(viewer.getId());
                return true;
            } catch (SQLException e) {
                // The session claims admin but the database disagrees (suspended,
                // demoted, deleted). Fail closed.
                return false;
            }
        }
        return matchStatus == MatchStatus.ACCEPTED;
    }

    private String lockedReason(MatchStatus matchStatus) {
        if (matchStatus == null) {
            return "Contact details are shared once this donor accepts a request of yours.";
        }
        return switch (matchStatus) {
            case NOTIFIED -> "This donor has been notified. Their contact details unlock as soon as they accept.";
            case DECLINED -> "This donor declined this request, so their contact details stay private.";
            case EXPIRED -> "This match expired before the donor responded, so their contact details stay private.";
            default -> "Contact details are shared once this donor accepts your request.";
        };
    }
}
