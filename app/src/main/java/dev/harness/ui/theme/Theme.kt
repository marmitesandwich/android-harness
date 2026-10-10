package dev.harness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.harness.R

/**
 * Colours that carry meaning in the trace: READ vs WRITE vs failure.
 * Kept out of the Material scheme because they are not generic roles.
 */
@Immutable
class HarnessColors(
    val read: Color,
    val write: Color,
    val danger: Color,
    val subdued: Color,
    val surface2: Color,
    val rule: Color,
)

private val LightHarness = HarnessColors(
    read = Color(0xFF1F8A70),
    write = Color(0xFFC9480A),
    danger = Color(0xFFC62F3B),
    subdued = Color(0xFF5B6773),
    surface2 = Color(0xFFDCE2E7),
    rule = Color(0xFFB5BFC8),
)

private val DarkHarness = HarnessColors(
    read = Color(0xFF4CC9A4),
    write = Color(0xFFFF8A4C),
    danger = Color(0xFFFF6B75),
    subdued = Color(0xFF98A6B3),
    surface2 = Color(0xFF1A2733),
    rule = Color(0xFF34475A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F5BFF),
    onPrimary = Color.White,
    background = Color(0xFFE8ECEF),
    onBackground = Color(0xFF1C2530),
    surface = Color(0xFFE8ECEF),
    onSurface = Color(0xFF1C2530),
    surfaceVariant = Color(0xFFDCE2E7),
    onSurfaceVariant = Color(0xFF5B6773),
    outline = Color(0xFFB5BFC8),
    error = Color(0xFFC62F3B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7C9BFF),
    onPrimary = Color(0xFF0E1720),
    background = Color(0xFF0E1720),
    onBackground = Color(0xFFE6ECF1),
    surface = Color(0xFF0E1720),
    onSurface = Color(0xFFE6ECF1),
    surfaceVariant = Color(0xFF1A2733),
    onSurfaceVariant = Color(0xFF98A6B3),
    outline = Color(0xFF34475A),
    error = Color(0xFFFF6B75),
)

// Both fonts are variable; pick the weight (and a narrower width for the mono) per use.
@OptIn(ExperimentalTextApi::class)
private fun sans(weight: FontWeight) = Font(
    R.font.instrumentsans,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun mono(weight: FontWeight) = Font(
    R.font.martianmono,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.width(87.5f)),
)

val SansFamily = FontFamily(sans(FontWeight.Normal), sans(FontWeight.Medium), sans(FontWeight.SemiBold))
val MonoFamily = FontFamily(mono(FontWeight.Normal), mono(FontWeight.Medium), mono(FontWeight.SemiBold))

private fun Typography.withSans(): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = SansFamily),
    displayMedium = displayMedium.copy(fontFamily = SansFamily),
    displaySmall = displaySmall.copy(fontFamily = SansFamily),
    headlineLarge = headlineLarge.copy(fontFamily = SansFamily),
    headlineMedium = headlineMedium.copy(fontFamily = SansFamily),
    headlineSmall = headlineSmall.copy(fontFamily = SansFamily),
    titleLarge = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = titleSmall.copy(fontFamily = SansFamily),
    bodyLarge = TextStyle(fontFamily = SansFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = SansFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = SansFamily, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = labelLarge.copy(fontFamily = SansFamily),
    labelMedium = labelMedium.copy(fontFamily = SansFamily),
    labelSmall = labelSmall.copy(fontFamily = SansFamily),
)

private val LocalHarnessColors = staticCompositionLocalOf<HarnessColors> { error("HarnessTheme missing") }

object Harness {
    val colors: HarnessColors
        @Composable @ReadOnlyComposable get() = LocalHarnessColors.current
}

@Composable
fun HarnessTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHarnessColors provides if (dark) DarkHarness else LightHarness) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = Typography().withSans(),
            content = content,
        )
    }
}
