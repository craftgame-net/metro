package net.craftgame.metro.rail

import net.craftgame.metro.geo.BlockPos

/**
 * The rail blocks of one loaded world. Call it only on the server thread that owns the blocks. Reading a block in a
 * chunk that is not loaded may load that chunk at once.
 */
interface RailWorld {
    /** The world name, as data.yml stores it. */
    val name: String

    /** Returns the shape of the rail at [pos], or null when the block is no rail. Every rail kind counts. */
    fun railShape(pos: BlockPos): RailShape?

    /**
     * Sets the rail at [pos] to [shape]. Does nothing when the block is no rail. Throws [IllegalArgumentException]
     * when the rail kind cannot take [shape], such as a curve on a powered rail.
     */
    fun setRailShape(
        pos: BlockPos,
        shape: RailShape,
    )
}

/** Finds the [RailWorld] of a world by its name. */
fun interface RailWorlds {
    /** Returns the world named [name], or null when it is not loaded. */
    fun world(name: String): RailWorld?
}
