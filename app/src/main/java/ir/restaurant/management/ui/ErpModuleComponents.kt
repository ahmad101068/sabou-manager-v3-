package ir.restaurant.management.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Small shared building blocks for feature dashboards. Business data is supplied by callers. */
@Composable
internal fun ErpModuleHeader(
    title: String,
    subtitle: String,
    onRefresh: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        ManagementGeometricLogo()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ErpPalette.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ErpPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "تازه‌سازی", tint = ErpPalette.Ink)
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun ErpQuickActionTile(
    title: String,
    icon: ImageVector,
    soft: Color,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ErpPalette.Border),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PastelIcon(icon, soft, if (enabled) accent else ErpPalette.Muted)
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) ErpPalette.Ink else ErpPalette.Muted,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ErpAttentionRow(
    title: String,
    description: String,
    icon: ImageVector = Icons.Outlined.WarningAmber,
    accent: Color = ErpPalette.Amber,
    soft: Color = ErpPalette.AmberSoft,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        shape = RoundedCornerShape(19.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ErpPalette.Border),
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(accent, RoundedCornerShape(9.dp)))
            PastelIcon(icon, soft, accent, Modifier.padding(start = 10.dp))
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = ErpPalette.Ink)
                Text(description, style = MaterialTheme.typography.bodySmall, color = ErpPalette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (onClick != null) Icon(Icons.Outlined.ChevronLeft, contentDescription = "باز کردن", tint = ErpPalette.Muted.copy(alpha = .55f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun ErpStatePanel(
    title: String,
    description: String,
    isError: Boolean = false,
    retry: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isError) ErpPalette.RedSoft else Color.White),
        border = BorderStroke(1.dp, if (isError) ErpPalette.Red.copy(alpha = .24f) else ErpPalette.Border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (isError) ErpPalette.Red else ErpPalette.Ink)
            Text(description, style = MaterialTheme.typography.bodySmall, color = ErpPalette.Muted)
            if (retry != null) {
                Surface(onClick = retry, color = Color.Transparent) {
                    Text("تلاش دوباره", modifier = Modifier.padding(vertical = 6.dp), color = ErpPalette.Indigo, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

internal data class ErpKpiItem(
    val label: String,
    val value: String,
)

internal data class ErpActionItem(
    val title: String,
    val icon: ImageVector,
    val soft: Color,
    val accent: Color,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
internal fun ErpDashboardHero(
    eyebrow: String,
    value: String,
    caption: String,
    metrics: List<ErpKpiItem>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(27.dp),
        colors = CardDefaults.cardColors(containerColor = ErpPalette.Teal),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .78f))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .8f))
            if (metrics.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.take(3).forEach { metric ->
                        HeroMetric(metric.label, metric.value, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ErpQuickActionsGrid(
    actions: List<ErpActionItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        actions.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowItems.forEach { action ->
                    ErpQuickActionTile(
                        title = action.title,
                        icon = action.icon,
                        soft = action.soft,
                        accent = action.accent,
                        enabled = action.enabled,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
