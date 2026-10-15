package net.craftgame.metro.edit

import net.craftgame.metro.config.Speeds

/** A speed typed by an editor: [Auto] (unset) or a [Value]. */
sealed interface SpeedInput {
    data object Auto : SpeedInput

    /** A speed in blocks per tick. */
    data class Value(
        val speed: Double,
    ) : SpeedInput

    /** The typed speed, or null for [Auto]. */
    val override: Double? get() = (this as? Value)?.speed
}

/**
 * Reads `auto` or a speed in blocks per tick. Returns null for other text, `Infinity`, `NaN` and speeds below
 * [Speeds.MIN]. Callers apply the upper cap.
 */
fun parseSpeed(raw: String): SpeedInput? =
    if (raw.equals("auto", ignoreCase = true)) {
        SpeedInput.Auto
    } else {
        raw.toDoubleOrNull()?.takeIf(Speeds::valid)?.let { SpeedInput.Value(it) }
    }
