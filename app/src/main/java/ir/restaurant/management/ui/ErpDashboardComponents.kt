package ir.restaurant.management.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object ErpPalette {
    val Teal = Color(0xFF0F766E)
    val TealDark = Color(0xFF115E59)
    val TealLight = Color(0xFFCCFBF1)
    val TealSoft = Color(0xFFE6F7F5)
    val Indigo = Color(0xFF5B5BD6)
    val IndigoSoft = Color(0xFFEDEDFC)
    val Canvas = Color(0xFFF7F9FA)
    val Ink = Color(0xFF17202A)
    val Muted = Color(0xFF6B7280)
    val Border = Color(0xFFE7EAEE)
    val Green = Color(0xFF2E8B6D)
    val GreenSoft = Color(0xFFE8F6F1)
    val Amber = Color(0xFFD58A17)
    val AmberSoft = Color(0xFFFFF4DD)
    val Red = Color(0xFFD84D4D)
    val RedSoft = Color(0xFFFFEEEE)
    val Blue = Color(0xFF255A9B)
    val BlueSoft = Color(0xFFEAF3FF)
    val Purple = Color(0xFF7B61B3)
    val PurpleSoft = Color(0xFFF2ECFF)
}

@Composable
internal fun ErpSectionTitle(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = ErpPalette.Ink,
        )
        if (action != null && onAction != null) {
            Surface(onClick = onAction, color = Color.Transparent) {
                Text(
                    text = action,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = ErpPalette.Indigo,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun ManagementGeometricLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .background(ErpPalette.IndigoSoft, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 3.1f, cap = StrokeCap.Round)
            drawLine(ErpPalette.Indigo, Offset(w * .22f, h * .25f), Offset(w * .78f, h * .25f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(ErpPalette.Teal, Offset(w * .78f, h * .25f), Offset(w * .64f, h * .78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(ErpPalette.Indigo, Offset(w * .64f, h * .78f), Offset(w * .28f, h * .78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(ErpPalette.Teal, Offset(w * .28f, h * .78f), Offset(w * .22f, h * .25f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawCircle(ErpPalette.TealDark, radius = w * .08f, center = Offset(w * .50f, h * .52f))
        }
    }
}

@Composable
internal fun PastelIcon(
    icon: ImageVector,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .background(background, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
internal fun TinyTrendChart(
    direction: TrendDirection,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val points = when (direction) {
            TrendDirection.UP -> listOf(.72f, .28f)
            TrendDirection.DOWN -> listOf(.28f, .72f)
            TrendDirection.SAME -> listOf(.50f, .50f)
            TrendDirection.NOT_AVAILABLE -> emptyList()
        }
        if (points.size < 2) return@Canvas
        val path = Path()
        val stepX = size.width / (points.size - 1)
        points.forEachIndexed { index, yFactor ->
            val point = Offset(index * stepX, size.height * yFactor)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, lineColor.copy(alpha = .96f), style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawCircle(lineColor, radius = 5f, center = Offset(size.width, size.height * points.last()))
    }
}

@Composable
internal fun ErpMetricCard(
    title: String,
    value: String,
    change: String,
    direction: TrendDirection,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, ErpPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = ErpPalette.Muted, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = ErpPalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                change,
                style = MaterialTheme.typography.labelSmall,
                color = when (direction) {
                    TrendDirection.UP -> ErpPalette.Green
                    TrendDirection.DOWN -> ErpPalette.Red
                    TrendDirection.SAME, TrendDirection.NOT_AVAILABLE -> ErpPalette.Muted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ErpBottomNavigation(
    selected: AppScreen,
    onNavigate: (AppScreen) -> Unit,
) {
    if (currentErpWindowClass() != ErpWindowClass.COMPACT) return
    val selectedTopLevel = selected.topLevelDestination()
    NavigationBar(
        modifier = Modifier.testTag("main_bottom_navigation"),
        containerColor = Color.White,
        tonalElevation = 0.dp,
    ) {
        val destinations = listOf(
            Triple(AppScreen.DASHBOARD, "خانه", Icons.Outlined.Home),
            Triple(AppScreen.CONTROL_HUB, "کنترل", Icons.Outlined.Assessment),
            Triple(AppScreen.OPERATIONS_HUB, "عملیات", Icons.Outlined.Inventory2),
            Triple(AppScreen.FINANCE_HUB, "مالی", Icons.Outlined.AccountBalanceWallet),
            Triple(AppScreen.MORE, "بیشتر", Icons.Outlined.MoreHoriz),
        )
        destinations.forEach { (destination, label, icon) ->
            val isSelected = selectedTopLevel == destination
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp)) },
                label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                modifier = Modifier.testTag("nav_${destination.name.lowercase()}"),
                colors = NavigationBarItemDefaults.colors(
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

internal fun toPersianDigits(value: String): String {
    val latin = "0123456789"
    val persian = "۰۱۲۳۴۵۶۷۸۹"
    return buildString(value.length) {
        value.forEach { ch ->
            val index = latin.indexOf(ch)
            append(if (index >= 0) persian[index] else ch)
        }
    }
}

@Composable
internal fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .76f), textAlign = TextAlign.Center)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
