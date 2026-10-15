package net.craftgame.metro.util

import net.craftgame.metro.data.Junction
import net.craftgame.metro.data.Platform
import net.craftgame.metro.geo.BlockPos
import net.craftgame.metro.point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaletteTest {
    @Test
    fun codesAreSixDigitUpperCaseHex() {
        assertEquals("&#9CFF2E", Palette.code(Palette.BOARD))
        assertEquals("&#00FF40", Palette.code(Palette.OUT))
    }

    @Test
    fun everyRoleHasItsOwnColourAndUnknownRolesNone() {
        val colours = Platform.ROLES.map { Palette.anchor(it) }
        assertEquals(Platform.ROLES.size, colours.filterNotNull().toSet().size)
        assertNull(Palette.anchor("door"))
        assertEquals("", Palette.anchorCode("door"))
        assertEquals(Palette.code(Palette.EJECT), Palette.anchorCode("eject"))
    }

    @Test
    fun aCrossingThatDoesNotTurnIsGrey() {
        assertEquals(Palette.SPLIT, Palette.junction(Junction.Kind.SPLIT, turns = false))
        assertEquals(Palette.JOIN, Palette.junction(Junction.Kind.JOIN, turns = true))
        assertEquals(Palette.CROSS, Palette.junction(Junction.Kind.CROSS, turns = true))
        assertEquals(Palette.CROSS_OFF, Palette.junction(Junction.Kind.CROSS, turns = false))
    }

    @Test
    fun blockCoordsRoundDownAndShowADashForNoPoint() {
        assertEquals("-1,64,2", blockCoords(point(-0.5, 64.9, 2.0)))
        assertEquals("3,4,5", blockCoords(BlockPos(3, 4, 5)))
        assertEquals("-", blockCoords(null))
    }
}
