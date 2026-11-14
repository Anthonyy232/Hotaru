package com.anthonyla.hotaru.ui.kit

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.meta.spatial.uiset.theme.darkSpatialColorScheme
import com.meta.spatial.uiset.theme.interFontFamily
import com.meta.spatial.uiset.theme.SpatialShapeSizes
import com.meta.spatial.uiset.theme.SpatialTheme
import com.meta.spatial.uiset.theme.SpatialTypography

/**
 * visionOS's palette on glass: white labels at three strengths, controls lighter than the glass under them, and fields and
 * tracks darker than it. Color is kept for meaning: [Glow], the firefly light of the app's name, on what is on, chosen, playing
 * or watched, and [Red] on favorites, errors and what destroys.
 */
object HotaruColors {
    val Text = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.6f)
    val TextTertiary = Color.White.copy(alpha = 0.3f)
    val Fill = Color.White.copy(alpha = 0.1f)
    val FillSelected = Color.White.copy(alpha = 0.24f)
    val Recess = Color.Black.copy(alpha = 0.25f)
    val Separator = Color.White.copy(alpha = 0.12f)
    val Scrim = Color.Black.copy(alpha = 0.6f)
    val Glow = Color(0xFFFFBE7A)
    val Red = Color(0xFFFF453A)
}

object HotaruDimens {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val gutter: Dp = 32.dp
    val section: Dp = 48.dp

    val icon: Dp = 24.dp
    /** A row or tab that takes input. */
    val hitTarget: Dp = 48.dp
    val hitTargetPrimary: Dp = 60.dp
    /** Between adjacent controls, as visionOS and Meta both ask; it also keeps their touch slop apart. */
    val targetGap: Dp = 16.dp
    val readingWidth: Dp = 720.dp
}

/**
 * Corners from the UI set's scale, nested concentrically as visionOS nests them: an outer radius is the inner one plus the
 * padding between them, so a group is its rows plus 8 dp and a pane is a group plus 12.
 */
object HotaruShapes {
    val tag: Shape = RoundedCornerShape(SpatialShapeSizes.twoXS)
    val row: Shape = RoundedCornerShape(SpatialShapeSizes.small)
    val card: Shape = RoundedCornerShape(SpatialShapeSizes.medium)
    val group: Shape = RoundedCornerShape(SpatialShapeSizes.large)
    val pane: Shape = RoundedCornerShape(SpatialShapeSizes.twoXL)
    val window: Shape = RoundedCornerShape(SpatialShapeSizes.threeXL)
    val pill: Shape = CircleShape
}

object HotaruMotion {
    const val quickMs = 200
    const val standardMs = 350
    const val liftScale = 1.04f
}

private fun type(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = interFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    // Inter runs wider than SF Pro at title sizes.
    letterSpacing = if (size >= 20) (-0.02).em else 0.em,
)

/**
 * visionOS's type ramp at this window's scale, where its 17 pt body is 14 sp, in its weights: bold titles and medium body,
 * which stay legible on glass. The slots are Large Title, Title 1, Title 3, Body and Footnote.
 */
private val Typography = SpatialTypography(
    headline1Strong = type(26, 32, FontWeight.Bold),
    headline1 = type(26, 32, FontWeight.Medium),
    headline2Strong = type(20, 26, FontWeight.Bold),
    headline2 = type(20, 26, FontWeight.Medium),
    headline3Strong = type(16, 22, FontWeight.Bold),
    headline3 = type(16, 22, FontWeight.Medium),
    body1Strong = type(14, 20, FontWeight.Bold),
    body1 = type(14, 20, FontWeight.Medium),
    body2Strong = type(12, 16, FontWeight.Bold),
    body2 = type(12, 16, FontWeight.Medium),
)

/** Extra Large Title: hero titles and Quick Connect's digits. */
val DisplayStyle = type(40, 46, FontWeight.Bold)

private val Colors = darkSpatialColorScheme().copy(
    primaryButton = HotaruColors.Text,
    secondaryButton = HotaruColors.Fill,
    negativeButton = HotaruColors.Red,
    // The search bar's field.
    sideNavBackground = HotaruColors.Recess,
    hover = Color.White.copy(alpha = 0.08f),
    pressed = Color.White.copy(alpha = 0.16f),
    active = HotaruColors.Glow,
    positive = HotaruColors.Glow,
    negative = HotaruColors.Red,
    progressBarOnMedia = HotaruColors.FillSelected,
    progressOnMedia = HotaruColors.Glow,
)

/**
 * The UI set's components in visionOS's colors and type, with Material text defaulting to them. Material goes outside: it
 * provides a ripple indication, and the UI set's hover highlight must win.
 */
@Composable
fun HotaruTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = HotaruColors.Text)) {
        SpatialTheme(colorScheme = Colors, typography = Typography) {
            CompositionLocalProvider(LocalContentColor provides HotaruColors.Text, LocalTextStyle provides SpatialTheme.typography.body1, content = content)
        }
    }
}
