package com.resbox.player.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ResColors {
    val ObsidianSurface = Color(0xFF0B0E17)
    val ElevatedCardSurface = Color(0xFF151B2D)
    val FrostedGlassSurface = Color(0xCC161C2E)
    val PrimaryViolet = Color(0xFF7C5CFF)
    val SecondaryIndigo = Color(0xFF536DFE)
    val FocusCyan = Color(0xFF00E5FF)
    val Gold4KBadge = Color(0xFFFFB300)
    val EmeraldScoreBadge = Color(0xFF00E676)
    val CoralAlert = Color(0xFFFF5252)
    val TextPrimary = Color(0xFFF5F7FF)
    val TextSecondary = Color(0xFFA5B2D6)
    val SpecularTop = Color(0x45FFFFFF)
    val SpecularBottom = Color(0x0AFFFFFF)
}

@Immutable
data class AmbientPaletteColors(
    val dominant: Color = Color(0xFF281C4D),
    val vibrant: Color = Color(0xFF14425A),
    val muted: Color = Color(0xFF0B0E17)
)

val LocalIsTvDevice = staticCompositionLocalOf { false }

private val ResDarkColorScheme = darkColorScheme(
    primary = ResColors.PrimaryViolet,
    onPrimary = Color.White,
    secondary = ResColors.FocusCyan,
    onSecondary = Color.Black,
    tertiary = ResColors.Gold4KBadge,
    background = ResColors.ObsidianSurface,
    surface = ResColors.ElevatedCardSurface,
    onBackground = ResColors.TextPrimary,
    onSurface = ResColors.TextPrimary,
    surfaceVariant = Color(0xFF1E263D),
    onSurfaceVariant = ResColors.TextSecondary
)

private val ResTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

/**
 * Detects whether the app is running on an Android TV / Leanback device or wide landscape screen.
 */
fun isRunningOnTv(context: Context, configuration: Configuration): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isLeanback = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature("android.software.leanback")
    val isWideScreenNoTouch = configuration.screenWidthDp >= 840 &&
        !context.packageManager.hasSystemFeature("android.hardware.touchscreen")
    return isLeanback || isWideScreenNoTouch
}

@Composable
fun ResBoxTheme(
    enableDynamicColor: Boolean = true,
    forceTvMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTv = remember(configuration, forceTvMode) {
        forceTvMode || isRunningOnTv(context, configuration)
    }

    val colorScheme = if (enableDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isTv) {
        dynamicDarkColorScheme(context).copy(
            background = ResColors.ObsidianSurface
        )
    } else {
        ResDarkColorScheme
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalIsTvDevice provides isTv) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ResTypography,
            content = content
        )
    }
}

/**
 * Extracts dominant & vibrant colors from any remote poster URL using AndroidX `Palette` API
 * to drive the immersive Mesh Gradient ambient background (Requirement 3.1).
 */
@Composable
fun rememberPosterAmbientPalette(
    imageUrl: String,
    defaultPalette: AmbientPaletteColors = AmbientPaletteColors()
): AmbientPaletteColors {
    val context = LocalContext.current
    var extracted by remember(imageUrl) { mutableStateOf(defaultPalette) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(160, 240)
                    .build()
                val result = loader.execute(request)
                val bitmap: Bitmap? = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).maximumColorCount(12).generate()
                    val domInt = palette.getDarkVibrantColor(
                        palette.getDominantColor(0xFF281C4D.toInt())
                    )
                    val vibInt = palette.getVibrantColor(
                        palette.getMutedColor(0xFF14425A.toInt())
                    )
                    extracted = AmbientPaletteColors(
                        dominant = Color(domInt).copy(alpha = 0.55f),
                        vibrant = Color(vibInt).copy(alpha = 0.42f),
                        muted = ResColors.ObsidianSurface
                    )
                }
            } catch (_: Exception) {
                // Keep default ambient palette
            }
        }
    }

    val animDom by animateColorAsState(
        targetValue = extracted.dominant,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "AmbientDominant"
    )
    val animVib by animateColorAsState(
        targetValue = extracted.vibrant,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "AmbientVibrant"
    )
    return AmbientPaletteColors(dominant = animDom, vibrant = animVib, muted = ResColors.ObsidianSurface)
}

/**
 * Renders an immersive Mesh Gradient fluid canvas driven by `AmbientPaletteColors`.
 */
@Composable
fun MeshGradientBackground(
    palette: AmbientPaletteColors,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ResColors.ObsidianSurface)
            .drawBehind {
                drawRect(color = ResColors.ObsidianSurface)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.dominant, Color.Transparent),
                        center = Offset(size.width * 0.22f, size.height * 0.18f),
                        radius = size.maxDimension * 0.68f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.vibrant, Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.28f),
                        radius = size.maxDimension * 0.58f
                    )
                )
            }
    ) {
        content()
    }
}

/**
 * Specular Glass Border + D-Pad TV Focus Scale (`1.0f -> 1.08f`) + Neon Halo Ring modifier (Requirement 3.1 & 3.2).
 */
fun Modifier.resCardSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    onFocusAccentChanged: ((Boolean) -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "TvFocusScale"
    )

    val borderBrush = if (isFocused) {
        Brush.linearGradient(listOf(ResColors.FocusCyan, ResColors.PrimaryViolet))
    } else {
        Brush.linearGradient(listOf(ResColors.SpecularTop, ResColors.SpecularBottom))
    }
    val borderWidth: Dp = if (isFocused) 2.5.dp else 1.dp
    val shadowElevation: Dp = if (isFocused) 18.dp else 4.dp

    this
        .scale(scale)
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = if (isFocused) ResColors.FocusCyan else Color.Black,
            spotColor = if (isFocused) ResColors.FocusCyan else Color.Black
        )
        .clip(shape)
        .background(ResColors.ElevatedCardSurface, shape)
        .border(borderWidth, borderBrush, shape)
        .onFocusChanged { state ->
            if (isFocused != state.isFocused) {
                isFocused = state.isFocused
                onFocusAccentChanged?.invoke(state.isFocused)
            }
        }
        .focusable()
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyUp &&
                (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
            ) {
                onClick?.invoke()
                true
            } else {
                false
            }
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )
}

/**
 * TV Item Interactive surface modifier with instant key response, dynamic focus cyan halo and selected tone.
 */
fun Modifier.tvInteractiveItem(
    shape: Shape = RoundedCornerShape(12.dp),
    isSelected: Boolean = false,
    scaleOnFocus: Float = 1.05f,
    onClick: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1.0f,
        label = "TvItemScale"
    )

    this
        .scale(scale)
        .onFocusChanged { state ->
            if (isFocused != state.isFocused) {
                isFocused = state.isFocused
                onFocusChanged?.invoke(state.isFocused)
            }
        }
        .border(
            width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
            color = if (isFocused) ResColors.FocusCyan
                    else if (isSelected) ResColors.PrimaryViolet.copy(alpha = 0.85f)
                    else Color.White.copy(alpha = 0.12f),
            shape = shape
        )
        .clip(shape)
        .background(
            color = when {
                isFocused -> ResColors.PrimaryViolet.copy(alpha = 0.6f)
                isSelected -> ResColors.PrimaryViolet.copy(alpha = 0.35f)
                else -> Color(0x77161E32)
            },
            shape = shape
        )
        .focusable()
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyUp &&
                (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
            ) {
                onClick?.invoke()
                true
            } else {
                false
            }
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )
}

/**
 * Shimmer Skeleton placeholder effect (Requirement 3.1: No spinning progress indicators for lists).
 */
@Composable
fun ResShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    val transition = rememberInfiniteTransition(label = "ResShimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ResShimmerOffset"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF161D31),
            Color(0xFF283354),
            Color(0xFF161D31)
        ),
        start = Offset(translateAnim, translateAnim * 0.4f),
        end = Offset(translateAnim + 360f, (translateAnim + 360f) * 0.4f)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(ResColors.SpecularTop, ResColors.SpecularBottom)),
                shape = shape
            )
    )
}
