package net.craftgame.metro.edit

/**
 * Slot layout shared by the editor menus so they look alike. Rows have columns 0-8. Header goes in slot 0, Back in
 * slot 8. Lists put the control row below an empty row.
 */
object MenuLayout {
    /** Slots in one row. */
    const val ROW = 9

    /** Slot of the header item. */
    const val HEADER = 0

    /** Slot of the Back button in a one-row menu. */
    const val BACK = 8

    /** Middle column of a row. */
    private const val CENTRE = 4

    /** Up to this many buttons are placed with a gap between them. */
    private const val SPACED_MAX = 4

    /** Rows of the largest chest. */
    private const val MAX_ROWS = 6
    private const val CREATE_COLUMN = 3
    private const val SETTINGS_COLUMN = 5

    /** Rows of a list menu besides the entries: one empty row and the control row. */
    private const val LIST_EXTRA_ROWS = 2

    /** A button in the control row of a list menu, with its column. */
    enum class Control(
        val column: Int,
    ) {
        CREATE(CREATE_COLUMN),
        SETTINGS(SETTINGS_COLUMN),
        BACK(MenuLayout.BACK),
    }

    /** Slots of the anchor action menu. */
    object Actions {
        const val SIZE = ROW * 3

        val ANCHORS = (0..6).toList()
        const val CREATE_JUNCTION = 8

        val NAVIGATION = listOf(18, 19, 20, 22, 24, 26)
    }

    /** The kinds from the left (the current one glows), a gap, the settings, and Delete always last. */
    object JunctionPoint {
        val KINDS = listOf(0, 1, 2)

        val SETTINGS = listOf(4, 5)
        const val DELETE = BACK
    }

    /** Slots of the menu of an anchor point. */
    object AnchorPoint {
        val BUTTONS = listOf(2, 4, 6, 8)
    }

    /** Slots of the line settings menu. */
    object LineSettings {
        val BUTTONS = listOf(0, 2, 4, 6)
    }

    /** Returns the slots for [count] buttons in a one-row menu, centred and between the header and Back slots. */
    fun row(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val step = if (count <= SPACED_MAX) 2 else 1
        val first = (CENTRE - step * (count - 1) / 2.0).toInt().coerceAtLeast(HEADER + 1)
        return List(count) { (first + it * step).coerceAtMost(BACK - 1) }
    }

    /** Too many entries shrink to the rows that fit, so the controls always stay. */
    fun listSize(count: Int): Int {
        val entryRows = ((count + ROW - 1) / ROW).coerceIn(1, MAX_ROWS - LIST_EXTRA_ROWS)
        return (entryRows + LIST_EXTRA_ROWS) * ROW
    }

    /** Returns how many entries fit in a list menu of [size] slots. */
    fun listCapacity(size: Int): Int = size - LIST_EXTRA_ROWS * ROW

    /** [alone] centres a lone control. */
    fun control(
        size: Int,
        control: Control,
        alone: Boolean = false,
    ): Int = size - ROW + if (alone) CENTRE else control.column
}
