package com.bloodlink.model;

import java.time.LocalDateTime;

public final class Requester extends User {
    public Requester(long id, String fullName, String email, String phone, String district,
                     String address, boolean approved, boolean active, LocalDateTime createdAt,
                     String nidNumber, String guardianName, String guardianPhone) {
        super(id, fullName, email, phone, district, address, Role.REQUESTER, approved, active, createdAt, nidNumber, guardianName, guardianPhone);
    }
}
