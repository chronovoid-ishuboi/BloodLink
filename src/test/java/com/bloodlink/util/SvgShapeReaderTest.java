package com.bloodlink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The SVG reader is what makes "drop your own icon in and edit badges.json"
 * true, so its shape coverage is worth pinning down. Each case mirrors
 * something the badges README tells a user they can do.
 */
class SvgShapeReaderTest {

    @Test
    void readsPathElements() {
        String data = SvgShapeReaderTestAccess.read("/com/bloodlink/badges/silver.svg");
        assertNotNull(data);
        assertTrue(data.startsWith("M"), data);
        // The star knockout is a distinct subpath, so a loaded silver badge has more than one.
        assertTrue(data.chars().filter(c -> c == 'M').count() >= 2, "Expected multiple subpaths: " + data);
    }

    @Test
    void everyShippedBadgeLoads() {
        for (String file : new String[]{"none", "bronze", "silver", "gold", "platinum"}) {
            assertNotNull(SvgShapeReaderTestAccess.read("/com/bloodlink/badges/" + file + ".svg"),
                    file + ".svg did not load");
        }
    }

    @Test
    void missingResourceReturnsNull() {
        assertNull(SvgShapeReaderTestAccess.read("/com/bloodlink/badges/nope.svg"));
    }

    @Test
    void nonSvgFileIsTreatedAsRawPathData() {
        assertEquals("M0,0 L10,10 Z", SvgShapeReaderTestAccess.read("/svgreader/raw-path.txt"));
    }

    @Test
    void convertsPrimitiveShapesToPathData() {
        String data = SvgShapeReaderTestAccess.read("/svgreader/primitives.svg");
        assertNotNull(data);
        // rect, circle and polygon each contribute a subpath.
        assertEquals(3, data.chars().filter(c -> c == 'M').count(), data);
        assertTrue(data.contains("A"), "circle should convert to arcs: " + data);
        assertTrue(data.contains("Z"), data);
    }

    @Test
    void ignoresCommentedOutShapesAndUnsupportedElements() {
        String data = SvgShapeReaderTestAccess.read("/svgreader/noise.svg");
        assertNotNull(data);
        assertEquals(1, data.chars().filter(c -> c == 'M').count(),
                "Only the one real path should survive: " + data);
    }

    @Test
    void fileWithNoUsableShapeReturnsNull() {
        assertNull(SvgShapeReaderTestAccess.read("/svgreader/empty.svg"));
    }
}
