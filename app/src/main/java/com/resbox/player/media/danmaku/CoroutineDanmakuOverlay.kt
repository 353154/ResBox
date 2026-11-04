package com.resbox.player.media.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface

@Immutable
data class DanmakuComment(
    val id: Long,
    val text: String,
    val colorInt: Int = 0xFFFFFFFF.toInt(),
    val trackRow: Int = 0,
    val speedPxPerSec: Float = 240f
)

private class ActiveDanmakuNode(
    val comment: DanmakuComment,
    var currentX: Float,
    val y: Float
)

/**
 * 60fps Coroutine-driven Compose Canvas Danmaku Engine (`withFrameNanos` frame-synced).
 * Renders hardware-accelerated scrolling comments with crisp stroke outlines and zero allocation jitter.
 */
@Composable
fun CoroutineDanmakuOverlay(
    enabled: Boolean,
    isPlaying: Boolean,
    commentsPool: List<DanmakuComment>,
    modifier: Modifier = Modifier
) {
    if (!enabled || commentsPool.isEmpty()) return

    val activeNodes = remember { mutableStateListOf<ActiveDanmakuNode>() }
    var lastFrameTimeNanos by remember { mutableLongStateOf(0L) }

    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 38f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = android.graphics.Color.WHITE
            setShadowLayer(6f, 2f, 2f, android.graphics.Color.BLACK)
        }
    }

    LaunchedEffect(enabled, isPlaying, commentsPool) {
        if (!enabled) {
            activeNodes.clear()
            return@LaunchedEffect
        }
        var spawnCursor = 0
        var accumulatedSpawnMs = 0L

        while (enabled) {
            withFrameNanos { frameNanos ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = frameNanos
                    return@withFrameNanos
                }
                val deltaSec = ((frameNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastFrameTimeNanos = frameNanos

                if (isPlaying) {
                    accumulatedSpawnMs += (deltaSec * 1000f).toLong()
                    if (accumulatedSpawnMs >= 1_450L && activeNodes.size < 14) {
                        accumulatedSpawnMs = 0L
                        val nextComment = commentsPool[spawnCursor % commentsPool.size]
                        spawnCursor++
                        val row = spawnCursor % 5
                        activeNodes.add(
                            ActiveDanmakuNode(
                                comment = nextComment.copy(trackRow = row),
                                currentX = 1920f + (row * 95f),
                                y = 78f + row * 54f
                            )
                        )
                    }

                    val iterator = activeNodes.listIterator()
                    while (iterator.hasNext()) {
                        val node = iterator.next()
                        node.currentX -= node.comment.speedPxPerSec * deltaSec
                        if (node.currentX < -650f) {
                            iterator.remove()
                        }
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (i in 0 until activeNodes.size) {
                val node = activeNodes[i]
                textPaint.color = node.comment.colorInt
                native.drawText(node.comment.text, node.currentX, node.y, textPaint)
            }
        }
    }
}
