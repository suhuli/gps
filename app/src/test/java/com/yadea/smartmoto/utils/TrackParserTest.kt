package com.yadea.smartmoto.utils

import org.junit.Assert.*
import org.junit.Test

class TrackParserTest {
    @Test fun parsesAndPreservesOrder() {
        val points = TrackParser.parse("116.4,39.9;116.41,39.91;")
        assertEquals(2, points.size)
        assertEquals(116.4, points[0].longitude, 0.0)
        assertEquals(39.91, points[1].latitude, 0.0)
    }
    @Test fun supportsBomWhitespaceAndTrailingDelimiter() {
        assertEquals(2, TrackParser.parse("\uFEFF 116, 39 ;\n117,40;\n").size)
    }
    @Test fun dropsOnlyConsecutiveDuplicates() {
        assertEquals(3, TrackParser.parse("116,39;116,39;117,40;116,39").size)
    }
    @Test fun supportsWesternSouthernCoordinates() {
        val p = TrackParser.parse("-73,-35;-74,-36")
        assertEquals(-73.0, p[0].longitude, 0.0)
    }
    @Test fun rejectsBadTokensAtomically() {
        listOf("bad", "116,39;invalid;117,40", "181,39;117,40", "116,91;117,40", "NaN,39;117,40", "Infinity,39;117,40", "1,2,3;4,5", "", "116,39;116,39").forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { TrackParser.parse(input) }
        }
    }
    @Test fun rejectsOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) { TrackParser.parse("x".repeat(TrackParser.MAX_BYTES + 1)) }
    }
    @Test fun rejectsTooManyPoints() {
        val text = (0..TrackParser.MAX_POINTS).joinToString(";") { "${110.0 + it / 100000.0},39" }
        assertThrows(IllegalArgumentException::class.java) { TrackParser.parse(text) }
    }
    @Test fun acceptsBoundaryCoordinates() {
        assertEquals(2, TrackParser.parse("-180,-90;180,90").size)
    }
}
