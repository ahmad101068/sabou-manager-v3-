package ir.restaurant.management.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat

/**
 * سیستم بصری مرکزی: مدیریتی، آرام، حرفه‌ای و سازگار با Light/Dark و RTL.
 * رنگ اصلی سبز تیره برای اعتماد، کهربایی برای اقدام، و سطوح خنثی برای تمرکز روی داده‌ها.
 */
object ManagementBrand {
    val Evergreen = Color(0xFF075B4B)
    val EvergreenDark = Color(0xFF032F29)
    val Emerald = Color(0xFF00856F)
    val Gold = Color(0xFFD9A62E)
    val GoldBright = Color(0xFFF2C75C)
    val Amber = Gold
    val AmberSoft = Color(0xFFFFE9B0)
    val Ink = Color(0xFF101917)
    val Canvas = Color(0xFFF4F6F2)
    val Paper = Color(0xFFFFFFFF)
    val Mist = Color(0xFFE5ECE8)
    val Slate = Color(0xFF52615D)
    val Night = Color(0xFF071310)
}

private val ManagementLightColors = lightColorScheme(
    primary = ManagementBrand.Evergreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE4),
    onPrimaryContainer = ManagementBrand.EvergreenDark,
    secondary = Color(0xFF8A6200),
    onSecondary = Color.White,
    secondaryContainer = ManagementBrand.AmberSoft,
    onSecondaryContainer = Color(0xFF392400),
    tertiary = Color(0xFF8A3F18),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC9),
    onTertiaryContainer = Color(0xFF351000),
    background = ManagementBrand.Canvas,
    onBackground = ManagementBrand.Ink,
    surface = ManagementBrand.Paper,
    onSurface = ManagementBrand.Ink,
    surfaceVariant = ManagementBrand.Mist,
    onSurfaceVariant = ManagementBrand.Slate,
    outline = Color(0xFF83908B),
    outlineVariant = Color(0xFFD9E0DC),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD6),
)

private val ManagementDarkColors = darkColorScheme(
    primary = Color(0xFF79DBC1),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF185044),
    onPrimaryContainer = Color(0xFFBCEEDF),
    secondary = Color(0xFFFFC96B),
    onSecondary = Color(0xFF472A00),
    secondaryContainer = Color(0xFF654000),
    onSecondaryContainer = Color(0xFFFFDEA1),
    tertiary = Color(0xFFA8CAEF),
    onTertiary = Color(0xFF0A355C),
    tertiaryContainer = Color(0xFF294C70),
    onTertiaryContainer = Color(0xFFD3E5FF),
    background = ManagementBrand.Night,
    onBackground = Color(0xFFE1E7E3),
    surface = Color(0xFF101E1A),
    onSurface = Color(0xFFE1E7E3),
    surfaceVariant = Color(0xFF3E4945),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF89948F),
    outlineVariant = Color(0xFF3E4945),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

private val ManagementTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val ManagementShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun RestaurantManagementTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScaleMultiplier: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) ManagementDarkColors else ManagementLightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            // Older Android versions still paint an opaque status bar; match it to the
            // shared dark header so its light icons stay readable there as well.
            window.statusBarColor = ManagementBrand.EvergreenDark.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = colors.surface.toArgb()
            }
            WindowInsetsControllerCompat(window, view).apply {
                // All primary screens draw a dark brand header behind the status bar.
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * fontScaleMultiplier.coerceIn(0.9f, 1.4f)),
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = ManagementTypography,
            shapes = ManagementShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
