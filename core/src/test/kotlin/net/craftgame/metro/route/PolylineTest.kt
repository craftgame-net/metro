package net.craftgame.metro.route

import net.craftgame.metro.geo.Point
import net.craftgame.metro.point
import net.craftgame.metro.railAt
import kotlin.test.Test
import kotlin.test.assertEquals

class PolylineTest {
    private fun walk(steps: List<Triple<Int, Int, Int>>): List<Point> {
        var x = 0
        var y = 64
        var z = 0
        val path = arrayListOf(railAt(x, y, z))
        for ((dx, dy, dz) in steps) {
            x += dx
            y += dy
            z += dz
            path += railAt(x, y, z)
        }
        return path
    }

    private fun repeat(
        count: Int,
        step: Triple<Int, Int, Int>,
    ): List<Triple<Int, Int, Int>> = List(count) { step }

    private val east = Triple(1, 0, 0)
    private val south = Triple(0, 0, 1)
    private val up = Triple(1, 1, 0)
    private val down = Triple(1, -1, 0)

    private fun length(points: List<Point>): Double {
        val vectors = points.map { it.toVec3() }
        return (1 until vectors.size).sumOf { vectors[it - 1].distance(vectors[it]) }
    }

    private val shapes: Map<String, List<Point>> =
        mapOf(
            "straight" to walk(repeat(40, east)),
            "slope" to walk(repeat(5, east) + repeat(4, up) + repeat(3, east) + repeat(4, down) + repeat(5, east)),
            "staircase" to walk(List(20) { if (it % 2 == 0) east else south }),
            "diagonal" to walk(repeat(12, Triple(1, 0, 1))),
            "curve" to walk(repeat(10, east) + listOf(east, south, east, south) + repeat(10, south)),
            "l-turn" to walk(repeat(10, east) + repeat(10, south)),
        )

    @Test
    fun straightRunKeepsOnlyItsEnds() {
        val path = shapes.getValue("straight")
        val compressed = Polyline.compress(path)
        assertEquals(listOf(path.first(), path.last()), compressed.points)
    }

    @Test
    fun slopeKeepsWhereItStartsAndEnds() {
        val path = shapes.getValue("slope")
        val compressed = Polyline.compress(path)
        assertEquals(listOf(0, 5, 9, 12, 16, 21).map { path[it] }, compressed.points)
    }

    @Test
    fun lTurnKeepsTheCorner() {
        val path = shapes.getValue("l-turn")
        assertEquals(listOf(path[0], path[10], path[20]), Polyline.compress(path).points)
    }

    @Test
    fun staircaseKeepsEveryTurnAndATrueDiagonalOnlyItsEnds() {
        val stairs = shapes.getValue("staircase")
        assertEquals(stairs, Polyline.compress(stairs).points)
        val diagonal = shapes.getValue("diagonal")
        assertEquals(2, Polyline.compress(diagonal).points.size)
    }

    @Test
    fun keptIndicesSurviveAndTheIndexMapPointsAtThem() {
        val path = shapes.getValue("straight")
        val compressed = Polyline.compress(path, keep = setOf(7, 30))
        assertEquals(listOf(path[0], path[7], path[30], path[40]), compressed.points)
        assertEquals(0, compressed.indexMap[0])
        assertEquals(1, compressed.indexMap[7])
        assertEquals(2, compressed.indexMap[30])
        assertEquals(3, compressed.indexMap[40])
        assertEquals(-1, compressed.indexMap[8])
    }

    @Test
    fun compressionPreservesLength() {
        for ((name, path) in shapes) {
            assertEquals(length(path), length(Polyline.compress(path, keep = setOf(3)).points), 1e-9, name)
        }
    }

    @Test
    fun densifyRestoresTheBlockPathExactly() {
        for ((name, path) in shapes) {
            val compressed = Polyline.compress(path, keep = setOf(2, 9))
            val dense = Polyline.densify(compressed.points)
            assertEquals(path, dense.points, name)
            for ((original, stored) in compressed.indexMap.withIndex()) {
                if (stored >= 0) assertEquals(original, dense.indices[stored], name)
            }
        }
    }

    @Test
    fun densifyLeavesADensePathAsItIs() {
        val path = shapes.getValue("curve")
        val dense = Polyline.densify(path)
        assertEquals(path, dense.points)
        assertEquals(path.indices.toList(), dense.indices.toList())
    }

    @Test
    fun irregularSegmentsDensifyToAboutOneBlock() {
        val a = point(0.0, 64.0, 0.0)
        val b = point(3.4, 64.0, 1.0)
        val dense = Polyline.densify(listOf(a, b))
        assertEquals(4, dense.points.size)
        assertEquals(b, dense.points.last())
        assertEquals(listOf(0, 3), dense.indices.toList())
    }

    @Test
    fun shortAndEmptyPolylinesPassThrough() {
        assertEquals(emptyList(), Polyline.compress(emptyList()).points)
        assertEquals(emptyList(), Polyline.densify(emptyList()).points)
        val two = listOf(railAt(0), railAt(5))
        assertEquals(two, Polyline.compress(two).points)
        assertEquals(6, Polyline.densify(two).points.size)
    }

    @Test
    fun compressMarkedMovesTheMarksOntoTheKeptPoints() {
        val path = shapes.getValue("l-turn")

        val marked = Polyline.compressMarked(path, listOf(0, 4, 10, 20))

        assertEquals(listOf(path[0], path[4], path[10], path[20]), marked.points)
        assertEquals(listOf(0, 1, 2, 3), marked.marks)
        assertEquals(listOf(path[0], path[10], path[20]), Polyline.compressMarked(path, emptyList()).points)
    }

    @Test
    fun unknownMarksStayUnknown() {
        val path = shapes.getValue("straight")

        val marked = Polyline.compressMarked(path, null)

        assertEquals(null, marked.marks)
        assertEquals(listOf(path.first(), path.last()), marked.points)
    }

    @Test
    fun aRepeatedPointIsKeptSoTheLengthStaysTheSame() {
        val path = listOf(railAt(0), railAt(1), railAt(1), railAt(2))

        assertEquals(path, Polyline.compress(path).points)
        assertEquals(path, Polyline.densify(path).points)
    }

    @Test
    fun aReversalIsNotStraight() {
        val path = listOf(railAt(0), railAt(3), railAt(1))

        assertEquals(path, Polyline.compress(path).points)
    }
}
