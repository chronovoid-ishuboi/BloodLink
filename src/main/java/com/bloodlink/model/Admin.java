package com.bloodlink.model;

import java.time.LocalDateTime;

public final class Admin extends User {
    public Admin(long id, String fullName, String email, String phone, String district,
                 String address, boolean approved, boolean active, LocalDateTime createdAt,
                 String nidNumber, String guardianName, String guardianPhone) {
        super(id, fullName, email, phone, district, address, Role.ADMIN, approved, active, createdAt, nidNumber, guardianName, guardianPhone);
    }
}
