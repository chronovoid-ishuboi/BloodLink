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
    private Long referenceHospitalId;
    private Double heightCm;
    private String chronicConditions;
    private boolean recentSurgery;
    private String recentSurgeryDetails;
    private boolean recentTattoo;
    private String recentTattooDetails;
    private boolean currentMedications;
    private String currentMedicationsDetails;
    private boolean recentIllness;
    private String recentIllnessDetails;
    private boolean recentPregnancy;
    private String recentPregnancyDetails;

    public Donor(long id, String fullName, String email, String phone, String district, String address,
                 boolean approved, boolean active, LocalDateTime createdAt, String nidNumber, String guardianName, String guardianPhone,
                 BloodGroup bloodGroup, LocalDate birthDate, double weightKg, LocalDate lastDonationDate,
                 AvailabilityStatus availabilityStatus, int verifiedDonationCount, Long referenceHospitalId,
                 Double heightCm, String chronicConditions, boolean recentSurgery, String recentSurgeryDetails,
                 boolean recentTattoo, String recentTattooDetails, boolean currentMedications, String currentMedicationsDetails,
                 boolean recentIllness, String recentIllnessDetails, boolean recentPregnancy, String recentPregnancyDetails) {
        super(id, fullName, email, phone, district, address, Role.DONOR, approved, active, createdAt, nidNumber, guardianName, guardianPhone);
        this.bloodGroup = bloodGroup;
        this.birthDate = birthDate;
        this.weightKg = weightKg;
        this.lastDonationDate = lastDonationDate;
        this.availabilityStatus = availabilityStatus;
        this.verifiedDonationCount = verifiedDonationCount;
        this.referenceHospitalId = referenceHospitalId;
        this.heightCm = heightCm;
        this.chronicConditions = chronicConditions;
        this.recentSurgery = recentSurgery;
        this.recentSurgeryDetails = recentSurgeryDetails;
        this.recentTattoo = recentTattoo;
        this.recentTattooDetails = recentTattooDetails;
        this.currentMedications = currentMedications;
        this.currentMedicationsDetails = currentMedicationsDetails;
        this.recentIllness = recentIllness;
        this.recentIllnessDetails = recentIllnessDetails;
        this.recentPregnancy = recentPregnancy;
        this.recentPregnancyDetails = recentPregnancyDetails;
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

    /** The hospital this donor has chosen as closest to where they actually are, or null if they haven't set one. */
    public Long getReferenceHospitalId() { return referenceHospitalId; }
    public void setReferenceHospitalId(Long referenceHospitalId) { this.referenceHospitalId = referenceHospitalId; }

    public Double getHeightCm() { return heightCm; }
    public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }
    public String getChronicConditions() { return chronicConditions; }
    public void setChronicConditions(String chronicConditions) { this.chronicConditions = chronicConditions; }
    public boolean isRecentSurgery() { return recentSurgery; }
    public void setRecentSurgery(boolean recentSurgery) { this.recentSurgery = recentSurgery; }
    public String getRecentSurgeryDetails() { return recentSurgeryDetails; }
    public void setRecentSurgeryDetails(String recentSurgeryDetails) { this.recentSurgeryDetails = recentSurgeryDetails; }
    public boolean isRecentTattoo() { return recentTattoo; }
    public void setRecentTattoo(boolean recentTattoo) { this.recentTattoo = recentTattoo; }
    public String getRecentTattooDetails() { return recentTattooDetails; }
    public void setRecentTattooDetails(String recentTattooDetails) { this.recentTattooDetails = recentTattooDetails; }
    public boolean isCurrentMedications() { return currentMedications; }
    public void setCurrentMedications(boolean currentMedications) { this.currentMedications = currentMedications; }
    public String getCurrentMedicationsDetails() { return currentMedicationsDetails; }
    public void setCurrentMedicationsDetails(String currentMedicationsDetails) { this.currentMedicationsDetails = currentMedicationsDetails; }
    public boolean isRecentIllness() { return recentIllness; }
    public void setRecentIllness(boolean recentIllness) { this.recentIllness = recentIllness; }
    public String getRecentIllnessDetails() { return recentIllnessDetails; }
    public void setRecentIllnessDetails(String recentIllnessDetails) { this.recentIllnessDetails = recentIllnessDetails; }
    public boolean isRecentPregnancy() { return recentPregnancy; }
    public void setRecentPregnancy(boolean recentPregnancy) { this.recentPregnancy = recentPregnancy; }
    public String getRecentPregnancyDetails() { return recentPregnancyDetails; }
    public void setRecentPregnancyDetails(String recentPregnancyDetails) { this.recentPregnancyDetails = recentPregnancyDetails; }
}
