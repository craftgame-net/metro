package net.craftgame.metro.config

/** The `motion:` section of config.yml. It uses no server state, so tests run without a server. */
data class Motion(
    /** Speed gained each tick while speeding up, in blocks per tick. */
    val acceleration: Double = DEFAULT_ACCELERATION,
    /** Speed lost each tick while braking, in blocks per tick. */
    val deceleration: Double = DEFAULT_DECELERATION,
    /** Blocks before a slow stretch where the cart already starts to brake. */
    val slowLead: Double = DEFAULT_SLOW_LEAD,
    /** Blocks after a slow stretch where the cart is still held at the slow speed. */
    val slowTrail: Double = DEFAULT_SLOW_TRAIL,
    /** Speed limit on slopes, in blocks per tick. */
    val slopeSpeed: Double = DEFAULT_SLOPE_SPEED,
    /** Blocks before a slope where the cart already runs at the slope speed. */
    val slopeLead: Double = DEFAULT_SLOPE_LEAD,
    /** Blocks after a slope where the cart still runs at the slope speed. */
    val slopeTrail: Double = DEFAULT_SLOPE_TRAIL,
) {
    /**
     * Clamps every value into range, calling [warn] once per change. Acceleration and deceleration must be positive:
     * zero would stop a cart from leaving `begin` or from braking. Above [maxSpeed] they are clamped to it.
     */
    fun validated(
        maxSpeed: Double,
        warn: (String) -> Unit,
    ): Motion =
        Motion(
            acceleration = rate("acceleration", acceleration, DEFAULT_ACCELERATION, maxSpeed, warn),
            deceleration = rate("deceleration", deceleration, DEFAULT_DECELERATION, maxSpeed, warn),
            slowLead = zone("slow-lead", slowLead, DEFAULT_SLOW_LEAD, warn),
            slowTrail = zone("slow-trail", slowTrail, DEFAULT_SLOW_TRAIL, warn),
            slopeSpeed = slopeSpeed(slopeSpeed, maxSpeed, warn),
            slopeLead = zone("slope-lead", slopeLead, DEFAULT_SLOPE_LEAD, warn),
            slopeTrail = zone("slope-trail", slopeTrail, DEFAULT_SLOPE_TRAIL, warn),
        )

    private fun rate(
        key: String,
        value: Double,
        default: Double,
        maxSpeed: Double,
        warn: (String) -> Unit,
    ): Double =
        when {
            !value.isFinite() || value <= 0.0 ->
                default.coerceAtMost(maxSpeed).also { warn("motion.$key: $value is not a positive number, using $it") }
            value > maxSpeed -> maxSpeed.also { warn("motion.$key: $value is above speed.max, using $it") }
            else -> value
        }

    private fun slopeSpeed(
        value: Double,
        maxSpeed: Double,
        warn: (String) -> Unit,
    ): Double =
        when {
            !value.isFinite() || value < Speeds.MIN ->
                DEFAULT_SLOPE_SPEED.coerceAtMost(maxSpeed).also {
                    warn("motion.slope-speed: $value is not a number of at least ${Speeds.MIN}, using $it")
                }
            value > maxSpeed -> maxSpeed.also { warn("motion.slope-speed: $value is above speed.max, using $it") }
            else -> value
        }

    private fun zone(
        key: String,
        value: Double,
        default: Double,
        warn: (String) -> Unit,
    ): Double =
        when {
            !value.isFinite() -> default.also { warn("motion.$key: $value is not a number, using $it") }
            value < 0.0 || value > MAX_SLOW_ZONE ->
                value.coerceIn(0.0, MAX_SLOW_ZONE).also {
                    warn("motion.$key: $value is outside 0..$MAX_SLOW_ZONE blocks, using $it")
                }
            else -> value
        }

    companion object {
        const val DEFAULT_ACCELERATION = 0.02
        const val DEFAULT_DECELERATION = 0.03
        const val DEFAULT_SLOW_LEAD = 6.0
        const val DEFAULT_SLOW_TRAIL = 2.0
        const val DEFAULT_SLOPE_SPEED = 0.6
        const val DEFAULT_SLOPE_LEAD = 2.0
        const val DEFAULT_SLOPE_TRAIL = 2.0

        /** Largest allowed slow-lead, slow-trail, slope-lead or slope-trail, in blocks. */
        const val MAX_SLOW_ZONE = 16.0
    }
}
