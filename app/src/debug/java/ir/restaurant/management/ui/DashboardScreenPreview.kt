package ir.restaurant.management.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ir.restaurant.management.data.repository.DashboardPeriod
import ir.restaurant.management.data.repository.DashboardSnapshot
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.security.UserRole

@Preview(showBackground = true, widthDp = 390, heightDp = 844, locale = "fa")
@Composable
private fun DashboardScreenPreview() {
    val snapshot = DashboardSnapshot(
        fromEpochDay = 1L,
        toEpochDay = 1L,
        period = DashboardPeriod.TODAY,
    )
    val user = AppUserRecord(1, "manager", "مدیر شعبه", UserRole.MANAGER, true, false)
    val home = DashboardUxComposer.compose(snapshot, snapshot, user, "مدیریت رستوران")

    MaterialTheme {
        DashboardScreen(
            state = snapshot,
            home = home,
            managementOverview = HomeManagementOverviewUiState(unavailableMessage = "Preview"),
            onSearch = {},
            onSettings = {},
            onToday = {},
            onWeek = {},
            onMonth = {},
            onCustomRange = { _, _ -> },
            onBranchSelected = {},
            onWarehouse = {},
            onOpen = {},
        )
    }
}
