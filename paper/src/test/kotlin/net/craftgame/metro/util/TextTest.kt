package net.craftgame.metro.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    @Test
    fun parsesHexColour() {
        val component = Text.of(Palette.code(Palette.BOARD) + "Board")
        assertEquals(Palette.BOARD, component.color()?.value())
    }

    @Test
    fun fillReplacesEachPlaceholderOnce() {
        val text = Text.fill("{a} and {b}", "a" to "{b}", "b" to "x")
        assertEquals("{b} and x", text)
    }

    @Test
    fun fillLeavesUnknownPlaceholdersAndRepeatsKnownOnes() {
        assertEquals("1 {other} 1 {}", Text.fill("{n} {other} {n} {}", "n" to "1"))
        assertEquals("max-cruise: 1.0", Text.fill("max-cruise: {max-cruise}", "max-cruise" to "1.0"))
    }

    @Test
    fun fillKeepsDollarSignsAndBackslashesInValues() {
        val value = "a\\b \$1"
        assertEquals("cost $value", Text.fill("cost {v}", "v" to value))
    }
}
