package net.craftgame.metro.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JunctionTest {
    @Test
    fun kindOfIsCaseInsensitive() {
        assertEquals(Junction.Kind.SPLIT, Junction.kindOf("SPLIT"))
        assertEquals(Junction.Kind.JOIN, Junction.kindOf("join"))
        assertEquals(Junction.Kind.CROSS, Junction.kindOf("Cross"))
    }

    @Test
    fun kindOfRejectsUnknownNames() {
        assertNull(Junction.kindOf("ramp"))
        assertNull(Junction.kindOf(""))
    }
}
