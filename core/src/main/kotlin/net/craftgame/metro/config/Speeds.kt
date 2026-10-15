package net.craftgame.metro.config

/** The `speed:` section of config.yml, in blocks per tick. It uses no server state, so tests run without a server. */
data class Speeds(
    /** Cap for every speed. Never above [VANILLA_LIMIT]. */
    val max: Double = DEFAULT_MAX,
    /** Cap for line speeds. */
    val maxCruise: Double = DEFAULT_MAX_CRUISE,
    /** Used when a line has no speed of its own. */
    val cruise: Double = DEFAULT_CRUISE,
    /** Edges inside a station: a platform on the main line and the pocket edges (`begin` to `out`, `in` to `eject`). */
    val station: Double = DEFAULT_STATION,
    /** Edges through a station that the ride does not stop at. */
    val through: Double = DEFAULT_THROUGH,
    /** Ramps and branches whose junction has no speed. */
    val branch: Double = DEFAULT_BRANCH,
    /** Cap for the speed in sharp turns, see [net.craftgame.metro.route.Turns]. */
    val turn: Double = DEFAULT_TURN,
) {
    /**
     * Clamps every value into range, calling [warn] once per change. A value below [MIN] or not a number gets
     * its default.
     */
    fun validated(warn: (String) -> Unit): Speeds {
        fun speed(
            key: String,
            value: Double,
            default: Double,
            cap: Double,
            capKey: String,
        ): Double =
            when {
                !valid(value) -> default.coerceAtMost(cap).also { warn("speed.$key: $value is below $MIN, using $it") }
                value > cap -> cap.also { warn("speed.$key: $value is above speed.$capKey, using $it") }
                else -> value
            }

        val max =
            when {
                !valid(max) -> DEFAULT_MAX.also { warn("speed.max: $max is below $MIN, using $it") }
                max > VANILLA_LIMIT -> VANILLA_LIMIT.also { warn("speed.max: $max is above $it, the vanilla limit") }
                else -> max
            }
        val maxCruise = speed("max-cruise", maxCruise, DEFAULT_MAX_CRUISE, max, "max")
        val cruise = speed("cruise", cruise, DEFAULT_CRUISE, maxCruise, "max-cruise")
        return Speeds(
            max = max,
            maxCruise = maxCruise,
            cruise = cruise,
            station = speed("station", station, DEFAULT_STATION, max, "max"),
            through = speed("through", through, DEFAULT_THROUGH, max, "max"),
            branch = speed("branch", branch, DEFAULT_BRANCH, max, "max"),
            turn = speed("turn", turn, DEFAULT_TURN, max, "max"),
        )
    }

    companion object {
        /** The slowest any speed may be (one block per second), so no stretch of a route gets a limit of zero. */
        const val MIN = 0.05

        /** True for a finite speed of at least [MIN]. */
        fun valid(speed: Double): Boolean = speed.isFinite() && speed >= MIN

        const val DEFAULT_MAX = 1.2
        const val DEFAULT_MAX_CRUISE = 1.0
        const val DEFAULT_CRUISE = 0.6
        const val DEFAULT_STATION = 0.3
        const val DEFAULT_THROUGH = 0.55
        const val DEFAULT_BRANCH = 0.3
        const val DEFAULT_TURN = 0.35

        /**
         * Vanilla caps horizontal cart movement at 2.0, then scales it by the 0.75 passenger factor
         * (see [net.craftgame.metro.ride.Steering]): 2.0 * 0.75 = 1.5 blocks per tick.
         */
        const val VANILLA_LIMIT = 1.5
    }
}
