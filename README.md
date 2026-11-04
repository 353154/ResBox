# ResBox — 次世代 Android & Android TV 影视聚合播放器

**ResBox** 是一款 100% 使用 **Kotlin + Jetpack Compose (Material 3 + TV Material 3) + Media3 (ExoPlayer) + Clean Architecture** 构建的次世代双端自适应影视聚合播放器，完全兼容 **TVBox / 猫影视开源生态**。

---

## 核心特性与架构亮点

1. **“Res” 极简现代视觉规范 (`ResTheme.kt` / `ResComponents.kt`)**
   - **Material You & Palette 流光背景**：移动端支持 Android 12+ Monet 动态壁纸取色；浏览海报或进入详情页时，自动通过 `Palette` 提取当前焦点海报主色调，实时渲染柔和的高斯模糊 Mesh Gradient 背景。
   - **双端自适应导航 (`ResBoxNavigation.kt`)**：手机端采用悬浮毛玻璃胶囊底栏 (`MobileFloatingPillBar`)；Android TV 端自动切换为左侧可折叠抽屉导航 (`TvCollapsibleNavigationDrawer`) + 遥控器 D-Pad `1.0f -> 1.08f` 阻尼缩放与霓虹光圈边框。
   - **零转圈骨架屏与共享元素过渡**：全局使用 `ResShimmerBox` 流光骨架屏替代生硬转圈，并使用 Compose `SharedTransitionLayout` 实现海报列表到详情页的无缝过渡。

2. **TVBox 生态完全兼容 (`ConfigDecryptor.kt` / `JarLoader.kt` / `NativeCmsEngine.kt`)**
   - **多源仓库与自动解密**：兼容单仓 JSON 与多仓 (`urls` / `storeHouse`) 格式，自动识别并解密 Base64、AES-128-CBC (`2423`/`$#`)、AES-ECB、图片尾部 `**` 隐写以及非标准注释 JSON。
   - **动态 Spider 加载器**：通过 `DexClassLoader` 动态加载远程 `.jar` / `.dex` 爬虫包（100% 兼容 `com.github.catvod.crawler.Spider` 接口），同时内置 Apple CMS (MacCMS JSON/XML) 与 Jsoup 解析引擎。
   - **防御性 JSON 解析 (`DefensiveSerializers.kt`)**：针对第三方接口类型混乱问题，定制 `FlexibleStringListSerializer`、`FlexibleIntSerializer`、`FlexibleExtSerializer` 与 `HtmlCleanStringSerializer`。

3. **并发流式搜索与智能换源 (`VodRepository.kt`)**
   - **并发搜索池**：基于 `channelFlow` + `Semaphore(10)` 控制并发度，搜到一个源立即在 UI 流式插入展示，无需等待全部完成。
   - **智能测速与无缝换源**：详情页与播放器支持并发 HTTP HEAD 测速排序；当某线路播放失败或超时，自动无缝切换至最快同名备用源并记忆播放进度。

4. **无头 WebView 嗅探与内存级 TS 切片去广告 (`HeadlessWebSniffer.kt` / `AdBlockHlsPlaylistParserFactory.kt`)**
   - **无头嗅探 (`parse: 1`)**：重写 `shouldInterceptRequest` 精准捕获 `.m3u8` / `.mp4` / `.flv` 媒体流及动态 `User-Agent`、`Referer`、`Cookie`，离开页面时严格销毁 `WebView` 防止内存泄漏。
   - **HLS TS 切片去广告**：自定义 `HlsPlaylistParserFactory`，分析 `#EXT-X-DISCONTINUITY` 切片时长突变、路径前缀偏移及域名黑名单，在内存中直接剔除插播的博彩/贴片广告切片。
