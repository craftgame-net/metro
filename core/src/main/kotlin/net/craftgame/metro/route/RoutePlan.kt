package net.craftgame.metro.route

import net.craftgame.metro.geo.Point

/**
 * The path of a ride. [points] are points along the rails. [segmentSpeeds] holds the speed limit in blocks per
 * tick of the segment starting at each point; the last entry is a 0.0 stop marker. [switchCandidates] are indices
 * into [points] whose rail may have to be reshaped before the cart arrives.
 */
data class RoutePlan(
    val points: List<Point>,
    val segmentSpeeds: List<Double>,
    val switchCandidates: List<Int>,
)
