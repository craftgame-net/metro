package net.craftgame.metro.gui

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Station
import net.craftgame.metro.ride.Destination
import net.craftgame.metro.ride.DestinationSearch
import net.craftgame.metro.ride.Options
import net.craftgame.metro.util.Icons
import net.craftgame.metro.util.Paging
import net.craftgame.metro.util.Text
import net.craftgame.metro.util.iconMaterial
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/** Names shown in the chat message when a player clicks an unavailable entry. */
data class BoardingHint(
    val station: String,
    val platform: String,
)

/** One item of the menu. */
class BoardingEntry(
    val item: ItemStack,
    /** What a click does: a stop id (see [Nodes.stop]) to ride to, or one of the `*_ACTION` values. */
    val action: String,
    val hint: BoardingHint? = null,
)

/** The open boarding menu of one player, with the boarding it belongs to. Closing the menu drops the boarding. */
class BoardingMenuHolder(
    val stationId: String,
    val platformId: String,
    originSpot: Location,
) : InventoryHolder {
    /** Where the player stood when they pressed the `board` anchor. */
    val originSpot: Location = originSpot.clone()

    /** Action of each filled slot, by slot index. */
    val slots: MutableMap<Int, String> = HashMap()

    /** Hint of each unavailable-destination slot, by slot index. */
    val hints: MutableMap<Int, BoardingHint> = HashMap()

    /** In menu order: own, slower, then unavailable. A paged menu shows a slice. */
    val entries: MutableList<BoardingEntry> = ArrayList()

    /** From 1. Turning it re-renders the same inventory, so the boarding stays. */
    var page = 1

    /** True once a destination was picked, so the boarding is confirmed at most once. */
    var selected = false
    private lateinit var inventory: Inventory

    override fun getInventory(): Inventory = inventory

    /** Sets the inventory of this holder. Call once, before the inventory is opened. */
    fun attach(inventory: Inventory) {
        this.inventory = inventory
    }
}

object BoardingMenu {
    /** Action values of the buttons, which are not a stop id. */
    const val EXIT_ACTION = "__exit__"

    const val HINT_ACTION = "__hint__"

    const val PREVIOUS_ACTION = "__previous__"

    const val NEXT_ACTION = "__next__"

    /**
     * Fills [holder] from [options], which must be boardable, and opens the menu for [player]. Call on the main
     * thread.
     */
    fun open(
        plugin: MetroPlugin,
        player: Player,
        origin: Station,
        options: Options,
        holder: BoardingMenuHolder,
    ) {
        holder.entries += entries(plugin, origin, options)
        val size = BoardingLayout.size(holder.entries.size)
        val inventory = Bukkit.createInventory(holder, size, Text.of(title(plugin, origin, options)))
        holder.attach(inventory)
        render(plugin, holder)
        player.openInventory(inventory)
    }

    /** Moves the page of [holder] by [delta], within the existing pages, and redraws the open menu in place. */
    fun turn(
        plugin: MetroPlugin,
        holder: BoardingMenuHolder,
        delta: Int,
    ) {
        holder.page = (holder.page + delta).coerceIn(1, BoardingLayout.pages(holder.entries.size))
        render(plugin, holder)
    }

    private fun entries(
        plugin: MetroPlugin,
        origin: Station,
        options: Options,
    ): List<BoardingEntry> {
        val entries = ArrayList<BoardingEntry>()
        for (destination in options.own) {
            val item = destination.item(plugin, destination.station.display, time(plugin, destination))
            entries += BoardingEntry(item, Nodes.stop(destination.station.id, destination.platform))
        }
        for (hint in options.slower) {
            val destination = hint.destination
            val platform = DestinationSearch.platformName(plugin, origin, hint.platform)
            val name = plugin.metroConfig.message("boarding.slower-name", "station" to destination.station.display)
            val lore = plugin.metroConfig.message("boarding.slower-lore", "platform" to platform)
            val item = destination.item(plugin, name, time(plugin, destination), lore)
            entries += BoardingEntry(item, Nodes.stop(destination.station.id, destination.platform))
        }
        for (hint in options.unavailable) {
            val station = hint.destination.station
            val platform = DestinationSearch.platformName(plugin, origin, hint.platform)
            val name = plugin.metroConfig.message("boarding.hint-name", "station" to station.display)
            val lore = plugin.metroConfig.message("boarding.hint-lore", "platform" to platform)
            val item = hint.destination.item(plugin, name, null, lore)
            entries += BoardingEntry(item, HINT_ACTION, BoardingHint(station.display, platform))
        }
        return entries
    }

    private fun render(
        plugin: MetroPlugin,
        holder: BoardingMenuHolder,
    ) {
        val inventory = holder.inventory
        inventory.clear()
        holder.slots.clear()
        holder.hints.clear()
        val total = holder.entries.size
        val paged = BoardingLayout.paged(total)
        val shown = if (paged) Paging.slice(holder.entries, holder.page, BoardingLayout.PAGE_SIZE) else holder.entries

        fun put(
            slot: Int,
            item: ItemStack,
            action: String,
        ) {
            inventory.setItem(slot, item)
            holder.slots[slot] = action
        }
        shown.forEachIndexed { slot, entry ->
            put(slot, entry.item, entry.action)
            entry.hint?.let { holder.hints[slot] = it }
        }
        put(BoardingLayout.exitSlot(total), exitItem(plugin), EXIT_ACTION)
        if (paged) {
            val pages = BoardingLayout.pages(total)
            val page = holder.page
            if (page > 1) {
                val previous = arrow(plugin, "boarding.page-previous", page - 1, pages)
                put(BoardingLayout.PREVIOUS_SLOT, previous, PREVIOUS_ACTION)
            }
            if (page < pages) {
                put(BoardingLayout.NEXT_SLOT, arrow(plugin, "boarding.page-next", page + 1, pages), NEXT_ACTION)
            }
        }
    }

    private fun arrow(
        plugin: MetroPlugin,
        key: String,
        target: Int,
        pages: Int,
    ): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta ?: return item
        meta.displayName(Text.of(plugin.metroConfig.message(key, "page" to "$target", "pages" to "$pages")))
        item.itemMeta = meta
        return item
    }

    private fun title(
        plugin: MetroPlugin,
        origin: Station,
        options: Options,
    ): String {
        val elsewhere =
            options.slower
                .groupingBy { it.platform }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
        return if (options.own.isEmpty() && elsewhere != null) {
            val platform = DestinationSearch.platformName(plugin, origin, elsewhere)
            plugin.metroConfig.message("boarding.title-elsewhere", "platform" to platform)
        } else {
            plugin.metroConfig.message("boarding.title")
        }
    }

    /** Returns the ride time of [destination] as text, rounded up to whole seconds. */
    private fun time(
        plugin: MetroPlugin,
        destination: Destination,
    ): String {
        val seconds = (destination.ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND
        return if (seconds < SECONDS_PER_MINUTE) {
            plugin.metroConfig.message("boarding.time-seconds", "s" to seconds.toString())
        } else {
            plugin.metroConfig.message(
                "boarding.time-minutes",
                "m" to (seconds / SECONDS_PER_MINUTE).toString(),
                "s" to (seconds % SECONDS_PER_MINUTE).toString(),
            )
        }
    }

    /**
     * Builds the menu item of this destination: [name], then the [time] text (null: none), the line it runs via when
     * that is not the origin line, and [extraLore].
     */
    private fun Destination.item(
        plugin: MetroPlugin,
        name: String,
        time: String?,
        extraLore: String? = null,
    ): ItemStack {
        val fallback = plugin.metroConfig.defaultIcon
        val item = ItemStack(iconMaterial(station.icon, fallback).takeIf(Icons::usable) ?: fallback)
        val meta = item.itemMeta ?: return item
        meta.displayName(Text.of(name))
        val lore = listOfNotNull(time).toMutableList()
        val viaLine = line?.takeIf { !onOriginLine }
        if (viaLine != null) {
            val lineName = plugin.dataStore.lines[viaLine]?.display ?: viaLine
            lore += plugin.metroConfig.message("boarding.via-line", "line" to lineName)
        }
        extraLore?.let { lore += it }
        meta.lore(lore.map { Text.of(it) })
        item.itemMeta = meta
        return item
    }

    private fun exitItem(plugin: MetroPlugin): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta ?: return item
        meta.displayName(Text.of(plugin.metroConfig.message("boarding.exit")))
        item.itemMeta = meta
        return item
    }

    private const val TICKS_PER_SECOND = 20
    private const val SECONDS_PER_MINUTE = 60
}
