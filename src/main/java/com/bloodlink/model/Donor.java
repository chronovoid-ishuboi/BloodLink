package com.bloodlink.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Donor extends User {
    private BloodGroup bloodGroup;
    private LocalDate birthDate;
    private double weightKg;
    private LocalDate lastDonationDate;
    private AvailabilityStatus availabilityStatus;
    private int verifiedDonationCount;

    public Donor(long id, String fullName, String email, String phone, String district, String address,
                 boolean approved, boolean active, LocalDateTime createdAt, BloodGroup bloodGroup,
                 LocalDate birthDate, double weightKg, LocalDate lastDonationDate,
                 AvailabilityStatus availabilityStatus, int verifiedDonationCount) {
        super(id, fullName, email, phone, district, address, Role.DONOR, approved, active, createdAt);
        this.bloodGroup = bloodGroup;
        this.birthDate = birthDate;
        this.weightKg = weightKg;
        this.lastDonationDate = lastDonationDate;
        this.availabilityStatus = availabilityStatus;
        this.verifiedDonationCount = verifiedDonationCount;
    }

    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }
    public LocalDate getLastDonationDate() { return lastDonationDate; }
    public void setLastDonationDate(LocalDate lastDonationDate) { this.lastDonationDate = lastDonationDate; }
    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(AvailabilityStatus availabilityStatus) { this.availabilityStatus = availabilityStatus; }
    public int getVerifiedDonationCount() { return verifiedDonationCount; }
    public void setVerifiedDonationCount(int verifiedDonationCount) { this.verifiedDonationCount = verifiedDonationCount; }
    public BadgeTier getBadgeTier() { return BadgeTier.fromDonationCount(verifiedDonationCount); }
}
