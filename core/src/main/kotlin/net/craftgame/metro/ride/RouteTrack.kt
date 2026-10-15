package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import kotlin.math.abs

/** A path with the distance along it worked out for every point, to look up points and segments by distance. */
class RouteTrack(
    val path: List<Point>,
) {
    private val cumulative = DoubleArray(path.size)

    /** Length of the whole path in blocks. */
    val totalLength: Double

    init {
        var accumulated = 0.0
        if (path.isNotEmpty()) {
            cumulative[0] = 0.0
            for (i in 1 until path.size) {
                accumulated += path[i].toVec3().distance(path[i - 1].toVec3())
                cumulative[i] = accumulated
            }
        }
        totalLength = accumulated
    }

    /** Returns the blocks from the start to point [index], clamped to the path. 0 for an empty path. */
    fun distanceAt(index: Int): Double {
        if (cumulative.isEmpty()) return 0.0
        return cumulative[index.coerceIn(0, cumulative.size - 1)]
    }

    /**
     * Returns the index of the segment (its first point) that contains [distance]. A distance exactly on a point
     * belongs to the segment ending there. Clamped to the path.
     */
    fun segmentAt(distance: Double): Int {
        if (path.size < 2) return 0
        return (firstReaching(distance.coerceIn(0.0, totalLength)) - 1).coerceAtMost(path.size - 1)
    }

    private fun firstReaching(distance: Double): Int {
        var low = 1
        var high = cumulative.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (cumulative[mid] < distance) low = mid + 1 else high = mid
        }
        return low
    }

    /** Returns the point [distance] blocks along the path, clamped to its ends. Throws for an empty path. */
    fun pointAt(distance: Double): Point {
        require(path.isNotEmpty()) { "empty path" }
        val clamped = distance.coerceIn(0.0, totalLength)
        val index = firstReaching(clamped)
        // A single point has no segment: the search lands past the end, on that point.
        if (index >= path.size) return path.last()

        val segmentStart = cumulative[index - 1]
        val segmentLength = cumulative[index] - segmentStart
        val t = if (segmentLength <= 1e-9) 0.0 else (clamped - segmentStart) / segmentLength
        val a = path[index - 1].toVec3()
        val b = path[index].toVec3()
        return Point.of(path[index - 1].world, a * (1 - t) + b * t)
    }

    /**
     * Returns the distance along the path of the point nearest to [location]. [hint] is the last known distance and
     * [speed] the cart's speed in blocks per tick. Only segments from a little behind [hint] to as far ahead as the
     * cart can travel in a few ticks are candidates, so a hairpin or a nearby track never pulls progress ahead.
     * Among equally near ones, the closest to [hint] wins.
     */
    fun projectedDistance(
        location: Vec3,
        hint: Double,
        speed: Double,
    ): Double {
        val ahead = (speed * AHEAD_TICKS).coerceIn(MIN_AHEAD, MAX_AHEAD)
        var bestDistance = hint
        var bestSquared = Double.MAX_VALUE
        // A binary search skips segments ending before the window; the loop stops past its far end.
        var i = if (path.size < 2) 0 else firstReaching(hint - BEHIND) - 1
        while (i < path.size - 1) {
            val segmentStart = cumulative[i]
            if (segmentStart > hint + ahead) break
            val a = path[i].toVec3()
            val b = path[i + 1].toVec3()
            val ab = b - a
            val lengthSquared = ab.lengthSquared()
            val abLength = Math.sqrt(lengthSquared)
            val t =
                if (lengthSquared < 1e-9) {
                    0.0
                } else {
                    ((location - a).dot(ab) / lengthSquared).coerceIn(0.0, 1.0)
                }
            val point = a + ab * t
            val squared = point.distanceSquared(location)
            val distance = (segmentStart + abLength * t).coerceAtMost(hint + ahead)
            val nearer = squared < bestSquared - TIE
            val tie = !nearer && squared <= bestSquared + TIE && abs(distance - hint) < abs(bestDistance - hint)
            if (nearer || tie) {
                bestSquared = minOf(squared, bestSquared)
                bestDistance = distance
            }
            i++
        }
        return bestDistance
    }

    private companion object {
        /** Blocks behind the hint that are still searched. */
        const val BEHIND = 6.0

        /** Ticks of travel at the current speed that are searched ahead. */
        const val AHEAD_TICKS = 3.0

        /** Blocks. A slow or starting cart still keeps up. */
        const val MIN_AHEAD = 3.0

        /** Blocks. */
        const val MAX_AHEAD = 12.0

        /** Squared distances in blocks, closer than this count as equally near. */
        const val TIE = 1e-4
    }
}
