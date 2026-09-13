package ir.restaurant.management.ui

internal enum class GridKeyboardKey { TAB, ENTER, ESCAPE, OTHER }
internal enum class GridKeyboardEventType { DOWN, OTHER }

internal enum class GridKeyboardCommand {
    NEXT_CELL,
    PREVIOUS_CELL,
    COMMIT_ROW,
    CANCEL_ROW,
    COMMIT_ALL,
    NONE,
}

internal fun resolveGridKeyboardCommand(
    key: GridKeyboardKey,
    eventType: GridKeyboardEventType,
    shiftPressed: Boolean = false,
    ctrlPressed: Boolean = false,
): GridKeyboardCommand {
    if (eventType != GridKeyboardEventType.DOWN) return GridKeyboardCommand.NONE
    return when {
        key == GridKeyboardKey.ENTER && ctrlPressed -> GridKeyboardCommand.COMMIT_ALL
        key == GridKeyboardKey.TAB && shiftPressed -> GridKeyboardCommand.PREVIOUS_CELL
        key == GridKeyboardKey.TAB -> GridKeyboardCommand.NEXT_CELL
        key == GridKeyboardKey.ENTER -> GridKeyboardCommand.COMMIT_ROW
        key == GridKeyboardKey.ESCAPE -> GridKeyboardCommand.CANCEL_ROW
        else -> GridKeyboardCommand.NONE
    }
}
