# ResBox UI/UX 设计规范与双端自适应视觉标准 ("Res" Minimalist Material You)

## 1. 设计愿景 (Design Vision)
**ResBox** 摒弃传统 TVBox 简陋陈旧的网格与弹窗设计，融合 **ReVanced Manager / Res 极简几何秩序感** 与 **Android 14+ Material You 动态流体色彩**，在手机触控端（Mobile/Tablet）与大屏电视端（Android TV 10-Foot UI）提供一致、60fps 丝滑且零视觉噪音的观影体验。

---

## 2. 色彩系统与动态流光背景 (Color Tokens & Dynamic Mesh Gradient)

| 语义 Token | 默认色值 (Dark Mode) | 动态取色逻辑与用途 |
| :--- | :--- | :--- |
| `ResBackgroundObsidian` | `#0B0E17` | 全局底层深邃黑曜石画布，防止 OLED 烧屏并突显海报色彩 |
| `ResSurfaceGlass` | `#181E30` (Alpha 72%) | 顶栏、悬浮胶囊底栏、TV 侧边抽屉的毛玻璃半透明基底 (`RenderEffect` 28dp 模糊) |
| `ResPrimaryAccent` | `#7C5CFF` (Mobile 动态 Monet) | 移动端优先跟随系统 `dynamicDarkColorScheme`，默认采用电光紫 |
| `ResFocusCyan` | `#00E5FF` | Android TV 遥控器焦点光圈高亮色、测速最优源标识 |
| `ResBadge4KGold` | `#FFB300` | `4K` / `HDR10+` / `DoVi` 画质胶囊标签背景色 |
| `ResBadgeScoreGreen` | `#00E676` | 豆瓣评分 / IMDb 高分胶囊标签 |
| `PaletteDominantGlow` | 运行时 `Palette` 提取 | 焦点海报或详情页背景提取 `vibrantSwatch` + `darkMutedSwatch` 生成三点径向 Mesh Gradient |

---

## 3. 卡片、圆角与排版层级 (Cards, Specular Borders & Typography)

1. **大圆角几何体系**：
   - 海报卡片统一采用 `RoundedCornerShape(20.dp)`；详情页主视觉与 Hero Banner 采用 `RoundedCornerShape(24.dp)`；胶囊标签（Pill Badge）采用 `RoundedCornerShape(50)`。
2. **微光描边 (Border Specular)**：
   - 所有卡片表面叠加 `1.dp` 线性渐变高光描边：`Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.04f)))`，模拟玻璃边缘折射。
3. **无转圈骨架屏 (Shimmer Skeleton)**：
   - 全局严禁使用生硬的 `CircularProgressIndicator` 阻塞列表，所有加载态统一使用 `ResShimmerPlaceholder` 1200ms 线性流光骨架屏。

---

## 4. 双端自适应交互规范 (Mobile Touch vs. Android TV D-Pad)

### 4.1 移动端 / 平板 (Mobile & Tablet)
- **导航形态**：底部悬浮毛玻璃胶囊栏 (`FloatingPillNavigationBar`)，距离底部 `20.dp` 悬浮，支持 4 个核心入口（首页、聚合搜索、电视直播、多仓配置）。
- **播放手势**：
  - 左侧上下滑动：无级调节屏幕亮度 (`WindowManager.LayoutParams.screenBrightness`)
  - 右侧上下滑动：调节系统媒体音量 (`AudioManager.STREAM_MUSIC`)
  - 水平滑动：精准预览并 Seek 视频进度
  - 长按屏幕任意位置：触发 `2.0x` 倍速快进并展示顶部流光速率胶囊，松手恢复原倍速。

### 4.2 电视端 (Android TV / Leanback)
- **导航形态**：左侧可折叠毛玻璃导航抽屉 (`TvNavigationDrawer`)，收起宽度 `76.dp`，获焦展开至 `220.dp`。
- **强焦点反馈 (`Modifier.resTvFocusable`)**：
  - 遥控器 D-Pad 焦点移入时，`animateFloatAsState` 驱动卡片缩放 `1.0f -> 1.08f`（阻尼弹簧 `Spring.DampingRatioLowBouncy`）。
  - 外围绘制 `2.5.dp` `ResFocusCyan` 霓虹光圈边框及 `16.dp` 扩散阴影。
  - 联动触发首页/详情页背景 `Palette` 主色调平滑过渡（600ms `animateColorAsState`）。
- **遥控器快捷操作**：
  - 播放器内支持遥控器数字键 `0-9` 组合快速跳集、左右键快进快退、下键呼出选集与音轨/字幕面板。
