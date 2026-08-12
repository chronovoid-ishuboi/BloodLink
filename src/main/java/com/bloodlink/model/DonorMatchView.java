package com.bloodlink.model;

import java.time.LocalDate;

public record DonorMatchView(long requestId, BloodGroup bloodGroup, String hospitalName, String district,
                             Urgency urgency, LocalDate deadline, RequestStatus requestStatus,
                             MatchStatus matchStatus, double score) { }
