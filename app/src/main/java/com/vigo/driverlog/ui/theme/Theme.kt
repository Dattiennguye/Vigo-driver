package com.vigo.driverlog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vigo.driverlog.domain.EventType

object VigoColors {
    val Navy = Color(0xFF142B45)
    val Blue = Color(0xFF2878D0)
    val Green = Color(0xFF1F9D68)
    val Orange = Color(0xFFE99A35)
    val Background = Color(0xFFF4F6F9)
    val White = Color(0xFFFFFFFF)
    val Text = Color(0xFF182334)
    val Muted = Color(0xFF5B677A)
    val Line = Color(0xFFDDE3EA)
    val Red = Color(0xFFC63D3D)
    val Purple = Color(0xFF7A5AC8)
    val Teal = Color(0xFF2A9AA8)

    fun forType(t: EventType): Color = when (t) {
        EventType.START_TRIP -> Green
        EventType.ARRIVE -> Blue
        EventType.PARK_START, EventType.PARK_END -> Orange
        EventType.RESUME -> Teal
        EventType.END_TRIP -> Red
        EventType.QUICK_LOG -> Purple
    }
}

private val scheme = lightColorScheme(
    primary = VigoColors.Blue,
    onPrimary = VigoColors.White,
    secondary = VigoColors.Green,
    onSecondary = VigoColors.White,
    tertiary = VigoColors.Orange,
    background = VigoColors.Background,
    onBackground = VigoColors.Text,
    surface = VigoColors.White,
    onSurface = VigoColors.Text,
    surfaceVariant = Color(0xFFE9EDF2),
    onSurfaceVariant = VigoColors.Muted,
    error = VigoColors.Red,
    primaryContainer = Color(0xFFDCEAFA),
    onPrimaryContainer = VigoColors.Navy,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VigoColors.Text),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun VigoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
