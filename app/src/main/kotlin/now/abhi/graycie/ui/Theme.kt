package now.abhi.graycie.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import now.abhi.graycie.R

val GraycieBackground = Color(0xFF12100E)
val GraycieSurface = Color(0xFF1B1815)
val GraycieSurfaceRaised = Color(0xFF25211D)
val GraycieText = Color(0xFFF5EFE7)
val GraycieMuted = Color(0xFFB9B0A6)
val GraycieBorder = Color(0xFF403832)
val GraycieAmber = Color(0xFFE3A85B)
val GraycieAmberSoft = Color(0xFFFFD8A8)
val GraycieError = Color(0xFFFFB4AB)
val GraycieDisabled = Color(0xFF746C64)

val Atkinson = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.SemiBold),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)

val ChatFavour = FontFamily(
    Font(R.font.chat_favour_regular, FontWeight.Normal),
)

private val GraycieColors: ColorScheme = darkColorScheme(
    primary = GraycieAmber,
    onPrimary = GraycieBackground,
    primaryContainer = Color(0xFF4B3521),
    onPrimaryContainer = GraycieAmberSoft,
    secondary = GraycieAmberSoft,
    onSecondary = GraycieBackground,
    background = GraycieBackground,
    onBackground = GraycieText,
    surface = GraycieSurface,
    onSurface = GraycieText,
    surfaceVariant = GraycieSurfaceRaised,
    onSurfaceVariant = GraycieMuted,
    outline = GraycieBorder,
    outlineVariant = GraycieBorder,
    error = GraycieError,
    onError = GraycieBackground,
)

private val GraycieTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = 43.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.1).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.45).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(fontFamily = Atkinson, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Atkinson, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Atkinson, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.1.sp,
    ),
)

@Composable
fun GraycieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GraycieColors,
        typography = GraycieTypography,
        content = content,
    )
}
