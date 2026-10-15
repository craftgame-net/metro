package net.craftgame.metro.ride

import net.craftgame.metro.at
import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteTrackTest {
    @Test
    fun totalLengthIsTheSumOfSegments() {
        val track = RouteTrack(listOf(at(0), at(3), at(3, z = 4)))
        assertEquals(7.0, track.totalLength, 1e-9)
    }

    @Test
    fun pointAtInterpolatesAcrossSegments() {
        val track = RouteTrack(listOf(at(0), at(2), at(2, z = 2)))
        val point = track.pointAt(3.0)
        assertEquals(2.0, point.x, 1e-9)
        assertEquals(1.0, point.z, 1e-9)
    }

    @Test
    fun distanceAtReturnsCumulativeLength() {
        val track = RouteTrack(listOf(at(0), at(3), at(3, z = 4)))
        assertEquals(0.0, track.distanceAt(0), 1e-9)
        assertEquals(3.0, track.distanceAt(1), 1e-9)
        assertEquals(7.0, track.distanceAt(2), 1e-9)
    }

    @Test
    fun projectedDistanceSnapsToTheNearestPoint() {
        val track = RouteTrack(listOf(at(0), at(10)))
        assertEquals(4.0, track.projectedDistance(Vec3(4.0, 0.0, 1.0), hint = 3.0, speed = 0.5), 1e-6)
    }

    private val hairpin = RouteTrack(listOf(at(0), at(10), at(10, z = 1), at(0, z = 1)))

    @Test
    fun aHairpinDoesNotPullProgressOntoTheReturnLeg() {
        // Drifted towards the return leg, which is nearer here, but 11 blocks further along the route.
        assertEquals(5.0, hairpin.projectedDistance(Vec3(5.0, 0.0, 0.6), hint = 5.0, speed = 0.5), 1e-6)
        assertEquals(5.0, hairpin.projectedDistance(Vec3(5.0, 0.0, 0.6), hint = 5.0, speed = 1.5), 1e-6)
    }

    @Test
    fun theReturnLegIsFollowedOnceTheCartGetsThere() {
        assertEquals(16.0, hairpin.projectedDistance(Vec3(5.0, 0.0, 0.9), hint = 15.5, speed = 0.5), 1e-6)
        assertEquals(10.5, hairpin.projectedDistance(Vec3(10.0, 0.0, 0.5), hint = 10.2, speed = 0.5), 1e-6)
    }

    @Test
    fun progressNeverRunsFurtherAheadThanTheCartCanTravel() {
        val track = RouteTrack(listOf(at(0), at(40)))
        assertEquals(3.0, track.projectedDistance(Vec3(20.0, 0.0, 0.0), hint = 0.0, speed = 0.1), 1e-6)
        assertEquals(12.0, track.projectedDistance(Vec3(20.0, 0.0, 0.0), hint = 0.0, speed = 10.0), 1e-6)
    }

    @Test
    fun nearlyEquallyNearCandidatesPreferTheSmallestJump() {
        // A loop that passes its own start: both passes are exactly as near.
        val loop = RouteTrack(listOf(at(0), at(2), at(2, z = 1), at(0, z = 1), at(0), at(-5)))
        assertEquals(0.0, loop.projectedDistance(Vec3(0.0, 0.0, 0.0), hint = 0.0, speed = 4.0), 1e-6)
    }

    private fun randomPath(random: Random): List<Point> {
        var x = 0.0
        var y = 64.0
        var z = 0.0
        return List(random.nextInt(2, 60)) {
            when (random.nextInt(5)) {
                0 -> Unit
                1 -> x += random.nextInt(1, 4)
                2 -> z -= random.nextInt(1, 4)
                3 -> y += random.nextInt(-1, 2)
                else -> x -= 1.0
            }
            Point("world", x, y, z)
        }
    }

    private fun linearCumulative(path: List<Point>): DoubleArray {
        val cumulative = DoubleArray(path.size)
        for (i in 1 until path.size) {
            cumulative[i] = cumulative[i - 1] + path[i].toVec3().distance(path[i - 1].toVec3())
        }
        return cumulative
    }

    private fun linearSegmentAt(
        cumulative: DoubleArray,
        distance: Double,
    ): Int {
        val clamped = distance.coerceIn(0.0, cumulative.last())
        var index = 0
        while (index < cumulative.size - 1 && cumulative[index + 1] < clamped) index++
        return index
    }

    private fun linearPointAt(
        path: List<Point>,
        cumulative: DoubleArray,
        distance: Double,
    ): Vec3 {
        val clamped = distance.coerceIn(0.0, cumulative.last())
        var index = 1
        while (index < cumulative.size && cumulative[index] < clamped) index++
        if (index >= path.size) return path.last().toVec3()
        val length = cumulative[index] - cumulative[index - 1]
        val t = if (length <= 1e-9) 0.0 else (clamped - cumulative[index - 1]) / length
        return path[index - 1].toVec3() * (1 - t) + path[index].toVec3() * t
    }

    private fun linearProjected(
        path: List<Point>,
        cumulative: DoubleArray,
        location: Vec3,
        hint: Double,
        speed: Double,
    ): Double {
        val ahead = (speed * 3.0).coerceIn(3.0, 12.0)
        var bestDistance = hint
        var bestSquared = Double.MAX_VALUE
        for (i in 0 until path.size - 1) {
            if (cumulative[i + 1] < hint - 6.0 || cumulative[i] > hint + ahead) continue
            val a = path[i].toVec3()
            val ab = path[i + 1].toVec3() - a
            val lengthSquared = ab.lengthSquared()
            val along = (location - a).dot(ab) / lengthSquared
            val t = if (lengthSquared < 1e-9) 0.0 else along.coerceIn(0.0, 1.0)
            val squared = (a + ab * t).distanceSquared(location)
            val distance = (cumulative[i] + Math.sqrt(lengthSquared) * t).coerceAtMost(hint + ahead)
            val nearer = squared < bestSquared - 1e-4
            val tie = !nearer && squared <= bestSquared + 1e-4 && abs(distance - hint) < abs(bestDistance - hint)
            if (nearer || tie) {
                bestSquared = minOf(squared, bestSquared)
                bestDistance = distance
            }
        }
        return bestDistance
    }

    @Test
    fun lookupsMatchTheLinearScansTheyReplaced() {
        val random = Random(20261004)
        repeat(200) {
            val path = randomPath(random)
            val track = RouteTrack(path)
            val cumulative = linearCumulative(path)
            val total = cumulative.last()
            val distances = cumulative.toList() + List(40) { random.nextDouble(-2.0, total + 2.0) }
            for (distance in distances) {
                assertEquals(linearSegmentAt(cumulative, distance), track.segmentAt(distance), "segment at $distance")
                assertEquals(linearPointAt(path, cumulative, distance), track.pointAt(distance).toVec3())
                val location = Vec3(random.nextDouble(-20.0, 40.0), 64.0, random.nextDouble(-40.0, 20.0))
                val speed = random.nextDouble(0.0, 5.0)
                assertEquals(
                    linearProjected(path, cumulative, location, distance, speed),
                    track.projectedDistance(location, distance, speed),
                    "projection with hint $distance",
                )
            }
        }
    }
}
