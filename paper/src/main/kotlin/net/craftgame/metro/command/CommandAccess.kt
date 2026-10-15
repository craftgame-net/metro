package net.craftgame.metro.command

import io.papermc.paper.command.brigadier.CommandSourceStack
import java.util.function.Predicate

/** Returns a test for `requires` that hides a command node from senders without the permission [node]. */
internal fun permission(node: String): Predicate<CommandSourceStack> = Predicate { it.sender.hasPermission(node) }
