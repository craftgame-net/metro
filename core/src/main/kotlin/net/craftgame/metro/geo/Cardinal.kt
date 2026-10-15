package net.craftgame.metro.geo

/** A compass direction on the x/z plane. North is -z and east is +x, as in Minecraft. */
enum class Cardinal(
    /** Unit vector of the direction. */
    val direction: Vec3,
) {
    NORTH(Vec3(0.0, 0.0, -1.0)),
    EAST(Vec3(1.0, 0.0, 0.0)),
    SOUTH(Vec3(0.0, 0.0, 1.0)),
    WEST(Vec3(-1.0, 0.0, 0.0)),
    ;

    companion object {
        /** Returns the direction named [name], ignoring case, or null for any other text. */
        fun of(name: String): Cardinal? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
