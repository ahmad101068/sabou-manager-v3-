package ir.restaurant.management.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Android window classes used by the ERP shell. They intentionally depend only on width. */
enum class ErpWindowClass { COMPACT, MEDIUM, EXPANDED }

internal fun classifyErpWindow(widthDp: Int): ErpWindowClass = when {
    widthDp < 600 -> ErpWindowClass.COMPACT
    widthDp < 840 -> ErpWindowClass.MEDIUM
    else -> ErpWindowClass.EXPANDED
}

@Composable
internal fun currentErpWindowClass(): ErpWindowClass =
    classifyErpWindow(LocalConfiguration.current.screenWidthDp)

@Composable
internal fun ErpResponsiveNavigationFrame(
    selected: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    content: @Composable () -> Unit,
) {
    val window = currentErpWindowClass()
    if (window == ErpWindowClass.COMPACT) {
        content()
        return
    }

    Row(Modifier.fillMaxSize()) {
        ErpNavigationRail(
            selected = selected,
            onNavigate = onNavigate,
            expanded = window == ErpWindowClass.EXPANDED,
        )
        Column(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

private data class ErpTopLevelDestination(
    val screen: AppScreen,
    val label: String,
    val icon: ImageVector,
)

private val erpTopLevelDestinations = listOf(
    ErpTopLevelDestination(AppScreen.DASHBOARD, "خانه", Icons.Outlined.Home),
    ErpTopLevelDestination(AppScreen.CONTROL_HUB, "کنترل", Icons.Outlined.Assessment),
    ErpTopLevelDestination(AppScreen.OPERATIONS_HUB, "عملیات", Icons.Outlined.Inventory2),
    ErpTopLevelDestination(AppScreen.FINANCE_HUB, "مالی", Icons.Outlined.AccountBalanceWallet),
    ErpTopLevelDestination(AppScreen.MORE, "بیشتر", Icons.Outlined.MoreHoriz),
)

@Composable
private fun ErpNavigationRail(
    selected: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    expanded: Boolean,
) {
    val selectedTopLevel = selected.topLevelDestination()
    val railWidth = if (expanded) 180.dp else 88.dp
    Surface(
        modifier = Modifier.width(railWidth).fillMaxHeight().testTag("erp_navigation_rail"),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = Color.White,
            header = {
                Column(
                    modifier = Modifier.padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ManagementGeometricLogo()
                    if (expanded) {
                        Text(
                            "مدیریت رستوران",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = ErpPalette.Ink,
                        )
                    }
                }
            },
        ) {
            erpTopLevelDestinations.forEach { destination ->
                val isSelected = selectedTopLevel == destination.screen
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onNavigate(destination.screen) },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    alwaysShowLabel = expanded,
                    modifier = Modifier.testTag("rail_${destination.screen.name.lowercase()}"),
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = ErpPalette.Indigo,
                        selectedTextColor = ErpPalette.Indigo,
                        indicatorColor = ErpPalette.IndigoSoft,
                        unselectedIconColor = ErpPalette.Muted,
                        unselectedTextColor = ErpPalette.Muted,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun ResponsiveContentSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val window = currentErpWindowClass()
    val horizontal = when (window) {
        ErpWindowClass.COMPACT -> 0.dp
        ErpWindowClass.MEDIUM -> 16.dp
        ErpWindowClass.EXPANDED -> 28.dp
    }
    Surface(
        modifier = modifier.fillMaxSize().padding(horizontal = horizontal),
        color = ErpPalette.Canvas,
        shape = if (window == ErpWindowClass.COMPACT) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
    ) { content() }
}
