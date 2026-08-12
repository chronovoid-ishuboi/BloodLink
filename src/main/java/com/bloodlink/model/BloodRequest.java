package com.bloodlink.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BloodRequest(
        long id,
        long requesterId,
        String requesterName,
        BloodGroup bloodGroup,
        int unitsNeeded,
        Urgency urgency,
        String hospitalName,
        String district,
        LocalDate deadline,
        String notes,
        RequestStatus status,
        Long acceptedDonorId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
