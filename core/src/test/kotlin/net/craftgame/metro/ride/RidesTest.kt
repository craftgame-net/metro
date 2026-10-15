package net.craftgame.metro.ride

import net.craftgame.metro.RecordingDebugLog
import net.craftgame.metro.TestNetwork
import net.craftgame.metro.WORLD
import net.craftgame.metro.addPlatform
import net.craftgame.metro.data.Platform
import net.craftgame.metro.geo.Point
import net.craftgame.metro.rail.FakeRails
import net.craftgame.metro.rail.RailShape
import net.craftgame.metro.railAt
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RidesTest {
    private val network = TestNetwork()
    private val rails = FakeRails()
    private val host = RecordingHost()
    private val severe = ArrayList<String>()
    private val logger =
        Logger.getAnonymousLogger().apply {
            useParentHandlers = false
            addHandler(
                object : Handler() {
                    override fun publish(record: LogRecord) {
                        if (record.level == Level.SEVERE) severe += record.message
                    }

                    override fun flush() = Unit

                    override fun close() = Unit
                },
            )
        }
    private val debugLog = RecordingDebugLog(0)
    private val rides = Rides(network, rails, host, logger, { EJECT_DELAY }, debugLog)
    private val carts = HashMap<Ride, FakeCart>()

    /** A straight path east along z = 0 from x = 0 to x = [length]. */
    private fun straight(length: Int): List<Point> = (0..length).map { railAt(it) }

    /** Adds the destination platform `b:0`, whose `eject` is the end of [path] and whose `land` is beside it. */
    private fun destination(path: List<Point>) {
        network.addPlatform("b", Platform(eject = path.last(), land = LAND))
    }

    /** Starts a ride along [path] with an online rider aboard a cart at its first point. */
    private fun board(
        path: List<Point>,
        dwell: Int = 0,
        switchCandidates: List<Int> = emptyList(),
    ): Ride {
        val rider = UUID.randomUUID()
        host.online += rider
        val cart = FakeCart(path.first(), rider)
        val ride =
            Ride(rider, cart, "b", "0", StationSpot("a", "0"), path, List(path.size) { SPEED }, PHYSICS)
                .apply { dwellTicks = dwell }
        rides.start(ride, switchCandidates)
        carts[ride] = cart
        return ride
    }

    /** One server tick: the rides steer, then vanilla moves every cart. */
    private fun tick() {
        rides.tick()
        carts.values.forEach(FakeCart::move)
    }

    /** Ticks until [done] holds, at most [limit] ticks. Returns the ticks it took. */
    private fun tickUntil(
        limit: Int = 1000,
        done: () -> Boolean,
    ): Int {
        var ticks = 0
        while (!done()) {
            check(ticks < limit) { "not done after $limit ticks" }
            tick()
            ticks++
        }
        return ticks
    }

    @Test
    fun startSavesTheOriginAndCapsTheCartAtTheTopSpeed() {
        val ride = board(straight(10))

        assertEquals(listOf("remember a:0"), host.events)
        assertEquals(SPEED, carts.getValue(ride).lastMaxSpeed)
        assertSame(ride, rides.rideOf(ride.playerId))
        assertSame(ride, rides.rideOfCart(ride.cart.id))
    }

    @Test
    fun theCartWaitsForTheDwellAndThenLeaves() {
        val path = straight(10)
        destination(path)
        val ride = board(path, dwell = 3)
        val cart = carts.getValue(ride)

        repeat(3) {
            tick()
            assertEquals(RideState.DWELL, ride.state)
            assertEquals(path.first(), cart.position)
        }
        tick()
        assertEquals(RideState.MOVING, ride.state)
        tick()
        assertTrue(cart.lastVelocity.x > 0.0)
        assertTrue(cart.position.x > path.first().x)
        assertEquals(-90f, cart.yaw)
    }

    @Test
    fun anArrivedRideWaitsTheEjectDelayAndSetsTheRiderDownAtLand() {
        val path = straight(20)
        destination(path)
        val ride = board(path)
        val cart = carts.getValue(ride)

        tickUntil { ride.state == RideState.ARRIVED }
        assertEquals(EJECT_DELAY, ride.ejectTicks)
        repeat(EJECT_DELAY) {
            tick()
            assertSame(ride, rides.rideOf(ride.playerId))
        }
        tick()

        assertNull(rides.rideOf(ride.playerId))
        assertTrue(cart.removed)
        assertEquals(
            listOf(
                "remember a:0",
                "setDown ${LAND.x} ${LAND.y} ${LAND.z}",
                "remember b:0",
                "clearRescue",
                "message boarding.arrival b",
            ),
            host.events,
        )
    }

    @Test
    fun withoutEjectTheRideFinishesAtTheEndOfThePath() {
        val path = straight(10)
        val ride = board(path)

        tickUntil { rides.rideOf(ride.playerId) == null }

        val end = path.last()
        assertTrue("setDown ${end.x} ${end.y} ${end.z}" in host.events)
        // No station `b`: nothing to name in an arrival message.
        assertFalse(host.events.any { it.startsWith("message") })
    }

    @Test
    fun gettingOutWhileWaitingAtEjectIsAnArrival() {
        val path = straight(10)
        destination(path)
        val ride = board(path)
        tickUntil { ride.state == RideState.ARRIVED }

        carts.getValue(ride).passenger = null
        tick()

        assertNull(rides.rideOf(ride.playerId))
        assertTrue("message boarding.arrival b" in host.events)
        assertFalse("rescue" in host.events)
    }

    @Test
    fun aRiderWhoLeavesMidRideIsRescued() {
        val path = straight(20)
        destination(path)
        val ride = board(path)
        tickUntil { ride.distance > 3.0 }

        carts.getValue(ride).passenger = null
        tick()

        assertNull(rides.rideOf(ride.playerId))
        assertTrue(carts.getValue(ride).removed)
        val message = "message boarding.abort-left-cart ${stalledData(ride)}"
        assertEquals(listOf("remember a:0", "rescue", message), host.events)
    }

    @Test
    fun aRiderWhoGoesOfflineMidRideIsNotRescued() {
        val path = straight(20)
        destination(path)
        val ride = board(path)
        tickUntil { ride.distance > 3.0 }

        host.online -= ride.playerId
        tick()

        assertNull(rides.rideOf(ride.playerId))
        assertEquals(listOf("remember a:0"), host.events)
    }

    @Test
    fun progressIsLoggedAtLevelThreeAboutTheRider() {
        val path = straight(40)
        destination(path)
        val ride = board(path)
        tickUntil(limit = 100) { ride.distance > 2.0 }
        assertTrue(debugLog.lines.isEmpty())

        debugLog.level = 3
        tickUntil { rides.rideOf(ride.playerId) == null }

        val progress = debugLog.lines.filter { it.text.startsWith("ride d=") }
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it.level == 3 && it.subject == ride.playerId })
    }

    @Test
    fun aRideLogsItsEventsAtTheirLevelsAboutTheRider() {
        val path = straight(40)
        destination(path)
        debugLog.level = 3
        val ride = board(path, dwell = 1)

        tickUntil { rides.rideOf(ride.playerId) == null }

        assertTrue(debugLog.lines.all { it.subject == ride.playerId })

        fun level(prefix: String) = debugLog.lines.single { it.text.startsWith(prefix) }.level
        assertEquals(1, level("ride start a:0 -> b:0, 40.0 blocks"))
        assertEquals(1, level("ride finish b:0"))
        assertEquals(2, level("ride at eject"))
        assertEquals(3, level("ride leaves the platform"))
        assertEquals(3, level("ride releases"))
        assertEquals(3, debugLog.lines.count { it.text.startsWith("ride holds chunk") })
    }

    @Test
    fun aStalledRideLogsTheAbortAtLevelOne() {
        val path = straight(20)
        destination(path)
        debugLog.level = 1
        val ride = board(path)
        tickUntil { ride.distance > 2.0 }

        carts.getValue(ride).blocked = true
        tickUntil { rides.rideOf(ride.playerId) == null }

        val abort = debugLog.lines.single { it.text.startsWith("ride abort") }
        assertEquals(1, abort.level)
        assertTrue("state=MOVING" in abort.text && "rollback=true" in abort.text, abort.text)
        assertTrue(debugLog.lines.all { it.level == 1 })
    }

    @Test
    fun switchesAreLoggedOncePerRailAtLevelTwo() {
        val path = (0..5).map { railAt(it) } + (1..8).map { railAt(5, z = it) }
        rails.alongX(0, 5).alongZ(5, 1, 8)
        destination(path)
        debugLog.level = 2
        val ride = board(path, switchCandidates = listOf(5))

        tickUntil { rides.rideOf(ride.playerId) == null }

        val switches = debugLog.lines.filter { it.text.startsWith("switch") }
        assertEquals(2, switches.size)
        assertTrue(switches.all { it.level == 2 })
        assertTrue("set" in switches[0].text && "restored" in switches[1].text, switches.toString())
    }

    @Test
    fun nothingIsBuiltWhileTheLogIsOff() {
        val path = straight(40)
        destination(path)
        val ride = board(path)

        tickUntil { rides.rideOf(ride.playerId) == null }
        board(path, dwell = 100)
        rides.stopAll()

        assertEquals(0, debugLog.built)
        assertTrue(debugLog.lines.isEmpty())
    }

    @Test
    fun aBlockedCartStallsAndIsRolledBack() {
        val path = straight(20)
        destination(path)
        val ride = board(path)
        val cart = carts.getValue(ride)
        tickUntil { ride.distance > 2.0 }

        cart.blocked = true
        val ticks = tickUntil { rides.rideOf(ride.playerId) == null }

        // The count starts at the last progress: the move just before the block, seen one tick later, or a few
        // ticks earlier.
        val expected = StallTracker.STALL_TICKS - STALL_SLACK..StallTracker.STALL_TICKS + 1
        assertTrue(ticks in expected, "took $ticks ticks")
        assertTrue(cart.removed)
        val message = "message boarding.abort-stalled ${stalledData(ride)}"
        assertEquals(listOf("remember a:0", "rescue", message), host.events)
        assertTrue(host.held.isEmpty())
    }

    @Test
    fun aRideThatThrowsIsAbortedAndTheOthersGoOn() {
        val path = straight(20)
        destination(path)
        val broken = board(path)
        val healthy = board(path)
        tickUntil { healthy.distance > 2.0 }

        carts.getValue(broken).broken = true
        val before = healthy.distance
        tick()
        tick()

        assertNull(rides.rideOf(broken.playerId))
        assertTrue(carts.getValue(broken).removed)
        assertTrue("rescue" in host.events)
        assertEquals(1, severe.size)
        assertSame(healthy, rides.rideOf(healthy.playerId))
        assertTrue(healthy.distance > before)
    }

    @Test
    fun switchesAreSetWhileTheCartPassesAndRestoredBehindIt() {
        // East to x = 5, then south: the corner rail at x = 5 is laid straight and needs reshaping.
        val path = (0..5).map { railAt(it) } + (1..8).map { railAt(5, z = it) }
        rails.alongX(0, 5).alongZ(5, 1, 8)
        destination(path)
        val ride = board(path, switchCandidates = listOf(5))
        val shapes = LinkedHashSet<RailShape?>()

        tickUntil {
            shapes += rails.shape(5, 64, 0)
            rides.rideOf(ride.playerId) == null
        }

        assertEquals(listOf(RailShape.EAST_WEST, RailShape.SOUTH_WEST), shapes.toList())
        assertEquals(RailShape.EAST_WEST, rails.shape(5, 64, 0))
    }

    @Test
    fun aRideHoldsEveryChunkItPassesOnceUntilItEnds() {
        val path = straight(40)
        destination(path)
        val ride = board(path)

        tickUntil { ride.state == RideState.ARRIVED }
        assertEquals(
            listOf(Triple(WORLD, 0, 0), Triple(WORLD, 1, 0), Triple(WORLD, 2, 0)),
            host.everHeld,
        )
        assertTrue(host.held.values.all { it == 1 })

        tickUntil { rides.rideOf(ride.playerId) == null }
        assertTrue(host.held.isEmpty())
    }

    @Test
    fun stopAllFinishesArrivedRidesAndRollsBackTheOthers() {
        val path = straight(10)
        destination(path)
        val arrived = board(path)
        tickUntil { arrived.state == RideState.ARRIVED }
        board(path, dwell = 100)
        host.events.clear()

        rides.stopAll()

        assertTrue(rides.active().isEmpty())
        assertEquals(1, host.events.count { it == "message boarding.arrival b" })
        assertEquals(1, host.events.count { it == "rescue" })
        assertTrue(host.held.isEmpty())
    }

    @Test
    fun aLostCartAbortsAndTellsTheRider() {
        val path = straight(20)
        destination(path)
        val ride = board(path)
        tickUntil { ride.distance > 2.0 }
        val data = stalledData(ride)

        carts.getValue(ride).isValid = false
        tick()

        assertNull(rides.rideOf(ride.playerId))
        assertEquals(listOf("rescue", "message boarding.abort-cart-lost $data"), host.events.drop(1))
    }

    @Test
    fun anOfflineRiderIsNotRescuedOrTold() {
        val path = straight(20)
        destination(path)
        val ride = board(path)
        host.online -= ride.playerId

        tick()

        assertNull(rides.rideOf(ride.playerId))
        assertEquals(listOf("remember a:0"), host.events)
    }

    @Test
    fun aRideThatThrowsTellsTheRiderItFailed() {
        val path = straight(20)
        destination(path)
        val ride = board(path)
        tickUntil { ride.distance > 2.0 }

        carts.getValue(ride).broken = true
        tick()

        assertEquals(listOf("rescue", "message boarding.abort-failed ${stalledData(ride)}"), host.events.drop(1))
    }

    @Test
    fun stopAllTellsTheRidersOfUnfinishedRides() {
        val path = straight(10)
        destination(path)
        board(path, dwell = 100)

        rides.stopAll()

        assertEquals(listOf("rescue", "message boarding.abort-shutdown 0, 10, b:0"), host.events.drop(1))
    }

    /** The placeholder values of an abort message, as [RecordingHost] writes them. */
    private fun stalledData(ride: Ride) = "${ride.distance.roundToInt()}, ${ride.totalLength.roundToInt()}, b:0"

    private companion object {
        const val EJECT_DELAY = 5

        /** Ticks. */
        const val STALL_SLACK = 5

        /** Blocks per tick. */
        const val SPEED = 0.4

        val PHYSICS = RideTime.Physics(accel = 0.05, decel = 0.05, turnSpeed = 0.2)

        val LAND = Point(WORLD, 20.5, 65.0, 2.5)
    }
}
