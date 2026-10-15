package net.craftgame.metro

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.craftgame.metro.command.MetroCommand
import net.craftgame.metro.config.MetroConfig
import net.craftgame.metro.data.DataStore
import net.craftgame.metro.debug.DebugHub
import net.craftgame.metro.debug.DebugLevel
import net.craftgame.metro.edit.AnchorEditor
import net.craftgame.metro.edit.EditManager
import net.craftgame.metro.edit.JunctionEditor
import net.craftgame.metro.edit.NetworkBrowser
import net.craftgame.metro.edit.PointEditor
import net.craftgame.metro.listener.CartProtectionListener
import net.craftgame.metro.listener.EditListener
import net.craftgame.metro.listener.InteractListener
import net.craftgame.metro.listener.MenuListener
import net.craftgame.metro.listener.RideCleanupListener
import net.craftgame.metro.listener.RideGuardListener
import net.craftgame.metro.platform.PaperRailWorlds
import net.craftgame.metro.platform.PaperScheduler
import net.craftgame.metro.platform.Scheduler
import net.craftgame.metro.preview.HoverLabel
import net.craftgame.metro.preview.NetworkPreview
import net.craftgame.metro.rail.RailWorlds
import net.craftgame.metro.ride.BoardingService
import net.craftgame.metro.ride.CartController
import net.craftgame.metro.route.ChunkPreloader
import net.craftgame.metro.route.ChunkTickets
import net.craftgame.metro.route.Router
import net.craftgame.metro.util.Icons
import net.craftgame.metro.util.PlayerStateStore
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/** Composition root: builds every service in `onEnable` and exposes them to their siblings. */
class MetroPlugin : JavaPlugin() {
    /** Runs every task of the plugin. Use it instead of Bukkit's scheduler. */
    lateinit var scheduler: Scheduler
        private set

    /** Reads and sets rails. Use it instead of reading rail blocks directly. */
    lateinit var railWorlds: RailWorlds
        private set

    lateinit var metroConfig: MetroConfig
        private set
    lateinit var dataStore: DataStore
        private set
    lateinit var router: Router
        private set
    lateinit var controller: CartController
        private set
    lateinit var playerState: PlayerStateStore
        private set
    lateinit var boarding: BoardingService
        private set
    lateinit var editManager: EditManager
        private set
    lateinit var junctionEditor: JunctionEditor
        private set
    lateinit var anchorEditor: AnchorEditor
        private set
    lateinit var networkBrowser: NetworkBrowser
        private set
    lateinit var pointEditor: PointEditor
        private set
    lateinit var networkPreview: NetworkPreview
        private set
    lateinit var hoverLabel: HoverLabel
        private set
    lateinit var chunkPreloader: ChunkPreloader
        private set
    lateinit var chunkTickets: ChunkTickets
        private set

    /** Tags a cart with its ride id in the cart's persistent data, so orphaned carts can be found and removed. */
    val rideKey: NamespacedKey by lazy { NamespacedKey(this, "ride") }

    /** The debug log and its viewers. Not saved: nobody watches after a restart. */
    val debugLog = DebugHub(System::currentTimeMillis)

    override fun onEnable() {
        saveDefaultConfig()
        scheduler = PaperScheduler(this)
        railWorlds = PaperRailWorlds()

        metroConfig = MetroConfig(this).also { it.reload() }
        dataStore =
            DataStore(
                File(dataFolder, "data.yml"),
                logger,
                worldLoaded = { Bukkit.getWorld(it) != null },
                iconName = Icons::usableName,
                defaultIcon = { metroConfig.defaultIcon.name },
                defaultLineName = { metroConfig.raw("editor.default-line-name") },
                debugLog = debugLog,
            ).also { it.load() }
        router =
            Router(
                store = dataStore,
                speeds = { metroConfig.speed },
                reporting = { debugLog.enabled(DebugLevel.DECISION, null) },
                slopeSpeed = { metroConfig.slopeSpeed },
            ) { from, to ->
                debugLog.log(DebugLevel.DECISION, null) { "no rail segment for $from -> $to, using a straight line" }
            }
        playerState = PlayerStateStore(File(dataFolder, "players.yml"), logger).also { it.load() }
        chunkTickets = ChunkTickets(this, debugLog)
        boarding = BoardingService(this)
        editManager = EditManager(this)
        junctionEditor = JunctionEditor(this)
        anchorEditor = AnchorEditor(this)
        networkBrowser = NetworkBrowser(this)
        pointEditor = PointEditor(this)
        networkPreview = NetworkPreview(this)
        hoverLabel = HoverLabel(this).also { it.start() }
        chunkPreloader = ChunkPreloader(this)

        val cleanup = RideCleanupListener(this)
        cleanup.cleanupLoaded()

        controller = CartController(this).also { it.start() }
        networkPreview.start()

        server.pluginManager.registerEvents(EditListener(this), this)
        server.pluginManager.registerEvents(InteractListener(this), this)
        server.pluginManager.registerEvents(MenuListener(this), this)
        server.pluginManager.registerEvents(RideGuardListener(this), this)
        server.pluginManager.registerEvents(CartProtectionListener(this), this)
        server.pluginManager.registerEvents(cleanup, this)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            MetroCommand(this).register(event.registrar())
        }
        logger.info("Metro: ${dataStore.stations.size} stations, ${dataStore.edges.size} segments")
    }

    override fun onDisable() {
        if (::hoverLabel.isInitialized) hoverLabel.stop()
        if (::chunkPreloader.isInitialized) chunkPreloader.cancel()
        if (::controller.isInitialized) controller.stop()
        if (::chunkTickets.isInitialized) chunkTickets.releaseAll()
        if (::playerState.isInitialized) {
            playerState.save()
            playerState.close()
        }
        if (::dataStore.isInitialized) dataStore.close()
        logger.info("Metro disabled")
    }
}
