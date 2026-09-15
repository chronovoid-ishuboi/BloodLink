package com.bloodlink.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the donor profile screen shows about one donor, gathered in a
 * single read-only value so the UI never has to fan out across DAOs itself.
 * <p>
 * Purely an aggregation of data that already existed: identity and availability
 * from {@link Donor}, reputation from {@code ReviewService.reputationOf}, the
 * review list from {@code ReviewService.reviewsReceivedBy}, and the donation
 * list from {@code DonorDAO.findDonationHistory}. Nothing here is derived,
 * scored, or written back, and no matching or eligibility logic is involved.
 *
 * @param averageRating   Average from verified-donation reviews, or {@code null}
 *                         when this donor has none yet. Follow the rule
 *                         {@link MatchCandidate} already documents: render
 *                         {@code null} as "No reviews yet", never as 0 stars.
 * @param reviewCount     How many reviews {@code averageRating} is based on.
 * @param distanceKm      Distance to the request's hospital when the profile was
 *                         opened from a match, {@code null} otherwise. Never
 *                         fabricated -- render {@code null} as unavailable.
 * @param phone           The donor's phone, or {@code null} when the viewer is
 *                         not allowed to see it. See {@link #contactVisible}.
 * @param email           The donor's email, under the same rule as {@code phone}.
 * @param contactVisible  Whether this viewer may see contact details at all.
 *                         Decided by {@code DonorProfileService} from the live
 *                         session, never by the caller, so a screen cannot grant
 *                         itself access by passing a flag.
 * @param contactLockedReason  Sentence to show in place of the contact block when
 *                         {@code contactVisible} is false; {@code null} otherwise.
 */
public record DonorProfileView(long donorId, String fullName, BloodGroup bloodGroup, String district,
                               String address, AvailabilityStatus availabilityStatus, BadgeTier badgeTier,
                               int verifiedDonationCount, LocalDateTime memberSince,
                               Double averageRating, long reviewCount, Double distanceKm,
                               String phone, String email, boolean contactVisible, String contactLockedReason,
                               List<DonationRecord> donations, List<Review> reviews) {

    /** True when this donor has at least one review, mirroring {@code ReputationSummary.hasReviews()}. */
    public boolean hasReviews() { return reviewCount > 0 && averageRating != null; }

    /** Total units across every verified donation in {@link #donations}. */
    public int totalVerifiedUnits() {
        return donations.stream().filter(DonationRecord::verified).mapToInt(DonationRecord::units).sum();
    }

    /** How many distinct hospitals this donor has donated at, counting verified donations only. */
    public long hospitalsHelped() {
        return donations.stream().filter(DonationRecord::verified)
                .map(DonationRecord::hospitalName).filter(name -> name != null && !name.isBlank())
                .distinct().count();
    }
}
