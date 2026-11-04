package com.resbox.player.media.kernel

import androidx.compose.runtime.Immutable

enum class PlayerKernelType(val displayName: String) {
    MEDIA3_EXO("Media3 ExoPlayer (硬解 + FFmpeg 音频)"),
    IJK_SOFT_EXT("IJKPlayer 软解扩展内核"),
    MPV_HDR_EXT("MPV 次世代 HDR 渲染内核")
}

enum class VideoAspectMode(val label: String) {
    FIT("适应屏幕"),
    FILL_CROP("裁剪铺满"),
    STRETCH_16_9("拉伸 16:9"),
    ORIGINAL("原始比例")
}

@Immutable
data class TrackItemInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

/**
 * Unified abstraction for ResBox media kernels (Requirement 2: Media3 primary + IJKPlayer/MPV extension interface).
 */
interface MediaKernelBridge {
    val kernelType: PlayerKernelType
    fun setPlaybackSpeed(speed: Float)
    fun seekTo(positionMs: Long)
    fun releaseKernel()
}
