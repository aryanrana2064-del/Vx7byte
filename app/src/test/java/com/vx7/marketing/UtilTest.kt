package com.vx7.marketing

import org.junit.Assert.*
import org.junit.Test

class UtilTest {
    @Test fun qtyFormatKeepsWhatWasEntered() {
        assertEquals("2", fmtQty(2.0)); assertEquals("1.5", fmtQty(1.5)); assertEquals("0.25", fmtQty(0.25))
    }

    @Test fun qtyParsingRejectsInvalid() {
        assertNull(parseQty("")); assertNull(parseQty("abc")); assertNull(parseQty("0")); assertNull(parseQty("-3")); assertNull(parseQty("NaN"))
        assertEquals(1.5, parseQty("1,5")!!, 0.0); assertEquals(10.0, parseQty(" 10 ")!!, 0.0)
    }

    @Test fun similarNamesDetection() {
        assertTrue(similarNames("Parle-G", "parle g")); assertTrue(similarNames("Tata Salt", "Tata Salt 1kg"))
        assertTrue(similarNames("Maggi", "Maggie")); assertFalse(similarNames("Rice", "Salt"))
    }

    @Test fun unitsListMatchesSpec() {
        assertEquals(13, UNITS.size); assertEquals(CUSTOM, UNITS.last())
        listOf("Piece", "Box", "Packet", "Carton", "Bag", "Kg", "Gram", "Litre", "Dozen", "Bundle", "Set", "Roll").forEach { assertTrue(it in UNITS) }
        assertEquals("Reel", resolveUnit(CUSTOM, " Reel ")); assertEquals("Box", resolveUnit("Box", "ignored"))
    }

    // ---- Real Rate / Last Rate
    @Test fun newProductKeepsRealRate() {
        val r = nextRates(null, null, 50.0)
        assertEquals(50.0, r.real!!, 0.0); assertNull(r.last)
    }

    @Test fun changingRealRateMovesOldValueToLastRate() {
        val r = nextRates(50.0, 40.0, 55.0)
        assertEquals(55.0, r.real!!, 0.0); assertEquals(50.0, r.last!!, 0.0)
    }

    @Test fun unchangedRealRateDoesNotTouchLastRate() {
        val r = nextRates(50.0, 40.0, 50.0)
        assertEquals(50.0, r.real!!, 0.0); assertEquals(40.0, r.last!!, 0.0)
    }

    @Test fun clearingRealRateKeepsOldValueAsLast() {
        val r = nextRates(50.0, 40.0, null)
        assertNull(r.real); assertEquals(50.0, r.last!!, 0.0)
    }

    @Test fun manualLastRateWins() {
        val r = resolveRates(50.0, 40.0, 55.0, 45.0, true)
        assertEquals(55.0, r.real!!, 0.0); assertEquals(45.0, r.last!!, 0.0)
        val auto = resolveRates(50.0, 40.0, 55.0, 40.0, false)
        assertEquals(50.0, auto.last!!, 0.0)
    }

    @Test fun rateParsing() {
        assertEquals(true to null, parseRate("")); assertEquals(true to 50.0, parseRate(" 50 ")); assertEquals(true to 12.5, parseRate("12,5"))
        assertFalse(parseRate("abc").first); assertFalse(parseRate("-1").first); assertFalse(parseRate("NaN").first)
        assertEquals("\u20B950", fmtRate(50.0)); assertEquals("\u20B950.5", fmtRate(50.5)); assertEquals("\u2014", fmtRate(null))
    }
}
