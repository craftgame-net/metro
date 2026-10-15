package net.craftgame.metro.route

import net.craftgame.metro.geo.Point
import net.craftgame.metro.geo.Vec3
import net.craftgame.metro.rail.RailPathfinder
import net.craftgame.metro.rail.RailWorlds

/**
 * Travel directions read from a platform's pocket when it has no explicit heading. The pocket is the short stretch
 * of rail inside a platform: `begin` to `out` and `in` to `eject`.
 */
internal object PocketHeadings {
    /**
     * Returns the direction in which the track continues past [out], taken from the only rail next to it that the
     * path from `begin` did not come from. Null when there is no such single rail.
     */
    fun fromBegin(
        rails: RailWorlds,
        out: Point,
        path: List<Point>?,
    ): Vec3? {
        val world = rails.world(out.world)
        val outBlock = if (world != null) RailPathfinder.nearestRail(world, out) else null
        val prev = if (path != null && path.size >= 2) path[path.size - 2].block() else null
        val next =
            if (world != null && outBlock != null && prev != null) {
                TrackChain.soleOther(RailPathfinder.connections(world, outBlock), prev)
            } else {
                null
            }
        return if (outBlock != null && next != null) {
            TrackChain.direction(outBlock.toVec3(), next.toVec3())
        } else {
            null
        }
    }

    /**
     * Returns the direction of travel at [inbound], from `in` towards the second point of [path]. Null when `in` does
     * not have exactly one other rail neighbour, which happens when it sits on a switch.
     */
    fun fromEject(
        rails: RailWorlds,
        inbound: Point,
        path: List<Point>?,
    ): Vec3? {
        val world = rails.world(inbound.world)
        val inBlock = if (world != null) RailPathfinder.nearestRail(world, inbound) else null
        val first = if (path != null && path.size >= 2) path[1].block() else null
        val guard =
            if (world != null && inBlock != null && first != null) {
                TrackChain.soleOther(RailPathfinder.connections(world, inBlock), first)
            } else {
                null
            }
        return if (inBlock != null && first != null && guard != null) {
            TrackChain.direction(inBlock.toVec3(), first.toVec3())
        } else {
            null
        }
    }
}
