package com.bloodlink.dao;

import com.bloodlink.model.DashboardStats;
import com.bloodlink.model.DemandRow;
import com.bloodlink.util.DatabaseSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminDAOTest {

    @BeforeAll
    static void setup() {
        DatabaseSetup.ensureInitialized();
    }

    @Test
    void testLoadStatsAndDemandRows() throws SQLException {
        AdminDAO adminDAO = new AdminDAO();
        DashboardStats stats = adminDAO.loadStats();
        assertNotNull(stats);
        assertTrue(stats.totalDonors() >= 0);

        List<DemandRow> demandRows = adminDAO.demandRows();
        assertNotNull(demandRows);
        assertFalse(demandRows.isEmpty());

        var users = adminDAO.findUsers("", 1, false);
        assertNotNull(users);
        assertTrue(users.items().stream().anyMatch(u -> u.id() == 1), "Should find admin");

        var monthly = adminDAO.monthlyRequests(6);
        assertNotNull(monthly);
        assertEquals(6, monthly.size());
    }
}
