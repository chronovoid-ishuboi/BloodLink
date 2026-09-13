package com.bloodlink.service;

import com.bloodlink.model.Role;
import com.bloodlink.model.User;
import com.bloodlink.util.DatabaseSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    @BeforeAll
    static void setup() {
        DatabaseSetup.ensureInitialized();
    }

    @Test
    void testLogins() {
        AuthService authService = new AuthService();

        // Test admin
        ServiceResult<User> adminRes = authService.login("admin@bloodlink.local", "Admin@123", Role.ADMIN);
        assertTrue(adminRes.success(), "Admin login should succeed: " + adminRes.message());

        // Test requester
        ServiceResult<User> reqRes = authService.login("requester@bloodlink.local", "Request@123", Role.REQUESTER);
        assertTrue(reqRes.success(), "Requester login should succeed: " + reqRes.message());

        // Test donor opos
        ServiceResult<User> donorRes = authService.login("donor.opos@bloodlink.local", "Donor@123", Role.DONOR);
        assertTrue(donorRes.success(), "Donor login should succeed: " + donorRes.message());

        // Test issmam donor with Donor@123 or Password123!
        ServiceResult<User> issmamRes = authService.login("issmam@bloodlink.local", "Donor@123", Role.DONOR);
        assertTrue(issmamRes.success(), "Issmam login should succeed with Donor@123: " + issmamRes.message());

        ServiceResult<User> issmamRes2 = authService.login("issmam@bloodlink.local", "Password123!", Role.DONOR);
        assertTrue(issmamRes2.success(), "Issmam login should succeed with Password123!: " + issmamRes2.message());
    }

    @Test
    void testGeminiApiKeyLoaded() {
        String key = com.bloodlink.util.AppConfig.get("gemini.api.key");
        assertNotNull(key);
        assertFalse(key.isBlank());
        assertTrue(key.startsWith("AQ."));
    }
}
