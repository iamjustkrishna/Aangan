package app.familyvault.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyvault.resources.Res
import app.familyvault.resources.figtree_400
import app.familyvault.resources.figtree_500
import app.familyvault.resources.figtree_600
import app.familyvault.resources.figtree_700
import org.jetbrains.compose.resources.Font

/** Colour tokens from design/tokens.json. Names match the token names. */
@Immutable
data class FamilyVaultColors(
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val ink: Color,
    val inkMuted: Color,
    val line: Color,
    val lineStrong: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryTint: Color,
    val record: Color,
    val onRecord: Color,
    val success: Color,
    val successTint: Color,
    val waiting: Color,
    val waitingTint: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerTint: Color,
    val tones: List<Color>,
    val onTone: Color,
    val thumbs: List<Color>,
    val thumbHill: Color,
    val scrim: Color,
    val onScrim: Color,
    val camera: Color,
)

private val tones = listOf(Color(0xFFFFD27A), Color(0xFF9EE0D2), Color(0xFFFFB3A7), Color(0xFFB9CDFA), Color(0xFFCFE59A), Color(0xFFE9C9AD))
private val thumbs = listOf(Color(0xFF3B6E8F), Color(0xFFB0703C), Color(0xFF7D5A86), Color(0xFF4F7D48))

val LightTokens = FamilyVaultColors(
    surface = Color(0xFFFFFFFF), surfaceRaised = Color(0xFFFFFFFF), surfaceSunken = Color(0xFFF1F3F5),
    ink = Color(0xFF16191C), inkMuted = Color(0xFF5C6670), line = Color(0xFFE4E7EA), lineStrong = Color(0xFF8A949D),
    primary = Color(0xFF2457D6), onPrimary = Color(0xFFFFFFFF), primaryTint = Color(0xFFE8EFFD),
    record = Color(0xFFE5322D), onRecord = Color(0xFFFFFFFF),
    success = Color(0xFF1E7A46), successTint = Color(0xFFE6F4EC),
    waiting = Color(0xFF8A5A00), waitingTint = Color(0xFFFDF1D8),
    danger = Color(0xFFC0261C), onDanger = Color(0xFFFFFFFF), dangerTint = Color(0xFFFDE7E5),
    tones = tones, onTone = Color(0xFF16191C), thumbs = thumbs, thumbHill = Color(0x24FFFFFF),
    scrim = Color(0xB8000000), onScrim = Color(0xFFFFFFFF), camera = Color(0xFF0B0C0D),
)

val DarkTokens = FamilyVaultColors(
    surface = Color(0xFF121416), surfaceRaised = Color(0xFF1C1F22), surfaceSunken = Color(0xFF262A2E),
    ink = Color(0xFFECEFF1), inkMuted = Color(0xFFA3ADB6), line = Color(0xFF2C3136), lineStrong = Color(0xFF6B757E),
    primary = Color(0xFF8AB0FF), onPrimary = Color(0xFF0F1B33), primaryTint = Color(0xFF1B2A4A),
    record = Color(0xFFFF6B61), onRecord = Color(0xFF121416),
    success = Color(0xFF5CC48A), successTint = Color(0xFF16301F),
    waiting = Color(0xFFF5B336), waitingTint = Color(0xFF3A2E12),
    danger = Color(0xFFFF8A7E), onDanger = Color(0xFF121416), dangerTint = Color(0xFF3A1A17),
    tones = tones, onTone = Color(0xFF16191C), thumbs = thumbs, thumbHill = Color(0x1AFFFFFF),
    scrim = Color(0xB8000000), onScrim = Color(0xFFFFFFFF), camera = Color(0xFF0B0C0D),
)

/** Spacing tokens space-1 … space-7. */
object Space {
    val s1 = 4.dp; val s2 = 8.dp; val s3 = 12.dp; val s4 = 16.dp; val s5 = 24.dp; val s6 = 32.dp; val s7 = 48.dp
}

/** Radius tokens. radius-pill is CircleShape / RoundedCornerShape(50). */
object Radius {
    val sm = 8.dp; val md = 12.dp; val lg = 20.dp
}

/** Size tokens. */
object Sizes {
    val touchMin = 56.dp; val touchComfy = 64.dp
    val avatarSm = 32.dp; val avatarMd = 40.dp; val avatarLg = 56.dp
    val record = 80.dp
}

val LocalFamilyVaultColors = staticCompositionLocalOf { LightTokens }

object FV {
    val colors: FamilyVaultColors
        @Composable get() = LocalFamilyVaultColors.current
}

@Composable
private fun figtree(): FontFamily = FontFamily(
    Font(Res.font.figtree_400, FontWeight.Normal),
    Font(Res.font.figtree_500, FontWeight.Medium),
    Font(Res.font.figtree_600, FontWeight.SemiBold),
    Font(Res.font.figtree_700, FontWeight.Bold),
)

@Composable
fun FamilyVaultTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkTokens else LightTokens
    val f = figtree()
    fun style(size: Int, line: Int, weight: FontWeight) =
        TextStyle(fontFamily = f, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

    // headline, title-lg, title, body-lg, body, label, caption, small
    val typography = Typography(
        headlineMedium = style(28, 34, FontWeight.Bold),
        titleLarge = style(22, 28, FontWeight.Bold),
        titleMedium = style(18, 24, FontWeight.Bold),
        bodyLarge = style(18, 26, FontWeight.Normal),
        bodyMedium = style(16, 24, FontWeight.Normal),
        labelLarge = style(16, 22, FontWeight.SemiBold),
        bodySmall = style(14, 20, FontWeight.Normal),
        labelMedium = style(14, 20, FontWeight.SemiBold),
        labelSmall = style(12, 16, FontWeight.SemiBold),
    )

    val scheme = if (dark) darkColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryTint, onPrimaryContainer = c.primary,
        background = c.surface, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surfaceSunken, onSurfaceVariant = c.inkMuted, outline = c.lineStrong, outlineVariant = c.line,
        error = c.danger, onError = c.onDanger, errorContainer = c.dangerTint,
        surfaceContainerLowest = c.surfaceRaised, surfaceContainerLow = c.surfaceRaised, surfaceContainer = c.surfaceRaised,
        surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceSunken,
    ) else lightColorScheme(
        primary = c.primary, onPrimary = c.onPrimary, primaryContainer = c.primaryTint, onPrimaryContainer = c.primary,
        background = c.surface, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        surfaceVariant = c.surfaceSunken, onSurfaceVariant = c.inkMuted, outline = c.lineStrong, outlineVariant = c.line,
        error = c.danger, onError = c.onDanger, errorContainer = c.dangerTint,
        surfaceContainerLowest = c.surfaceRaised, surfaceContainerLow = c.surfaceRaised, surfaceContainer = c.surfaceRaised,
        surfaceContainerHigh = c.surfaceRaised, surfaceContainerHighest = c.surfaceSunken,
    )

    CompositionLocalProvider(LocalFamilyVaultColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
