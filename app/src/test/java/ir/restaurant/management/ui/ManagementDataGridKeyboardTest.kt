package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ManagementDataGridKeyboardTest {
    @Test fun `editable grid maps complete keyboard workflow`() {
        assertEquals(GridKeyboardCommand.NEXT_CELL, command(GridKeyboardKey.TAB))
        assertEquals(GridKeyboardCommand.PREVIOUS_CELL, command(GridKeyboardKey.TAB, shift = true))
        assertEquals(GridKeyboardCommand.COMMIT_ROW, command(GridKeyboardKey.ENTER))
        assertEquals(GridKeyboardCommand.CANCEL_ROW, command(GridKeyboardKey.ESCAPE))
        assertEquals(GridKeyboardCommand.COMMIT_ALL, command(GridKeyboardKey.ENTER, ctrl = true))
        assertEquals(
            GridKeyboardCommand.NONE,
            resolveGridKeyboardCommand(GridKeyboardKey.TAB, GridKeyboardEventType.OTHER),
        )
    }

    private fun command(key: GridKeyboardKey, shift: Boolean = false, ctrl: Boolean = false) =
        resolveGridKeyboardCommand(key, GridKeyboardEventType.DOWN, shift, ctrl)
}
