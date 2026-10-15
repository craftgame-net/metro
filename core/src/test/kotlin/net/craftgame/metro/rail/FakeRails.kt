package net.craftgame.metro.rail

import net.craftgame.metro.WORLD
import net.craftgame.metro.geo.BlockPos

/**
 * One in-memory world named [name] whose rails are the entries of [shapes]. The builders lay track at height 64
 * unless told otherwise. Level rails connect whatever their shape, so the shape only matters for slopes and for
 * reading switch marks.
 */
class FakeRails(
    val name: String = WORLD,
    val shapes: MutableMap<BlockPos, RailShape> = HashMap(),
) : RailWorlds {
    val world: RailWorld =
        object : RailWorld {
            override val name: String get() = this@FakeRails.name

            override fun railShape(pos: BlockPos): RailShape? = shapes[pos]

            override fun setRailShape(
                pos: BlockPos,
                shape: RailShape,
            ) {
                if (pos in shapes) shapes[pos] = shape
            }
        }

    override fun world(name: String): RailWorld? = world.takeIf { name == this.name }

    /** Returns the shape at x, y, z, or null when there is no rail. */
    fun shape(
        x: Int,
        y: Int,
        z: Int,
    ): RailShape? = shapes[BlockPos(x, y, z)]

    /** Lays one rail. */
    fun rail(
        x: Int,
        y: Int,
        z: Int,
        shape: RailShape,
    ): FakeRails {
        shapes[BlockPos(x, y, z)] = shape
        return this
    }

    /** Lays east-west rails from x = [from] to [to], either way round. */
    fun alongX(
        from: Int,
        to: Int,
        z: Int = 0,
        y: Int = 64,
    ): FakeRails {
        for (x in minOf(from, to)..maxOf(from, to)) rail(x, y, z, RailShape.EAST_WEST)
        return this
    }

    /** Lays north-south rails from z = [from] to [to], either way round. */
    fun alongZ(
        x: Int,
        from: Int,
        to: Int,
        y: Int = 64,
    ): FakeRails {
        for (z in minOf(from, to)..maxOf(from, to)) rail(x, y, z, RailShape.NORTH_SOUTH)
        return this
    }
}
