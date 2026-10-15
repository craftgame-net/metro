package net.craftgame.metro.edit

import net.craftgame.metro.config.Speeds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeedInputTest {
    @Test
    fun parsesAutoAndNumbers() {
        assertEquals(SpeedInput.Auto, parseSpeed("AUTO"))
        assertEquals(SpeedInput.Value(0.6), parseSpeed("0.6"))
        assertEquals(SpeedInput.Value(Speeds.MIN), parseSpeed(Speeds.MIN.toString()))
    }

    @Test
    fun rejectsNonFiniteTooSlowAndText() {
        assertNull(parseSpeed("Infinity"))
        assertNull(parseSpeed("NaN"))
        assertNull(parseSpeed("1e-9"))
        assertNull(parseSpeed("0"))
        assertNull(parseSpeed("-1"))
        assertNull(parseSpeed("fast"))
    }

    @Test
    fun leavesUpperCapsToTheCaller() {
        assertEquals(SpeedInput.Value(1e308), parseSpeed("1e308"))
    }

    @Test
    fun overrideIsTheTypedSpeedOrNullForAuto() {
        assertNull(SpeedInput.Auto.override)
        assertEquals(0.6, SpeedInput.Value(0.6).override)
    }
}
