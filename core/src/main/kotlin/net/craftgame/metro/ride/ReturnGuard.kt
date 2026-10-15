package net.craftgame.metro.ride

import net.craftgame.metro.geo.Point
import java.util.UUID

/**
 * Ignores a boarding plate press from a player who has not left that plate since their last press. It remembers the
 * plate, not the player's position, because a player can move to another block while still on the same plate.
 */
class ReturnGuard {
    private val plates = HashMap<UUID, Point>()

    /** Remembers [plate] as the plate that player [id] last pressed. */
    fun set(
        id: UUID,
        plate: Point,
    ) {
        plates[id] = plate
    }

    /** True while [plate] is the block of the plate that player [id] last pressed and has not stepped off since. */
    fun holds(
        id: UUID,
        plate: Point,
    ): Boolean = plates[id]?.sameBlock(plate) == true

    /** Forgets every player for whom [stillOn] says they no longer stand on their remembered plate. */
    fun prune(stillOn: (UUID, Point) -> Boolean) {
        if (plates.isEmpty()) return
        plates.entries.removeIf { (id, plate) -> !stillOn(id, plate) }
    }
}
