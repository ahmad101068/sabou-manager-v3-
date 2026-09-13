package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPagingTest {
    @Test
    fun window_clamps_page_and_never_drops_tail() {
        val window = uiPageWindow(totalCount = 121, requestedPage = 99, pageSize = 50)
        assertEquals(2, window.pageIndex)
        assertEquals(3, window.pageCount)
        assertEquals(100, window.fromIndex)
        assertEquals(121, window.toIndexExclusive)
        assertEquals(21, window.visibleCount)
        assertTrue(window.hasPrevious)
        assertFalse(window.hasNext)
    }

    @Test
    fun empty_window_is_stable_first_page() {
        val window = uiPageWindow(totalCount = 0, requestedPage = 4, pageSize = 20)
        assertEquals(0, window.pageIndex)
        assertEquals(1, window.pageCount)
        assertEquals(0, window.fromIndex)
        assertEquals(0, window.toIndexExclusive)
    }

    @Test
    fun page_returns_only_requested_slice() {
        val rows = (1..105).toList()
        val window = uiPageWindow(rows.size, requestedPage = 1, pageSize = 50)
        assertEquals((51..100).toList(), rows.page(window))
    }
}
