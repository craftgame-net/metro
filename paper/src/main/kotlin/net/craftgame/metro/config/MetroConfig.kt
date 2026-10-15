package net.craftgame.metro.config

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.ride.RideTime
import net.craftgame.metro.ride.SpeedProfile
import net.craftgame.metro.util.Icons
import net.craftgame.metro.util.Palette
import net.craftgame.metro.util.Text
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/** Typed view of config.yml. Missing keys fall back to defaults; out-of-range values are clamped with a warning. */
class MetroConfig(
    private val plugin: MetroPlugin,
) {
    /** Grows on every [reload], so caches built from these values know when to start over. */
    var generation = 0L
        private set

    var speed = Speeds()
        private set

    var motion = Motion()
        private set
    val acceleration: Double get() = motion.acceleration
    val deceleration: Double get() = motion.deceleration
    val slowLead: Double get() = motion.slowLead
    val slowTrail: Double get() = motion.slowTrail

    /** Speed limit on slopes in blocks per tick (`motion.slope-speed`). */
    val slopeSpeed: Double get() = motion.slopeSpeed

    /** Ticks a rider waits in the cart before it leaves `begin` (`timing.departure`). */
    var departureTicks = 0
        private set

    /** Ticks the cart waits at `eject` before the rider is set down at `land` (`timing.eject-delay`). */
    var ejectDelay = DEFAULT_EJECT_DELAY
        private set

    val physics: RideTime.Physics
        get() =
            RideTime.Physics(
                accel = acceleration,
                decel = deceleration,
                turnSpeed = speed.turn,
                slowLead = slowLead,
                slowTrail = slowTrail,
                slope = SpeedProfile.Slope(slopeSpeed, motion.slopeLead, motion.slopeTrail),
            )

    var defaultIcon = Material.COMPASS
        private set

    var crowbarMaterial = Material.TRIDENT
        private set

    /** Most rail blocks one rebuild search visits before it gives up (`rebuild.max-search`). */
    var maxSearch = DEFAULT_MAX_SEARCH
        private set

    var previewEnabled = true
        private set
    var routeColor = Color.fromRGB(Palette.ROUTE)
        private set

    /** Ticks between two preview frames (`preview.interval`). */
    var previewInterval = 10
        private set

    /** Blocks around an editor within which preview markers are drawn (`preview.radius`). */
    var previewRadius = DEFAULT_PREVIEW_RADIUS
        private set

    /** Without a trailing slash. */
    var docsUrl = DEFAULT_DOCS_URL
        private set

    private var messages: Map<String, String> = emptyMap()

    /**
     * Re-reads config.yml and lang.yml and replaces every value, so call it on the main thread.
     * Copies lang.yml to the data folder when it is missing.
     */
    fun reload() {
        generation++
        // saveResource logs a warning when the file exists, so copy it out only the first time.
        if (!File(plugin.dataFolder, "lang.yml").exists()) plugin.saveResource("lang.yml", false)
        plugin.reloadConfig()
        val config = plugin.config

        val warn: (String) -> Unit = { plugin.logger.warning(it) }
        speed =
            Speeds(
                max = config.getDouble("speed.max", Speeds.DEFAULT_MAX),
                maxCruise = config.getDouble("speed.max-cruise", Speeds.DEFAULT_MAX_CRUISE),
                cruise = config.getDouble("speed.cruise", Speeds.DEFAULT_CRUISE),
                station = config.getDouble("speed.station", Speeds.DEFAULT_STATION),
                through = config.getDouble("speed.through", Speeds.DEFAULT_THROUGH),
                branch = config.getDouble("speed.branch", Speeds.DEFAULT_BRANCH),
                turn = config.getDouble("speed.turn", Speeds.DEFAULT_TURN),
            ).validated(warn)

        motion =
            Motion(
                acceleration = config.getDouble("motion.acceleration", Motion.DEFAULT_ACCELERATION),
                deceleration = config.getDouble("motion.deceleration", Motion.DEFAULT_DECELERATION),
                slowLead = config.getDouble("motion.slow-lead", Motion.DEFAULT_SLOW_LEAD),
                slowTrail = config.getDouble("motion.slow-trail", Motion.DEFAULT_SLOW_TRAIL),
                slopeSpeed = config.getDouble("motion.slope-speed", Motion.DEFAULT_SLOPE_SPEED),
                slopeLead = config.getDouble("motion.slope-lead", Motion.DEFAULT_SLOPE_LEAD),
                slopeTrail = config.getDouble("motion.slope-trail", Motion.DEFAULT_SLOPE_TRAIL),
            ).validated(speed.max, warn)

        departureTicks = ticks(config.getInt("timing.departure", 0), "departure")
        ejectDelay = ticks(config.getInt("timing.eject-delay", DEFAULT_EJECT_DELAY), "eject-delay")

        defaultIcon = material(config.getString("gui.destination-material"), Material.COMPASS)

        crowbarMaterial = material(config.getString("authoring.crowbar-material"), Material.TRIDENT)

        maxSearch = config.getInt("rebuild.max-search", DEFAULT_MAX_SEARCH).coerceAtLeast(MIN_MAX_SEARCH)

        previewEnabled = config.getBoolean("preview.enabled", true)
        routeColor = color(config.getString("preview.route-color"), Palette.ROUTE)
        previewInterval =
            config.getInt("preview.interval", DEFAULT_PREVIEW_INTERVAL).coerceAtLeast(MIN_PREVIEW_INTERVAL)
        previewRadius = config.getInt("preview.radius", DEFAULT_PREVIEW_RADIUS).coerceAtLeast(1)

        docsUrl = (config.getString("docs-url") ?: DEFAULT_DOCS_URL).trim().trimEnd('/').ifEmpty { DEFAULT_DOCS_URL }

        val flat = HashMap<String, String>()
        plugin.getResource("lang.yml")?.let { stream ->
            flatten(YamlConfiguration.loadConfiguration(stream.bufferedReader(Charsets.UTF_8)), "", flat)
        }
        flatten(YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "lang.yml")), "", flat)
        messages = flat
    }

    private fun flatten(
        section: ConfigurationSection,
        prefix: String,
        out: MutableMap<String, String>,
    ) {
        for (key in section.getKeys(false)) {
            when (val value = section.get(key)) {
                is ConfigurationSection -> flatten(value, "$prefix$key.", out)
                else -> out["$prefix$key"] = value?.toString().orEmpty()
            }
        }
    }

    /** Returns the lang.yml text for [key] with placeholders unfilled, or [key] itself when the text is missing. */
    fun raw(key: String): String = messages[key] ?: key

    /** Returns the lang.yml text for [key] with each `{name}` replaced by its value from [placeholders]. */
    fun message(
        key: String,
        vararg placeholders: Pair<String, String>,
    ): String = Text.fill(raw(key), *placeholders)

    /** Like [message], with the values in a map. */
    fun message(
        key: String,
        placeholders: Map<String, String>,
    ): String = Text.fill(raw(key), placeholders)

    /** Like [message], with the lang.yml `prefix` text in front. */
    fun prefixed(
        key: String,
        vararg placeholders: Pair<String, String>,
    ): String = raw("prefix") + message(key, *placeholders)

    private fun ticks(
        value: Int,
        key: String,
    ): Int {
        if (value < 0) plugin.logger.warning("timing.$key: $value is negative, using 0")
        return value.coerceAtLeast(0)
    }

    private fun material(
        name: String?,
        fallback: Material,
    ): Material = name?.let { Material.matchMaterial(it) }?.takeIf(Icons::usable) ?: fallback

    private fun color(
        hex: String?,
        fallback: Int,
    ): Color {
        val value = hex?.removePrefix("#") ?: return Color.fromRGB(fallback)
        return runCatching { Color.fromRGB(value.toInt(HEX_RADIX)) }.getOrElse { Color.fromRGB(fallback) }
    }

    private companion object {
        /** Blocks. */
        const val DEFAULT_PREVIEW_RADIUS = 48

        /** Rail blocks. */
        const val DEFAULT_MAX_SEARCH = 5000

        /** Ticks. */
        const val DEFAULT_PREVIEW_INTERVAL = 10

        /** Ticks. */
        const val DEFAULT_EJECT_DELAY = 20
        const val DEFAULT_DOCS_URL = "https://github.com/craftgame-net/metro/wiki"

        /**
         * Each frame walks every segment and marker for every editor, so drawing every tick costs a lot for
         * little gain. In ticks.
         */
        const val MIN_PREVIEW_INTERVAL = 2

        const val HEX_RADIX = 16

        /** Rail blocks. A tiny value would cut every search short. */
        const val MIN_MAX_SEARCH = 16
    }
}
