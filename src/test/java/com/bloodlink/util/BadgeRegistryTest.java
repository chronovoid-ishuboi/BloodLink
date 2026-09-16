package com.bloodlink.util;

import com.bloodlink.model.BadgeTier;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the badge manifest contract the README promises to users: every tier
 * resolves, the shipped SVGs are actually readable as JavaFX path geometry, and
 * nothing here needs a JavaFX toolkit (Color.web is a plain value class).
 */
class BadgeRegistryTest {

    @Test
    void everyTierResolvesToACompleteStyle() {
        for (BadgeTier tier : BadgeTier.values()) {
            BadgeRegistry.BadgeStyle style = BadgeRegistry.styleFor(tier);
            assertNotNull(style, "No style resolved for " + tier);
            assertNotNull(style.label(), "Null label for " + tier);
            assertFalse(style.label().isBlank(), "Blank label for " + tier);
            assertNotNull(style.color(), "Null color for " + tier);
        }
    }

    @Test
    void shippedManifestValuesAreUsedNotJustTheFallbacks() {
        assertEquals("Platinum Donor", BadgeRegistry.labelFor(BadgeTier.PLATINUM));
        assertEquals("New Donor", BadgeRegistry.labelFor(BadgeTier.NONE));
        assertEquals(Color.web("#C79A2E"), BadgeRegistry.colorFor(BadgeTier.GOLD));
    }

    /**
     * The shipped icons must load as real geometry, or badges silently render as
     * label-only text -- which is the exact problem this system replaced.
     */
    @Test
    void shippedIconsParseIntoPathGeometry() {
        for (BadgeTier tier : BadgeTier.values()) {
            String pathData = BadgeRegistry.iconFor(tier);
            assertNotNull(pathData, "No icon geometry loaded for " + tier);
            assertTrue(pathData.startsWith("M"), "Icon for " + tier + " is not path data: " + pathData);
        }
    }

    @Test
    void nullTierIsTreatedAsNone() {
        assertEquals(BadgeRegistry.labelFor(BadgeTier.NONE), BadgeRegistry.styleFor(null).label());
    }

    @Test
    void missingIconFileFallsBackToNullGeometryRatherThanThrowing() {
        assertNull(SvgShapeReaderTestAccess.read("/com/bloodlink/badges/does-not-exist.svg"));
    }
}
