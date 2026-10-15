package net.craftgame.metro.preview

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.geo.Point
import net.craftgame.metro.platform.Scheduler
import net.craftgame.metro.util.Palette
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Player
import kotlin.math.sqrt

/** Draws the stored edges, anchors and junctions as particles for players in editor mode. */
class NetworkPreview(
    private val plugin: MetroPlugin,
) {
    private var task: Scheduler.Task? = null

    /** Starts the repeating draw task, replacing a running one. Its period is `preview.interval` ticks. */
    fun start() {
        task?.cancel()
        val interval = plugin.metroConfig.previewInterval.toLong()
        task = plugin.scheduler.repeat(interval, interval) { render() }
    }

    /** Starts the task again, so a changed `preview.interval` takes effect. */
    fun restart() {
        start()
    }

    private fun render() {
        if (!plugin.metroConfig.previewEnabled) return
        val viewers = Bukkit.getOnlinePlayers().filter { plugin.editManager.isEditing(it) }.map { Viewer(it) }
        if (viewers.isEmpty()) return
        val radius = plugin.metroConfig.previewRadius.toDouble()

        for (edge in plugin.dataStore.edges.values) {
            drawLine(viewers, edge.points, plugin.metroConfig.routeColor, radius)
        }
        for (station in plugin.dataStore.stations.values) {
            for (platform in station.platforms) {
                drawMarker(viewers, platform.board, color(Palette.BOARD), radius)
                drawMarker(viewers, platform.begin, color(Palette.BEGIN), radius)
                drawMarker(viewers, platform.outbound, color(Palette.OUT), radius)
                drawMarker(viewers, platform.inbound, color(Palette.IN), radius)
                drawMarker(viewers, platform.eject, color(Palette.EJECT), radius)
                drawMarker(viewers, platform.land, color(Palette.LAND), radius)
            }
        }
        for (junction in plugin.dataStore.junctions.values) {
            drawMarker(viewers, junction.location, color(Palette.junction(junction.kind, junction.turns)), radius)
        }
    }

    private fun color(rgb: Int): Color = Color.fromRGB(rgb)

    private fun drawLine(
        viewers: List<Viewer>,
        points: List<Point>,
        color: Color,
        radius: Double,
    ) {
        if (points.size < 2) return
        val world = points[0].world
        val inWorld = viewers.filter { it.world == world }
        if (inWorld.isEmpty()) return
        val dust = Dust(Particle.DustOptions(color, 1.0f), radius * radius)
        val near = ArrayList<Viewer>(inWorld.size)
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            near.clear()
            inWorld.filterTo(near) { it.nearSegment(a, b, radius) }
            if (near.isEmpty()) continue
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dz = b.z - a.z
            // One particle about every 0.6 blocks.
            val steps = maxOf(1, (sqrt(dx * dx + dy * dy + dz * dz) / 0.6).toInt())
            for (step in 0..steps) {
                val t = step.toDouble() / steps
                spawnNear(near, a.x + dx * t, a.y + dy * t + LINE_LIFT, a.z + dz * t, dust)
            }
        }
    }

    private fun spawnNear(
        viewers: List<Viewer>,
        x: Double,
        y: Double,
        z: Double,
        dust: Dust,
    ) {
        for (viewer in viewers) {
            if (viewer.distanceSq(x, y, z) <= dust.radiusSq) {
                viewer.player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust.options)
            }
        }
    }

    private fun drawMarker(
        viewers: List<Viewer>,
        point: Point?,
        color: Color,
        radius: Double,
    ) {
        if (point == null) return
        val options = Particle.DustOptions(color, 1.4f)
        val x = point.blockX + 0.5
        val y = point.y + 0.5
        val z = point.blockZ + 0.5
        val radiusSq = radius * radius
        for (viewer in viewers) {
            if (viewer.world == point.world && viewer.distanceSq(x, y, z) <= radiusSq) {
                viewer.player.spawnParticle(
                    Particle.DUST,
                    x,
                    y,
                    z,
                    MARKER_COUNT,
                    MARKER_SPREAD,
                    MARKER_SPREAD,
                    MARKER_SPREAD,
                    0.0,
                    options,
                )
            }
        }
    }

    /** How a line is drawn: the particle and the squared distance up to which viewers see it. */
    private class Dust(
        val options: Particle.DustOptions,
        val radiusSq: Double,
    )

    /** The position of a player, read once per frame. */
    private class Viewer(
        val player: Player,
    ) {
        private val location = player.location

        /** World name, as [Point.world] holds it. */
        val world: String? = location.world?.name
        private val x = location.x
        private val y = location.y
        private val z = location.z

        fun distanceSq(
            px: Double,
            py: Double,
            pz: Double,
        ): Double {
            val dx = px - x
            val dy = py - y
            val dz = pz - z
            return dx * dx + dy * dy + dz * dz
        }

        /** True when the viewer is within [radius] blocks of the box around the segment from [a] to [b]. */
        fun nearSegment(
            a: Point,
            b: Point,
            radius: Double,
        ): Boolean =
            x in minOf(a.x, b.x) - radius..maxOf(a.x, b.x) + radius &&
                y in minOf(a.y, b.y) - radius..maxOf(a.y, b.y) + radius &&
                z in minOf(a.z, b.z) - radius..maxOf(a.z, b.z) + radius
    }

    private companion object {
        /** Blocks above the rail, so the line is not hidden in the block. */
        const val LINE_LIFT = 0.2

        /** Particles in the puff that marks an anchor. */
        const val MARKER_COUNT = 8

        /** Blocks the marker particles spread along each axis. */
        const val MARKER_SPREAD = 0.15
    }
}
