package ir.sabou.inventory.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ir.sabou.inventory.data.repository.DashboardPeriod
import ir.sabou.inventory.data.repository.DashboardSnapshot
import ir.sabou.inventory.domain.operations.AppUserRecord
import ir.sabou.inventory.domain.security.UserRole

@Preview(showBackground = true, widthDp = 390, heightDp = 844, locale = "fa")
@Composable
private fun DashboardScreenPreview() {
    val snapshot = DashboardSnapshot(
        fromEpochDay = 1L,
        toEpochDay = 1L,
        period = DashboardPeriod.TODAY,
    )
    val user = AppUserRecord(1, "manager", "مدیر شعبه", UserRole.MANAGER, true, false)
    val home = DashboardUxComposer.compose(snapshot, snapshot, user, "سبوی عشق")

    MaterialTheme {
        DashboardScreen(snapshot, home, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
    }
}
