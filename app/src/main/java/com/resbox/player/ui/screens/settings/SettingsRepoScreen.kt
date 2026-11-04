package com.resbox.player.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.resbox.player.data.local.datastore.AppPreferences
import com.resbox.player.data.local.entity.RepoSourceEntity
import com.resbox.player.ui.components.FrostedGlassBar
import com.resbox.player.ui.components.ResPillBadge
import com.resbox.player.ui.theme.AmbientPaletteColors
import com.resbox.player.ui.theme.LocalIsTvDevice
import com.resbox.player.ui.theme.MeshGradientBackground
import com.resbox.player.ui.theme.ResColors
import com.resbox.player.ui.theme.resCardSurface

@Composable
fun SettingsRepoScreen(
    uiState: SettingsUiState,
    preferences: AppPreferences,
    savedRepos: List<RepoSourceEntity>,
    onRepoUrlChanged: (String) -> Unit,
    onRepoNameChanged: (String) -> Unit,
    onAddAndActivateRepo: () -> Unit,
    onSelectRepo: (RepoSourceEntity) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleDanmaku: (Boolean) -> Unit,
    onToggleForceTvMode: (Boolean) -> Unit,
    onTogglePosterScraping: (Boolean) -> Unit = {},
    onUpdateTmdbApiKey: (String) -> Unit = {}
) {
    val isTv = LocalIsTvDevice.current

    MeshGradientBackground(palette = AmbientPaletteColors()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = if (isTv) 32.dp else 16.dp,
                end = if (isTv) 32.dp else 16.dp,
                top = 16.dp,
                bottom = if (isTv) 36.dp else 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "多源仓库与引擎配置 (TVBox 单仓 / 多仓 urls 自适应解密)",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }

            // Add New TVBox Config / Multi-Repo Card
            item {
                FrostedGlassBar(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "导入 TVBox 接口或 Multi-Repo 多仓地址",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "内置解密拦截器：自动识别 Base64、AES-CBC(2423/$#)、AES-ECB、PNG/BMP 尾部 `**` 隐写及注释混淆。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ResColors.TextSecondary
                        )

                        OutlinedTextField(
                            value = uiState.inputRepoName,
                            onValueChange = onRepoNameChanged,
                            label = { Text("仓库备注名称 (选填)", color = ResColors.TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ResColors.FocusCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.inputRepoUrl,
                            onValueChange = onRepoUrlChanged,
                            label = { Text("https://.../tvbox.json 或多仓地址", color = ResColors.TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ResColors.FocusCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ResColors.EmeraldScoreBadge,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ResColors.PrimaryViolet)
                                    .clickable { onAddAndActivateRepo() }
                                    .padding(horizontal = 22.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (uiState.isSyncingRepo) "解密同步中..." else "解密并挂载仓库",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Saved Repositories List
            item {
                Text(
                    text = "已缓存的多源仓库列表 (源失效时自动无缝切换备用仓)",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            items(savedRepos, key = { it.url }) { repo ->
                val active = repo.isActive || repo.url == preferences.activeRepoUrl
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .resCardSurface(shape = RoundedCornerShape(18.dp))
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) ResColors.FocusCyan else Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { onSelectRepo(repo) }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = repo.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = repo.url,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ResColors.TextSecondary
                            )
                        }
                        if (active) {
                            ResPillBadge(
                                text = "当前激活",
                                backgroundColor = ResColors.EmeraldScoreBadge,
                                contentColor = Color(0xFF0A1214)
                            )
                        }
                    }
                }
            }

            // Engine & UI Toggles
            item {
                FrostedGlassBar(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "核心引擎与 Res UI 视觉开关",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )

                        SettingToggleRow(
                            title = "HLS TS 切片智能去广告 (AdBlockHlsPlaylistParser)",
                            subtitle = "内存级剔除 #EXT-X-DISCONTINUITY 博彩/贴片广告切片与黑名单域名",
                            checked = preferences.enableHlsAdBlock,
                            onCheckedChange = onToggleAdBlock
                        )

                        SettingToggleRow(
                            title = "TMDB & 豆瓣 4K 海报/剧照/评分智能刮削",
                            subtitle = "自动清洗片名并从 TMDB (w780/w1280) 与豆瓣精准刮削超清海报与横版剧照",
                            checked = preferences.enablePosterScraping,
                            onCheckedChange = onTogglePosterScraping
                        )

                        if (preferences.enablePosterScraping) {
                            OutlinedTextField(
                                value = preferences.tmdbApiKey,
                                onValueChange = onUpdateTmdbApiKey,
                                label = { Text("自定义 TMDB API Key (留空使用官方内置密钥)", color = ResColors.TextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ResColors.FocusCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        SettingToggleRow(
                            title = "协程 Canvas 高帧率弹幕引擎",
                            subtitle = "播放时开启 60fps 硬件加速流式弹幕层",
                            checked = preferences.enableDanmaku,
                            onCheckedChange = onToggleDanmaku
                        )

                        SettingToggleRow(
                            title = "Material You 动态 Monet 壁纸取色",
                            subtitle = "移动端自动跟随系统壁纸主题色与海报 Palette 流光背景",
                            checked = preferences.enableDynamicColor,
                            onCheckedChange = onToggleDynamicColor
                        )

                        SettingToggleRow(
                            title = "强制开启 Android TV 大屏遥控器抽屉布局",
                            subtitle = "在手机或平板上预览电视端左侧抽屉导航与 1.08x 焦点光圈",
                            checked = preferences.forceTvMode,
                            onCheckedChange = onToggleForceTvMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = ResColors.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
