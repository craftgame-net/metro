package net.craftgame.metro.route

import net.craftgame.metro.geo.Point
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Edge polylines are stored compressed: only direction changes, ends and points that must stay addressable
 * (switch marks). Rides and estimates need one point per block, so [densify] expands them in memory.
 */
object Polyline {
    /** Block paths are exact; this only absorbs float noise. */
    private const val EPSILON = 1e-9

    /**
     * Compressed points. [indexMap] maps each source index to its index in [points], or -1 if the point was
     * dropped.
     */
    class Compressed(
        val points: List<Point>,
        val indexMap: IntArray,
    )

    /** Dense points. [indices] maps each source index to its index in [points]. */
    class Dense(
        val points: List<Point>,
        val indices: IntArray,
    )

    /** Only collinear points go, so length is preserved. Keeps the ends and every index in [keep]. */
    fun compress(
        points: List<Point>,
        keep: Collection<Int> = emptySet(),
    ): Compressed {
        val indexMap = IntArray(points.size) { -1 }
        if (points.size <= 2) {
            for (i in points.indices) indexMap[i] = i
            return Compressed(points.toList(), indexMap)
        }
        val kept = keep.toHashSet()
        val result = ArrayList<Point>()
        result.add(points[0])
        indexMap[0] = 0
        var anchor = points[0]
        for (i in 1 until points.size - 1) {
            if (i in kept || !straight(anchor, points[i], points[i + 1])) {
                result.add(points[i])
                indexMap[i] = result.size - 1
                anchor = points[i]
            }
        }
        result.add(points.last())
        indexMap[points.size - 1] = result.size - 1
        return Compressed(result, indexMap)
    }

    /** Compressed points with [marks], the indices into [points] of the switch marks. */
    class Marked(
        val points: List<Point>,
        val marks: List<Int>?,
    )

    /**
     * Compresses [path], keeping the points in [marks]. Returns the marks as indices into the new points. A null
     * [marks] stays null.
     */
    fun compressMarked(
        path: List<Point>,
        marks: List<Int>?,
    ): Marked {
        val compressed = compress(path, marks.orEmpty())
        return Marked(compressed.points, marks?.map { compressed.indexMap[it] })
    }

    /** Evenly spaced points, one step per block of the longest axis span. Stored points are kept. */
    fun densify(points: List<Point>): Dense {
        val indices = IntArray(points.size)
        if (points.isEmpty()) return Dense(emptyList(), indices)
        val result = ArrayList<Point>(points.size)
        result.add(points[0])
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val steps = steps(b.x - a.x, b.y - a.y, b.z - a.z)
            for (k in 1 until steps) {
                result.add(
                    Point(
                        a.world,
                        denseCoordinate(a.x, b.x, k, steps),
                        denseCoordinate(a.y, b.y, k, steps),
                        denseCoordinate(a.z, b.z, k, steps),
                    ),
                )
            }
            result.add(b)
            indices[i] = result.size - 1
        }
        return Dense(result, indices)
    }

    /**
     * Returns one coordinate of dense point [k] on a stored segment of [steps] steps from [from] to [to], as [densify]
     * places it. [k] 0 gives [from] and [steps] gives [to] exactly.
     */
    fun denseCoordinate(
        from: Double,
        to: Double,
        k: Int,
        steps: Int,
    ): Double =
        when {
            k <= 0 -> from
            k >= steps -> to
            else -> from + (to - from) * k / steps
        }

    /** Returns how many one-block steps a segment with these extents has: at least 1. */
    fun steps(
        dx: Double,
        dy: Double,
        dz: Double,
    ): Int = max(1, max(abs(dx), max(abs(dy), abs(dz))).roundToInt())

    /** A zero-length step never counts as straight. */
    private fun straight(
        from: Point,
        middle: Point,
        to: Point,
    ): Boolean {
        val ax = middle.x - from.x
        val ay = middle.y - from.y
        val az = middle.z - from.z
        val bx = to.x - middle.x
        val by = to.y - middle.y
        val bz = to.z - middle.z
        val lengths = sqrt(ax * ax + ay * ay + az * az) * sqrt(bx * bx + by * by + bz * bz)
        if (lengths == 0.0) return false
        val cx = ay * bz - az * by
        val cy = az * bx - ax * bz
        val cz = ax * by - ay * bx
        val cross = sqrt(cx * cx + cy * cy + cz * cz)
        val dot = ax * bx + ay * by + az * bz
        return dot > 0 && cross <= EPSILON * lengths
    }
}
