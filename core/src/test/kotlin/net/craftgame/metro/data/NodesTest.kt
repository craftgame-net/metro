package net.craftgame.metro.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodesTest {
    @Test
    fun buildsThreePartId() {
        assertEquals("central:0:out", Nodes.of("central", "0", "out"))
    }

    @Test
    fun parsesThreePartId() {
        val ref = Nodes.parse("central:0:out")
        assertEquals("central", ref?.stationId)
        assertEquals("0", ref?.platform)
        assertEquals("out", ref?.role)
    }

    @Test
    fun twoPartIdDefaultsToPlatformZero() {
        assertEquals("0", Nodes.parse("central:out")?.platform)
    }

    @Test
    fun rejectsMalformedIds() {
        assertNull(Nodes.parse("central"))
        assertNull(Nodes.parse("a:b:c:d"))
    }

    @Test
    fun stopKeysRoundTrip() {
        assertEquals("central:1", Nodes.stop("central", "1"))
        assertEquals("central" to "1", Nodes.parseStop("central:1"))
        assertNull(Nodes.parseStop("central"))
    }

    @Test
    fun buildsAndParsesJunctionId() {
        assertEquals("junction.depot.x", Nodes.junction("depot", "x"))
        val ref = Nodes.parseJunction("junction.depot.z")
        assertEquals("depot", ref?.id)
        assertEquals("z", ref?.axis)
    }

    @Test
    fun plainParseSkipsJunctionIds() {
        assertNull(Nodes.parse("junction.depot.x"))
    }

    @Test
    fun parseJunctionRejectsForeignAndMalformedIds() {
        assertNull(Nodes.parseJunction("central:0:out"))
        assertNull(Nodes.parseJunction("junction.depot"))
        assertNull(Nodes.parseJunction("junction."))
        assertNull(Nodes.parseJunction("junction..x"))
    }
}
