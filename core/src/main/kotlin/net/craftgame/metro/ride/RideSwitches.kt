package net.craftgame.metro.ride

import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.debug.DebugLog
import net.craftgame.metro.debug.blocks
import net.craftgame.metro.geo.Point
import net.craftgame.metro.rail.RailShape
import net.craftgame.metro.rail.RailWorlds
import net.craftgame.metro.util.blockCoords
import java.util.UUID

/**
 * A rail whose shape the ride sets while it passes. Only geometry is known at departure. The block is read when the
 * cart comes close.
 */
internal class Switch(
    val location: Point,
    /** Shape the rail needs for the ride. */
    val shape: RailShape,
    /** Blocks along the path where the rail is. */
    val atDistance: Double,
) {
    /** True once the cart has come close enough that the rail was looked at. */
    var applied = false

    /** True once nothing is left to restore. */
    var restored = false

    /** Null when the ride left it alone. */
    var original: RailShape? = null
}

/**
 * The rails one ride reshapes while it passes, in route order. [tend] and [restoreAll] read and set blocks, so call
 * them on the server thread with the chunks loaded. Debug lines are about the rider passed as `subject`.
 */
class RideSwitches {
    private val switches = ArrayList<Switch>()

    /** Index in [switches] of the first one not yet restored. Everything before it is done. */
    private var cursor = 0

    /**
     * Plans the rail shapes for the indices in [candidates] of [path]. No block is read, so a departure loads no
     * chunks. [distanceAt] returns the blocks from the start of the path to an index.
     */
    fun plan(
        path: List<Point>,
        candidates: List<Int>,
        debugLog: DebugLog = DebugLog.OFF,
        subject: UUID? = null,
        distanceAt: (Int) -> Double,
    ) {
        for (i in candidates.sorted()) {
            val shape = if (i in 1 until path.size - 1) RailShapes.shapeAt(path[i - 1], path[i], path[i + 1]) else null
            if (shape != null) switches.add(Switch(path[i], shape, distanceAt(i)))
        }
        debugLog.log(DebugLevel.DETAIL, subject) {
            val list = switches.joinToString { "${blockCoords(it.location)} ${it.shape} at ${blocks(it.atDistance)}" }
            "switches planned: ${switches.size}${if (switches.isEmpty()) "" else " ($list)"}"
        }
    }

    /**
     * Sets the rails near a cart [distance] blocks along the path to the shape the ride needs, and restores each one
     * after the cart has passed. A rail is restored only if it still has this ride's shape.
     */
    fun tend(
        rails: RailWorlds,
        distance: Double,
        debugLog: DebugLog = DebugLog.OFF,
        subject: UUID? = null,
    ) {
        while (cursor < switches.size && switches[cursor].restored) cursor++
        // Switches are in route order, so the rest are further ahead still.
        var index = cursor
        while (index < switches.size && inReach(switches[index], distance)) {
            tendSwitch(rails, switches[index], distance, debugLog, subject)
            index++
        }
    }

    /** Restores every rail that was set and not restored yet. */
    fun restoreAll(
        rails: RailWorlds,
        debugLog: DebugLog = DebugLog.OFF,
        subject: UUID? = null,
    ) {
        for (switch in switches) {
            if (switch.applied && !switch.restored) restore(rails, switch, debugLog, subject)
        }
    }

    /** A rail is set [LEAD] blocks before the cart reaches it. Once set it stays in reach. */
    private fun inReach(
        switch: Switch,
        distance: Double,
    ): Boolean = switch.applied || distance >= switch.atDistance - LEAD

    /** Holds the rail in the ride's shape and restores it [TRAIL] blocks after the cart has passed. */
    private fun tendSwitch(
        rails: RailWorlds,
        switch: Switch,
        distance: Double,
        debugLog: DebugLog,
        subject: UUID?,
    ) {
        switch.applied = true
        when {
            switch.restored -> Unit
            distance >= switch.atDistance + TRAIL -> restore(rails, switch, debugLog, subject)
            else -> hold(rails, switch, debugLog, subject)
        }
    }

    private fun hold(
        rails: RailWorlds,
        switch: Switch,
        debugLog: DebugLog,
        subject: UUID?,
    ) {
        val world = rails.world(switch.location.world)
        val pos = switch.location.block()
        val current = world?.railShape(pos)
        if (world == null || current == null) {
            switch.restored = true
            debugLog.log(DebugLevel.DECISION, subject) { "switch ${blockCoords(switch.location)} skipped: no rail" }
            return
        }
        if (current == switch.shape) return
        if (switch.original == null) {
            switch.original = current
            debugLog.log(DebugLevel.DECISION, subject) {
                "switch ${blockCoords(switch.location)} set $current -> ${switch.shape}"
            }
        }
        world.setRailShape(pos, switch.shape)
    }

    private fun restore(
        rails: RailWorlds,
        switch: Switch,
        debugLog: DebugLog,
        subject: UUID?,
    ) {
        switch.restored = true
        val where = blockCoords(switch.location)
        val original = switch.original
        val world = rails.world(switch.location.world)
        if (original == null || world == null) {
            debugLog.log(DebugLevel.DECISION, subject) { "switch $where left alone: the ride never changed it" }
            return
        }
        val pos = switch.location.block()
        // Someone else changed the rail meanwhile: leave their shape.
        if (world.railShape(pos) == switch.shape) {
            world.setRailShape(pos, original)
            debugLog.log(DebugLevel.DECISION, subject) { "switch $where restored to $original" }
        } else {
            debugLog.log(DebugLevel.DECISION, subject) { "switch $where left alone: changed by someone else" }
        }
    }

    private companion object {
        /** Blocks before the cart that a rail is set. */
        const val LEAD = 2.5

        /** Blocks after the cart that a rail is restored. */
        const val TRAIL = 2.0
    }
}
