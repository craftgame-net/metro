package net.craftgame.metro.edit

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.PotionContents
import io.papermc.paper.datacomponent.item.TooltipDisplay
import net.craftgame.metro.MetroPlugin
import net.craftgame.metro.data.Junction
import net.craftgame.metro.util.Palette
import net.craftgame.metro.util.blockCoords
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Menus for the point under the crosshair and for placing a junction. */
object PointMenu {
    const val ANCHOR = "point-anchor"
    const val JUNCTION = "point-junction"
    const val CREATE_JUNCTION = "junction-create"
    const val DELETE_JUNCTION = "confirm-delete-junction"

    /** The holder kinds of these menus, so `EditListener` sends their clicks to [PointEditor]. */
    val KINDS = setOf(ANCHOR, JUNCTION, CREATE_JUNCTION, DELETE_JUNCTION)

    fun openAnchor(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
        point: EditPoint.Anchor,
    ) {
        val station = point.station
        val holder =
            MetroMenuHolder(ANCHOR, station.id, block, platform = point.platform.id, role = point.role)
        val title =
            message(
                plugin,
                "menu.point-anchor-heading",
                "platform" to point.ref,
                "role" to point.role,
            )
        val color = Palette.anchorCode(point.role)
        val header =
            EditMenu.item(
                EditMenu.ICONS[point.role] ?: Material.PAPER,
                message(
                    plugin,
                    "item.point-anchor-label",
                    "color" to color,
                    "role" to point.role,
                    "platform" to point.ref,
                ),
                EditMenu.now(plugin, blockCoords(point.platform.anchor(point.role))),
            )
        val buttons =
            listOf(
                button(plugin, "select", Material.OAK_SIGN, "item.point-select"),
                button(plugin, "platform-settings", Material.COMPARATOR, "item.point-platform-settings"),
                button(plugin, "station-settings", Material.OAK_HANGING_SIGN, "item.point-station-settings"),
                button(plugin, "clear", EditMenu.DELETE_ICON, "item.point-clear"),
            )
        val decor = mapOf(MenuLayout.HEADER to header)
        EditMenu.slots(player, holder, title, decor, MenuLayout.AnchorPoint.BUTTONS, buttons)
    }

    fun openJunction(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
        junction: Junction,
    ) {
        val holder = MetroMenuHolder(JUNCTION, null, block, junctionId = junction.id)
        val kinds = Junction.Kind.entries.map { kindButton(plugin, it, it == junction.kind) }
        val turns =
            if (junction.kind == Junction.Kind.CROSS) {
                val item =
                    EditMenu.item(
                        if (junction.turns) Material.TORCH else Material.REDSTONE_TORCH,
                        message(plugin, "item.junction-turns"),
                        EditMenu.now(plugin, EditMenu.onOff(plugin, junction.turns)),
                    )
                Button("turns", item)
            } else {
                null
            }
        val speed = junction.speed?.toString() ?: message(plugin, "item.auto")
        val speedName = message(plugin, "item.junction-speed")
        val speedItem = EditMenu.item(Material.WIND_CHARGE, speedName, EditMenu.now(plugin, speed))
        val settings = listOfNotNull(turns, Button("speed", speedItem))
        val delete = button(plugin, "delete", EditMenu.DELETE_ICON, "item.delete")
        val placed =
            MenuLayout.JunctionPoint.KINDS.zip(kinds) +
                MenuLayout.JunctionPoint.SETTINGS.zip(settings) +
                (MenuLayout.JunctionPoint.DELETE to delete)
        val title = message(plugin, "menu.point-junction-title", "id" to junction.id)
        EditMenu.slots(player, holder, title, emptyMap(), placed.map { it.first }, placed.map { it.second })
    }

    fun openCreateJunction(
        plugin: MetroPlugin,
        player: Player,
        block: Location,
    ) {
        val holder = MetroMenuHolder(CREATE_JUNCTION, null, block)
        val buttons = Junction.Kind.entries.map { kindButton(plugin, it, false) }
        EditMenu.row(plugin, player, holder, message(plugin, "menu.create-junction-title"), null, buttons, back = true)
    }

    fun openDeleteJunction(
        plugin: MetroPlugin,
        player: Player,
        block: Location?,
        junction: Junction,
    ) {
        val holder = MetroMenuHolder(DELETE_JUNCTION, null, block, junctionId = junction.id)
        EditMenu.confirm(plugin, player, holder, message(plugin, "menu.delete-junction-title", "id" to junction.id))
    }

    private fun kindButton(
        plugin: MetroPlugin,
        kind: Junction.Kind,
        current: Boolean,
    ): Button {
        val name = kind.name.lowercase()
        val color = Palette.code(Palette.junction(kind, true))
        return Button(name, kindIcon(kind, message(plugin, "item.junction-$name", "color" to color), current))
    }

    private fun kindIcon(
        kind: Junction.Kind,
        name: String,
        glint: Boolean,
    ): ItemStack {
        val item = EditMenu.item(Material.TIPPED_ARROW, name, glint = glint)
        val color = Color.fromRGB(Palette.junction(kind, true))
        item.setData(DataComponentTypes.POTION_CONTENTS, PotionContents.potionContents().customColor(color).build())
        item.setData(
            DataComponentTypes.TOOLTIP_DISPLAY,
            TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.POTION_CONTENTS).build(),
        )
        return item
    }

    private fun button(
        plugin: MetroPlugin,
        action: String,
        material: Material,
        key: String,
    ): Button = Button(action, EditMenu.item(material, message(plugin, key)))

    private fun message(
        plugin: MetroPlugin,
        key: String,
        vararg placeholders: Pair<String, String>,
    ): String = plugin.metroConfig.message(key, *placeholders)
}
