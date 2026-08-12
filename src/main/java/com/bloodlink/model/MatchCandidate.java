package com.bloodlink.model;

public record MatchCandidate(long donorId, String donorName, BloodGroup bloodGroup, String district,
                             String phone, double score, String reason, AvailabilityStatus availabilityStatus,
                             BadgeTier badgeTier) { }
