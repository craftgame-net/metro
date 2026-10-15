package net.craftgame.metro.route

import net.craftgame.metro.config.Speeds
import net.craftgame.metro.data.Edge
import net.craftgame.metro.geo.Point

/**
 * The router's cost of an edge: its travel time in ticks. Each dense segment takes `length / v`, where `v` is the
 * edge speed (`speed.station` when it has none) capped by `speed.max`, by `speed.turn` right after a sharp turn and
 * by the slope speed on a slope.
 *
 * The polyline is not densified here: dense points inside a stored segment are collinear and evenly spaced, so only
 * a stored point can be a sharp turn, and a stored segment is a slope along its whole length or not at all.
 * Acceleration, braking and the slow-lead and slope-lead windows are ignored: the full ride model is too costly per
 * Dijkstra step.
 */
object EdgeCost {
    /**
     * Returns the estimated travel time of [edge] in ticks, or 0 for an edge with fewer than two points.
     * [slopeSpeed] is the speed limit on slopes in blocks per tick; [Slopes.OFF] leaves slopes alone.
     */
    fun ticks(
        edge: Edge,
        speeds: Speeds,
        slopeSpeed: Double = Slopes.OFF,
    ): Double {
        val path = edge.points
        if (path.size < 2) return 0.0
        val caps = Caps(speeds.turn, slopeSpeed)
        val base = minOf(edge.cruiseSpeed ?: speeds.station, speeds.max).coerceAtLeast(MIN_SPEED)
        val zoned = edge.startSwitchSpeed != null || edge.endSwitchSpeed != null
        val count = if (zoned) denseCount(path) else 0
        var total = 0.0
        var first = 0
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dz = b.z - a.z
            val length = Math.sqrt(dx * dx + dy * dy + dz * dz)
            val steps = Polyline.steps(dx, dy, dz)
            val sharp = caps.turns && sharpAt(path, i)
            val slope = caps.slopes && dy != 0.0 && slopeSegment(a, b, steps)
            val segment = Segment(first, steps, length / steps, sharp, slope)
            val speed = if (slope) Slopes.cap(base, slopeSpeed) else base
            total +=
                if (sharp) {
                    val step = length / steps
                    step / minOf(speed, caps.turn) + (length - step) / speed
                } else {
                    length / speed
                }
            if (zoned) total += zoneExtra(edge, segment, count, base, caps)
            first += steps
        }
        return total
    }

    /** The turn and slope caps of one query. */
    private class Caps(
        /** Blocks per tick; 0 or less turns the cap off. */
        val turn: Double,
        val slopeSpeed: Double,
    ) {
        val turns = turn > 0.0
        val slopes = slopeSpeed < Slopes.OFF

        /** Returns the speed on dense segment [index] of [segment] for a segment speed of [speed]. */
        fun speed(
            speed: Double,
            segment: Segment,
            index: Int,
        ): Double {
            var value = speed
            if (segment.slope) value = Slopes.cap(value, slopeSpeed)
            if (segment.sharp && index == segment.first) value = minOf(value, turn)
            return value
        }
    }

    /** One stored segment, which spans [steps] dense segments of [length] blocks each. */
    private class Segment(
        /** Index of its first dense segment on the whole edge. */
        val first: Int,
        val steps: Int,
        val length: Double,
        /** True when a sharp turn happens at the start of the segment. */
        val sharp: Boolean,
        /** True when its dense segments are slopes. */
        val slope: Boolean,
    )

    /**
     * Returns true when the dense segments of the stored segment from [a] to [b] are slopes. They all have the same
     * extents, so the first one, as [Polyline.densify] makes it, stands for all of them.
     */
    private fun slopeSegment(
        a: Point,
        b: Point,
        steps: Int,
    ): Boolean =
        Slopes.isSlope(
            Polyline.denseCoordinate(a.x, b.x, 1, steps) - a.x,
            Polyline.denseCoordinate(a.y, b.y, 1, steps) - a.y,
            Polyline.denseCoordinate(a.z, b.z, 1, steps) - a.z,
        )

    /** Each step inside a switch zone runs at the zone speed instead of [base], both under the [caps]. */
    private fun zoneExtra(
        edge: Edge,
        segment: Segment,
        count: Int,
        base: Double,
        caps: Caps,
    ): Double {
        val end = segment.first + segment.steps
        val head = segment.first until minOf(end, SwitchZone.SEGMENTS)
        val tail = maxOf(segment.first, count - SwitchZone.SEGMENTS, SwitchZone.SEGMENTS) until end
        var extra = 0.0
        for (index in head + tail) {
            val zone = SwitchZone.speed(base, edge.startSwitchSpeed, edge.endSwitchSpeed, index, count)
            if (zone >= base) continue
            val slow = caps.speed(zone.coerceAtLeast(MIN_SPEED), segment, index)
            extra += segment.length / slow - segment.length / caps.speed(base, segment, index)
        }
        return extra
    }

    /** Returns the number of dense segments on [path], as [Polyline.densify] would make. */
    private fun denseCount(path: List<Point>): Int {
        var count = 0
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            count += Polyline.steps(b.x - a.x, b.y - a.y, b.z - a.z)
        }
        return count
    }

    /** [Turns.isSharp] at a stored point, as the densified path would see it. */
    private fun sharpAt(
        path: List<Point>,
        i: Int,
    ): Boolean {
        if (i <= 0 || i >= path.size - 1) return false
        val prev = path[i - 1]
        val at = path[i]
        val next = path[i + 1]
        val inSteps = Polyline.steps(at.x - prev.x, at.y - prev.y, at.z - prev.z)
        val outSteps = Polyline.steps(next.x - at.x, next.y - at.y, next.z - at.z)
        val beforeX = Polyline.denseCoordinate(prev.x, at.x, inSteps - 1, inSteps)
        val beforeZ = Polyline.denseCoordinate(prev.z, at.z, inSteps - 1, inSteps)
        val afterX = Polyline.denseCoordinate(at.x, next.x, 1, outSteps)
        val afterZ = Polyline.denseCoordinate(at.z, next.z, 1, outSteps)
        return Turns.isSharp(at.x - beforeX, at.z - beforeZ, afterX - at.x, afterZ - at.z)
    }

    /** Blocks per tick. Keeps a bad stored speed from dividing by zero. */
    private const val MIN_SPEED = Speeds.MIN
}
