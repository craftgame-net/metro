package net.craftgame.metro.edit

import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Line
import net.craftgame.metro.data.Nodes
import net.craftgame.metro.data.Platform
import net.craftgame.metro.data.Station
import net.craftgame.metro.edit.MenuLayout.Control
import net.craftgame.metro.ride.DestinationSearch
import net.craftgame.metro.util.Icons
import net.craftgame.metro.util.Palette
import net.craftgame.metro.util.Text
import net.craftgame.metro.util.blockCoords
import net.craftgame.metro.util.iconMaterial
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/** Inventory holder of an editor menu. [slots] maps a slot to the choice its click reports. */
class MetroMenuHolder(
    /** Menu type. `EditListener` uses it to send a click to the right editor. */
    val kind: String,
    val stationId: String?,
    /** The block the menu was opened for. Null when it was opened by command without a block. */
    val block: Location?,
    val slots: MutableMap<Int, String> = HashMap(),
    /** Platform id, or an empty string when the menu is not about a platform. */
    val platform: String = "",
    val lineId: String? = null,
    val junctionId: String? = null,
    /** Anchor role of a point menu. */
    val role: String? = null,
) : InventoryHolder {
    private lateinit var inventory: Inventory

    override fun getInventory(): Inventory = inventory

    /** Sets the inventory of this holder. Call once, before the inventory is opened. */
    fun attach(inventory: Inventory) {
        this.inventory = inventory
    }
}

/** A menu item. [action] is what a click on it reports. */
class Button(
    val action: String,
    val item: ItemStack,
)

object EditMenu {
    /** Item shown for each anchor role and for `heading`. */
    internal val ICONS =
        linkedMapOf(
            "board" to Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
            "begin" to Material.MINECART,
            "out" to Material.POWERED_RAIL,
            "in" to Material.DETECTOR_RAIL,
            "eject" to Material.HOPPER,
            "land" to Material.END_STONE,
            "heading" to Material.COMPASS,
        )

    internal val DELETE_ICON = Material.LAVA_BUCKET

    fun openLines(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
    ) {
        val holder = MetroMenuHolder("lines", null, block)
        val lines = plugin.dataStore.lines.values
        val entries = lines.map { Button(it.id, lineItem(plugin, it)) }
        val create = Button("__create__", item(Material.PAPER, text(plugin, "item.create-line")))
        val controls = mapOf(Control.CREATE to create)
        list(player, holder, text(plugin, "menu.lines-title"), entries, controls)
    }

    fun openStations(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
        line: Line,
    ) {
        val holder = MetroMenuHolder("stations", null, block, lineId = line.id)
        val entries = plugin.dataStore.stationsOf(line.id).map { Button(it.id, stationItem(plugin, it)) }
        val controls =
            mapOf(
                Control.CREATE to Button("__create__", item(Material.PAPER, text(plugin, "item.create-station"))),
                Control.SETTINGS to Button("__settings__", item(Material.COMPARATOR, text(plugin, "item.settings"))),
                Control.BACK to backButton(plugin, "__lines__"),
            )
        list(player, holder, text(plugin, "menu.stations-title", "line" to line.display), entries, controls)
    }

    fun openPlatforms(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
        station: Station,
    ) {
        val holder = MetroMenuHolder("platform", station.id, block)
        val entries =
            station.platforms.map { platform ->
                val name = DestinationSearch.platformName(plugin, station, platform.id)
                Button(platform.id, platformItem(plugin, platform, name))
            }
        val controls =
            mapOf(
                Control.CREATE to Button("__add__", item(Material.PAPER, text(plugin, "item.create-platform"))),
                Control.SETTINGS to Button("__settings__", item(Material.COMPARATOR, text(plugin, "item.settings"))),
                Control.BACK to backButton(plugin, "__stations__"),
            )
        list(player, holder, text(plugin, "menu.platforms-title", "station" to station.display), entries, controls)
    }

    fun openLineSettings(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
        line: Line,
    ) {
        val holder = MetroMenuHolder("line-settings", null, block, lineId = line.id)
        val speed = line.speed?.toString() ?: text(plugin, "item.auto")
        val buttons =
            listOf(
                Button("line-name", item(Material.NAME_TAG, text(plugin, "item.name"))),
                Button("line-icon", item(Material.ITEM_FRAME, text(plugin, "item.icon"))),
                Button("line-speed", item(Material.WIND_CHARGE, text(plugin, "item.line-speed"), now(plugin, speed))),
                Button("line-delete", item(DELETE_ICON, text(plugin, "item.delete"))),
                backButton(plugin, "__back__"),
            )
        val title = text(plugin, "menu.line-settings-title", "line" to line.display)
        val slots = MenuLayout.LineSettings.BUTTONS + MenuLayout.BACK
        slots(player, holder, title, emptyMap(), slots, buttons)
    }

    fun openStationSettings(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
        station: Station,
    ) {
        val holder = MetroMenuHolder("station-settings", station.id, block)
        val terminal =
            item(
                if (station.terminal) Material.REDSTONE_TORCH else Material.TORCH,
                text(plugin, "item.terminal"),
                now(plugin, onOff(plugin, station.terminal)),
            )
        val buttons =
            listOf(
                Button("name", item(Material.NAME_TAG, text(plugin, "item.name"))),
                Button("icon", item(Material.ITEM_FRAME, text(plugin, "item.icon"))),
                Button("terminal", terminal),
                Button("delete", item(DELETE_ICON, text(plugin, "item.delete"))),
            )
        val title = text(plugin, "menu.station-settings-title", "station" to station.display)
        row(plugin, player, holder, title, stationItem(plugin, station), buttons, back = true)
    }

    fun openPlatformSettings(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
        station: Station,
        platform: Platform,
    ) {
        val holder = MetroMenuHolder("platform-settings", station.id, block, platform = platform.id)
        val name = DestinationSearch.platformName(plugin, station, platform.id)
        val buttons =
            listOf(
                Button("platform-name", item(Material.NAME_TAG, text(plugin, "item.name"))),
                Button("platform-heading", headingItem(plugin, platform)),
                Button("platform-icon", item(Material.ITEM_FRAME, text(plugin, "item.icon"))),
            )
        val title = text(plugin, "menu.platform-settings-title", "platform" to name)
        row(plugin, player, holder, title, platformItem(plugin, platform, name), buttons, back = true)
    }

    fun openActions(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
        station: Station,
        platform: String,
    ) {
        val holder = MetroMenuHolder("actions", station.id, block, platform = platform)
        val current = station.platform(platform) ?: Platform(id = platform)
        val name = DestinationSearch.platformName(plugin, station, platform)
        val title = text(plugin, "menu.actions-title", "station" to station.display, "platform" to name)
        val inventory = create(holder, MenuLayout.Actions.SIZE, title)
        val anchors =
            Platform.ROLES.map { Button(it, anchorItem(plugin, current, it)) } +
                Button("heading", headingItem(plugin, current, glint = current.heading != null))
        place(inventory, holder, MenuLayout.Actions.ANCHORS, anchors)
        val junction = Button("create-junction", item(Material.LEVER, text(plugin, "item.create-junction")))
        place(inventory, holder, listOf(MenuLayout.Actions.CREATE_JUNCTION), listOf(junction))
        val navigation =
            listOf(
                Button("lines", item(Material.RAIL, text(plugin, "item.lines"))),
                Button("stations", item(Material.OAK_HANGING_SIGN, text(plugin, "item.stations"))),
                Button("platforms", item(Material.OAK_SIGN, text(plugin, "item.platforms"))),
                Button("platform-settings", item(Material.COMPARATOR, text(plugin, "item.settings"))),
                Button("delete-platform", item(DELETE_ICON, text(plugin, "item.delete"))),
                backButton(plugin, "platforms"),
            )
        place(inventory, holder, MenuLayout.Actions.NAVIGATION, navigation)
        player.openInventory(inventory)
    }

    fun openLineDeleteConfirm(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
        line: Line,
    ) {
        val holder = MetroMenuHolder("confirm-delete-line", null, block, lineId = line.id)
        val lore = text(plugin, "item.confirm-delete-line-lore", "count" to line.stations.size.toString())
        confirm(plugin, player, holder, text(plugin, "menu.line-delete-title", "id" to line.id), lore)
    }

    fun openDeleteConfirm(
        plugin: MetroPlugin,
        player: Player,
        block: Location?,
        station: Station,
    ) {
        val holder = MetroMenuHolder("confirm-delete", station.id, block)
        confirm(plugin, player, holder, text(plugin, "menu.delete-station-title", "station" to station.display))
    }

    fun openDeletePlatformConfirm(
        plugin: MetroPlugin,
        player: Player,
        block: Location?,
        station: Station,
        platform: String,
    ) {
        val holder = MetroMenuHolder("confirm-delete-platform", station.id, block, platform = platform)
        val title = text(plugin, "menu.delete-platform-prompt", "platform" to Nodes.stop(station.id, platform))
        confirm(plugin, player, holder, title)
    }

    /**
     * Opens a one-row menu: [header] in slot 0 (null: none), [buttons] centred, and a Back button in slot 8 when
     * [back] is true.
     */
    internal fun row(
        plugin: MetroPlugin,
        player: Player,
        holder: MetroMenuHolder,
        title: String,
        header: ItemStack?,
        buttons: List<Button>,
        back: Boolean,
    ) {
        val inventory = create(holder, MenuLayout.ROW, title)
        if (header != null) inventory.setItem(MenuLayout.HEADER, header)
        place(inventory, holder, MenuLayout.row(buttons.size), buttons)
        if (back) place(inventory, holder, listOf(MenuLayout.BACK), listOf(backButton(plugin, "__back__")))
        player.openInventory(inventory)
    }

    /** Opens a one-row menu with [decor] items (slot to item, not clickable) and [buttons] placed in [slots]. */
    internal fun slots(
        player: Player,
        holder: MetroMenuHolder,
        title: String,
        decor: Map<Int, ItemStack>,
        slots: List<Int>,
        buttons: List<Button>,
    ) {
        val inventory = create(holder, MenuLayout.ROW, title)
        decor.forEach { (slot, item) -> inventory.setItem(slot, item) }
        place(inventory, holder, slots, buttons)
        player.openInventory(inventory)
    }

    /** Opens a yes/no menu. The buttons report `yes` and `no`. [yesLore] is extra text on the yes button. */
    internal fun confirm(
        plugin: MetroPlugin,
        player: Player,
        holder: MetroMenuHolder,
        title: String,
        yesLore: String? = null,
    ) {
        val buttons =
            listOf(
                Button("yes", item(Material.REDSTONE, text(plugin, "item.confirm-delete"), yesLore)),
                Button("no", item(Material.GLOWSTONE_DUST, text(plugin, "item.cancel"))),
            )
        row(plugin, player, holder, title, null, buttons, back = false)
    }

    internal fun backButton(
        plugin: MetroPlugin,
        action: String,
    ): Button = Button(action, item(Material.ARROW, text(plugin, "item.back")))

    /** Returns the text for a current setting, e.g. `now: 0.6`. */
    internal fun now(
        plugin: MetroPlugin,
        value: String,
    ): String = text(plugin, "item.now", "value" to value)

    internal fun onOff(
        plugin: MetroPlugin,
        on: Boolean,
    ): String = text(plugin, if (on) "item.state-on" else "item.state-off")

    /** Builds a menu item. A material that cannot be an item icon becomes a barrier. [glint] adds the enchant glow. */
    internal fun item(
        material: Material,
        name: String,
        lore: String? = null,
        glint: Boolean = false,
    ): ItemStack {
        val item = ItemStack(material.takeIf(Icons::usable) ?: Material.BARRIER)
        val meta = item.itemMeta ?: return item
        meta.displayName(Text.of(name))
        if (lore != null) meta.lore(listOf(Text.of(lore)))
        if (glint) meta.setEnchantmentGlintOverride(true)
        item.itemMeta = meta
        return item
    }

    /** Opens a list menu of [entries] with [controls] in the bottom row. Entries beyond the capacity are cut. */
    private fun list(
        player: Player,
        holder: MetroMenuHolder,
        title: String,
        entries: List<Button>,
        controls: Map<Control, Button>,
    ) {
        val size = MenuLayout.listSize(entries.size)
        val inventory = create(holder, size, title)
        val shown = entries.take(MenuLayout.listCapacity(size))
        place(inventory, holder, shown.indices.toList(), shown)
        val alone = controls.size == 1
        controls.forEach { (control, button) ->
            place(inventory, holder, listOf(MenuLayout.control(size, control, alone)), listOf(button))
        }
        player.openInventory(inventory)
    }

    private fun place(
        inventory: Inventory,
        holder: MetroMenuHolder,
        slots: List<Int>,
        buttons: List<Button>,
    ) {
        slots.zip(buttons).forEach { (slot, button) ->
            inventory.setItem(slot, button.item)
            holder.slots[slot] = button.action
        }
    }

    private fun create(
        holder: MetroMenuHolder,
        size: Int,
        title: String,
    ): Inventory {
        val inventory = Bukkit.createInventory(holder, size, Text.of(title))
        holder.attach(inventory)
        return inventory
    }

    private fun lineItem(
        plugin: MetroPlugin,
        line: Line,
    ): ItemStack =
        item(
            iconMaterial(line.icon, Material.COMPASS),
            text(plugin, "item.line", "display" to line.display, "id" to line.id),
            text(plugin, "item.line-lore", "count" to line.stations.size.toString()),
        )

    private fun stationItem(
        plugin: MetroPlugin,
        station: Station,
    ): ItemStack =
        item(
            iconMaterial(station.icon, Material.COMPASS),
            text(plugin, "item.station", "display" to station.display, "id" to station.id),
            text(plugin, "item.station-lore", "count" to station.platforms.size.toString()),
        )

    private fun platformItem(
        plugin: MetroPlugin,
        platform: Platform,
        name: String,
    ): ItemStack =
        item(
            iconMaterial(platform.icon, Material.OAK_SIGN),
            text(plugin, "item.platform", "name" to name),
            works(plugin, platform),
        )

    /** Returns lore that tells whether the platform serves boarding, alighting or neither. */
    private fun works(
        plugin: MetroPlugin,
        platform: Platform,
    ): String {
        val what =
            listOfNotNull(
                text(plugin, "item.works-boarding").takeIf { platform.boards },
                text(plugin, "item.works-alighting").takeIf { platform.alights },
            )
        return if (what.isEmpty()) {
            text(plugin, "item.works-nothing")
        } else {
            text(plugin, "item.works", "what" to what.joinToString(", "))
        }
    }

    private fun anchorItem(
        plugin: MetroPlugin,
        platform: Platform,
        role: String,
    ): ItemStack {
        val color = Palette.anchorCode(role)
        val anchor = platform.anchor(role)
        val lore = anchor?.let { now(plugin, blockCoords(it)) }
        return item(ICONS.getValue(role), text(plugin, "item.$role", "color" to color), lore, glint = anchor != null)
    }

    private fun headingItem(
        plugin: MetroPlugin,
        platform: Platform,
        glint: Boolean = false,
    ): ItemStack {
        val heading = platform.heading?.name?.lowercase() ?: text(plugin, "item.auto")
        return item(Material.COMPASS, text(plugin, "item.heading"), now(plugin, heading), glint)
    }

    private fun text(
        plugin: MetroPlugin,
        key: String,
        vararg placeholders: Pair<String, String>,
    ): String = plugin.metroConfig.message(key, *placeholders)
}
