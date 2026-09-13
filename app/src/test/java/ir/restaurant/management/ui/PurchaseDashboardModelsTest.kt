package ir.restaurant.management.ui

import ir.restaurant.management.data.repository.DashboardPeriod
import ir.restaurant.management.domain.operations.PurchaseDashboardSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class PurchaseDashboardModelsTest {
    @Test fun periodLabelsArePersianAndSpecific() {
        assertEquals("خرید امروز", PurchaseDashboardPresenter.purchaseTitle(DashboardPeriod.TODAY))
        assertEquals("خرید این هفته", PurchaseDashboardPresenter.purchaseTitle(DashboardPeriod.WEEK))
        assertEquals("خرید این ماه", PurchaseDashboardPresenter.purchaseTitle(DashboardPeriod.MONTH))
    }

    @Test fun noOperationalDataIsEmpty() {
        val ui = PurchaseDashboardPresenter.present(DashboardPeriod.TODAY, PurchaseDashboardSummary())
        assertEquals(PurchaseDashboardLoadStatus.EMPTY, ui.status)
        assertEquals("هنوز داده‌ای برای این بازه ثبت نشده", ui.message)
    }

    @Test fun realSummaryMapsWithoutSyntheticKpis() {
        val ui = PurchaseDashboardPresenter.present(
            DashboardPeriod.MONTH,
            PurchaseDashboardSummary(periodPurchaseRial = 120_000L, openOrderCount = 3, activeSupplierCount = 8, supplierPayablesRial = 50_000L),
        )
        assertEquals(PurchaseDashboardLoadStatus.LOADED, ui.status)
        assertEquals(3, ui.openOrderCount)
        assertEquals(8, ui.activeSupplierCount)
        assertEquals(50_000L, ui.supplierPayablesRial)
    }
}
