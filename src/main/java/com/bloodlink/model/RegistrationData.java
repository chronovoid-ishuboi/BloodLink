package com.bloodlink.model;

import java.time.LocalDate;

public record RegistrationData(
        Role role,
        String fullName,
        String email,
        String phone,
        String district,
        String address,
        String password,
        String nidNumber,
        String guardianName,
        String guardianPhone,
        BloodGroup bloodGroup,
        LocalDate birthDate,
        Double weightKg,
        LocalDate lastDonationDate,
        Double heightCm,
        String chronicConditions,
        boolean recentSurgery,
        String recentSurgeryDetails,
        boolean recentTattoo,
        String recentTattooDetails,
        boolean currentMedications,
        String currentMedicationsDetails,
        boolean recentIllness,
        String recentIllnessDetails,
        boolean recentPregnancy,
        String recentPregnancyDetails,
        byte[] profilePhoto
) { }
