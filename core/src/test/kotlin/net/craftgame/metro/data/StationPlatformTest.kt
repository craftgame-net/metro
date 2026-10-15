package net.craftgame.metro.data

import net.craftgame.metro.at
import net.craftgame.metro.geo.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StationPlatformTest {
    private fun station(): Station = Station(id = "s", display = "S", world = "world")

    @Test
    fun fallbackNumberIsNumericIdPlusOneElseTheId() {
        assertEquals("1", Platform(id = "0").fallbackNumber())
        assertEquals("8", Platform(id = "7").fallbackNumber())
        assertEquals("north", Platform(id = "north").fallbackNumber())
    }

    @Test
    fun platformNameFallsBackWhenBlank() {
        assertEquals("Platform #1", Platform().name("Platform #1"))
        assertEquals("Platform #1", Platform(display = "  ").name("Platform #1"))
        assertEquals("A", Platform(display = "A").name("Platform #1"))
    }

    @Test
    fun visitSpotPrefersLandThenBoardThenTrack() {
        val land = Point("world", 1.0, 2.0, 3.0)
        val board = Point("world", 4.0, 5.0, 6.0)
        val begin = Point("world", 7.0, 8.0, 9.0)
        assertEquals(land, Platform(land = land, board = board, begin = begin).visitSpot())
        assertEquals(board, Platform(board = board, begin = begin).visitSpot())
        assertEquals(begin, Platform(begin = begin).visitSpot())
        assertNull(Platform().visitSpot())
    }

    @Test
    fun stationVisitSpotSkipsBarePlatforms() {
        val station = station()
        station.addPlatform()
        val begin = Point("world", 7.0, 8.0, 9.0)
        val second = station.addPlatform(Platform(begin = begin))
        assertEquals(second to begin, station.firstVisitSpot())
    }

    @Test
    fun clearingAnAnchorUnsetsIt() {
        val platform = Platform(eject = Point("world", 1.0, 2.0, 3.0))
        platform.setAnchor("eject", null)
        assertNull(platform.anchor("eject"))
    }

    @Test
    fun addPlatformAssignsSequentialIds() {
        val station = station()
        assertEquals("0", station.addPlatform().id)
        assertEquals("1", station.addPlatform().id)
    }

    @Test
    fun nextIdReusesAFreedNumber() {
        val station = station()
        station.addPlatform()
        station.addPlatform()
        station.addPlatform()
        station.removePlatform("1")
        assertEquals("1", station.nextPlatformId())
    }

    @Test
    fun ensurePlatformIdsFillsOnlyBlanks() {
        val station = station()
        station.platforms.add(Platform())
        station.platforms.add(Platform(id = "5"))
        station.platforms.add(Platform())
        station.ensurePlatformIds()
        assertEquals(listOf("0", "5", "2"), station.platforms.map { it.id })
    }

    @Test
    fun ensurePlatformIdsRenamesRepeatedIds() {
        val station = station()
        station.platforms.add(Platform(id = "0"))
        station.platforms.add(Platform(id = "0"))
        station.platforms.add(Platform())
        station.platforms.add(Platform(id = "3"))
        val renamed = station.ensurePlatformIds()
        assertEquals(listOf("0", "1", "2", "3"), station.platforms.map { it.id })
        assertEquals(listOf("0" to "1"), renamed)
    }

    @Test
    fun lookupByIdIgnoresMissing() {
        val station = station()
        station.addPlatform()
        assertEquals("0", station.platform("0")?.id)
        assertNull(station.platform("nope"))
    }

    @Test
    fun boardingNeedsTriggerSpawnAndWayOut() {
        val at = Point("world", 0.0, 0.0, 0.0)
        assertTrue(Platform(board = at, begin = at, outbound = at).boards)
        assertFalse(Platform(board = at, begin = at).boards)
        assertFalse(Platform(begin = at, outbound = at).boards)
        assertFalse(Platform(board = at, outbound = at).boards)
    }

    @Test
    fun alightingNeedsWayInAndHalt() {
        val at = Point("world", 0.0, 0.0, 0.0)
        assertTrue(Platform(inbound = at, eject = at).alights)
        assertFalse(Platform(eject = at, land = at).alights)
    }

    @Test
    fun everyRoleReadsAndWritesItsOwnAnchor() {
        val platform = Platform()
        Platform.ROLES.forEachIndexed { index, role -> platform.setAnchor(role, at(index)) }

        assertEquals(Platform.ROLES.indices.map { at(it) }, Platform.ROLES.map { platform.anchor(it) })
        assertEquals(at(2), platform.outbound)
        assertEquals(at(3), platform.inbound)
        platform.setAnchor("door", at(9))
        assertNull(platform.anchor("door"))
        assertEquals(Platform.ROLES.toSet() - setOf("board", "land"), Platform.RAIL_ROLES)
    }

    @Test
    fun ridersAreSetDownAtLandElseAtEject() {
        assertEquals(at(1), Platform(eject = at(0), land = at(1)).teleportTarget())
        assertEquals(at(0), Platform(eject = at(0)).teleportTarget())
        assertNull(Platform().teleportTarget())
    }
}
