package net.craftgame.metro.config

object Permissions {
    const val RIDE = "metro.ride"

    /** Also covers `/metro rebuild`, `/metro issues` and its `[tp]` links. */
    const val BUILD = "metro.build"

    const val RELOAD = "metro.reload"

    const val DEBUG = "metro.debug"

    const val ADMIN = "metro.admin"
}
