package net.craftgame.metro.ride

/** Why a ride ended without arriving. [messageKey] is the lang text that tells the rider. */
enum class RideAbort(
    val messageKey: String,
) {
    /** The cart made no progress. */
    STALLED("boarding.abort-stalled"),

    /** The cart entity is gone. */
    CART_LOST("boarding.abort-cart-lost"),

    /** The rider quit, died or left the cart. */
    RIDER_LEFT("boarding.abort-left-cart"),

    /** The tick threw an exception. */
    FAILED("boarding.abort-failed"),

    /** The plugin is stopping. */
    SHUTDOWN("boarding.abort-shutdown"),
}
