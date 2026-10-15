package net.craftgame.metro.gui

import net.craftgame.metro.util.Paging

/**
 * Slots of the boarding menu. It is as tall as needed with the exit in its last slot, or paged when entries and exit
 * do not fit in one chest. It uses no server state.
 */
object BoardingLayout {
    /** Slots in one chest row. */
    const val ROW = 9

    /** Slots of the largest chest. */
    const val MAX_SLOTS = 54

    /** Entries on one page of a paged menu. The last row holds the page controls. */
    const val PAGE_SIZE = MAX_SLOTS - ROW

    const val PREVIOUS_SLOT = PAGE_SIZE
    const val EXIT_SLOT = PAGE_SIZE + ROW / 2
    const val NEXT_SLOT = MAX_SLOTS - 1

    /** True when [entries] plus the exit button do not fit in one chest. */
    fun paged(entries: Int): Boolean = entries + 1 > MAX_SLOTS

    /** Returns the number of pages for [entries], which is 1 when the menu is not paged. */
    fun pages(entries: Int): Int = if (paged(entries)) Paging.pages(entries, PAGE_SIZE) else 1

    /** Returns the inventory size in slots: whole rows for the entries and the exit, or a full chest when paged. */
    fun size(entries: Int): Int {
        val rows = (entries + 1 + ROW - 1) / ROW
        return if (paged(entries)) MAX_SLOTS else (rows * ROW).coerceAtLeast(ROW)
    }

    /** Returns the slot of the exit button. */
    fun exitSlot(entries: Int): Int = if (paged(entries)) EXIT_SLOT else size(entries) - 1
}
