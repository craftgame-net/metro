package net.craftgame.metro.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdsTest {
    @Test
    fun acceptsLowercaseDigitsUnderscoreAndDash() {
        assertEquals("red_line-2", Ids.validated("red_line-2"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals("red", Ids.validated("  red "))
    }

    @Test
    fun refusesUppercase() {
        assertNull(Ids.validated("Red"))
    }

    @Test
    fun refusesInnerSpace() {
        assertNull(Ids.validated("red line"))
    }

    @Test
    fun refusesDot() {
        assertNull(Ids.validated("red.line"))
    }

    @Test
    fun refusesColon() {
        assertNull(Ids.validated("red:line"))
    }

    @Test
    fun refusesEmpty() {
        assertNull(Ids.validated(""))
        assertNull(Ids.validated("   "))
    }

    @Test
    fun refusesNonAscii() {
        assertNull(Ids.validated("линия"))
        assertNull(Ids.validated("café"))
    }
}
