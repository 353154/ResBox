package com.resbox.player.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.resbox.player.data.model.VodInfo
import com.resbox.player.ui.theme.ResColors
import com.resbox.player.ui.theme.resCardSurface

@Composable
fun ResPillBadge(
    text: String,
    backgroundColor: Color = ResColors.PrimaryViolet.copy(alpha = 0.88f),
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * Poster card with:
 * - RoundedCornerShape(20.dp) + Specular border
 * - TV D-Pad Focus Scale `1.0f -> 1.08f` & Neon Focus Ring
 * - SharedElementTransition support from Home/Search grid to DetailScreen
 * - Pill badges for 4K/HDR quality, Douban score, and source site name
 */
@Composable
fun VodPosterCard(
    vod: VodInfo,
    onClick: () -> Unit,
    onFocusedPosterChanged: ((String) -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(20.dp)
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "poster_${vod.siteKey}_${vod.vodId}"),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .then(sharedModifier)
            .resCardSurface(
                shape = cardShape,
                onFocusAccentChanged = { focused ->
                    if (focused && vod.displayPoster.isNotBlank()) {
                        onFocusedPosterChanged?.invoke(vod.displayPoster)
                    }
                }
            )
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = vod.displayPoster,
            contentDescription = vod.vodName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom dark gradient legibility scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x33080A12),
                            Color(0xEE080A12)
                        ),
                        startY = 120f
                    )
                )
        )

        // Top Row Badges: Quality Pill (Left) + Douban Score Pill (Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val is4K = vod.qualityBadge.contains("4K", ignoreCase = true)
            ResPillBadge(
                text = vod.qualityBadge,
                backgroundColor = if (is4K) ResColors.Gold4KBadge else ResColors.PrimaryViolet.copy(alpha = 0.85f),
                contentColor = if (is4K) Color(0xFF10121A) else Color.White
            )

            if (vod.vodScore.isNotBlank()) {
                ResPillBadge(
                    text = "★ ${vod.vodScore}",
                    backgroundColor = ResColors.EmeraldScoreBadge.copy(alpha = 0.90f),
                    contentColor = Color(0xFF0A1214)
                )
            }
        }

        // Bottom Title & Source Line Tag
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (vod.siteName.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResPillBadge(
                        text = vod.siteName,
                        backgroundColor = Color(0xAA1E2942),
                        contentColor = ResColors.FocusCyan
                    )
                    if (vod.latencyMs > 0L) {
                        ResPillBadge(
                            text = "${vod.latencyMs}ms",
                            backgroundColor = Color(0xAA102A26),
                            contentColor = ResColors.EmeraldScoreBadge
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(
                text = vod.vodName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (vod.vodRemarks.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = vod.vodRemarks,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ResColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Translucent Frosted Glass container used for top bars, floating bottom navigation capsules, and TV drawers.
 */
@Composable
fun FrostedGlassBar(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(ResColors.FrostedGlassSurface)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
    ) {
        content()
    }
}
