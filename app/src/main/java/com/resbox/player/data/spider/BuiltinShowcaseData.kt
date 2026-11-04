package com.resbox.player.data.spider

import com.resbox.player.data.model.CategoryBean
import com.resbox.player.data.model.LiveBean
import com.resbox.player.data.model.LiveChannelGroup
import com.resbox.player.data.model.LiveChannelItem
import com.resbox.player.data.model.ParseBean
import com.resbox.player.data.model.SiteBean
import com.resbox.player.data.model.TvBoxConfig
import com.resbox.player.data.model.VodInfo
import com.resbox.player.data.model.VodResponsePayload

/**
 * Provides a pre-configured, out-of-the-box TVBox multi-site catalog with real HLS test streams
 * so ResBox renders rich 4K/HDR posters, multi-source PlayFrom switching, concurrent search,
 * and Media3 HLS playback immediately upon installation.
 */
object BuiltinShowcaseData {

    val defaultConfig = TvBoxConfig(
        spider = "",
        wallpaper = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1920&q=80",
        sites = listOf(
            SiteBean(
                key = "res_wogg_4k",
                name = "玩偶臻彩 4K",
                type = 1,
                api = "resbox://showcase/wogg",
                searchable = 1,
                quickSearch = 1,
                filterable = 1,
                categories = listOf("4K 电影", "热播剧集", "科幻动作", "高分纪录", "国漫番剧")
            ),
            SiteBean(
                key = "res_jisu_cloud",
                name = "极速云播 PRO",
                type = 1,
                api = "resbox://showcase/jisu",
                searchable = 1,
                quickSearch = 1,
                filterable = 1,
                categories = listOf("4K 电影", "热播剧集", "科幻动作", "高分纪录")
            ),
            SiteBean(
                key = "res_liangzi_vod",
                name = "量子秒播源",
                type = 1,
                api = "resbox://showcase/liangzi",
                searchable = 1,
                quickSearch = 1,
                filterable = 1,
                categories = listOf("4K 电影", "热播剧集", "国漫番剧")
            ),
            SiteBean(
                key = "res_feifan_hdr",
                name = "非凡 HDR 专线",
                type = 1,
                api = "resbox://showcase/feifan",
                searchable = 1,
                quickSearch = 1,
                filterable = 1,
                categories = listOf("4K 电影", "科幻动作", "高分纪录")
            )
        ),
        parses = listOf(
            ParseBean(
                name = "Res 极速嗅探器",
                type = 0,
                url = "https://jx.xmflv.com/?url="
            ),
            ParseBean(
                name = "聚合 JSON 专线",
                type = 1,
                url = "https://jx.playerjy.com/?url="
            )
        ),
        lives = listOf(
            LiveBean(
                name = "ResBox 全球臻享直播",
                type = 0,
                url = "resbox://showcase/live.m3u",
                epg = "https://epg.112114.xyz/?ch={name}&date={date}",
                channels = listOf(
                    LiveChannelGroup(
                        group = "4K 超高清卫视频道",
                        channels = listOf(
                            LiveChannelItem(
                                name = "CCTV-1 综合 HD",
                                urls = listOf(
                                    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                                    "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
                                ),
                                tvgId = "CCTV1",
                                catchupSource = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8?playseek={utc}-{lutc}"
                            ),
                            LiveChannelItem(
                                name = "CCTV-5+ 体育赛事 4K",
                                urls = listOf(
                                    "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
                                ),
                                tvgId = "CCTV5P"
                            ),
                            LiveChannelItem(
                                name = "CCTV-6 电影频道 HDR",
                                urls = listOf(
                                    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                                ),
                                tvgId = "CCTV6"
                            )
                        )
                    ),
                    LiveChannelGroup(
                        group = "纪录与探索频道",
                        channels = listOf(
                            LiveChannelItem(
                                name = "Discovery 科学探索 4K",
                                urls = listOf(
                                    "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
                                ),
                                tvgId = "DISC4K"
                            ),
                            LiveChannelItem(
                                name = "NatGeo 国家地理臻彩",
                                urls = listOf(
                                    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                                ),
                                tvgId = "NATGEO"
                            )
                        )
                    )
                )
            )
        ),
        ads = listOf(
            "vip.ffzy-play",
            "ad.doubleclick.net",
            "bet365",
            "macau-casino",
            "slot-ad-segment"
        )
    )

    private val categories = listOf(
        CategoryBean(typeId = "all", typeName = "精选推荐"),
        CategoryBean(typeId = "movie_4k", typeName = "4K 电影"),
        CategoryBean(typeId = "series_hot", typeName = "热播剧集"),
        CategoryBean(typeId = "scifi_action", typeName = "科幻动作"),
        CategoryBean(typeId = "docu_hdr", typeName = "高分纪录"),
        CategoryBean(typeId = "anime_cn", typeName = "国漫番剧")
    )

    private val catalog = listOf(
        VodInfo(
            vodId = "vod_dune_2",
            vodName = "沙丘2 (Dune: Part Two)",
            vodPic = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&q=80",
            vodRemarks = "4K DoVi • 60帧",
            vodYear = "2024",
            vodArea = "美国 / 加拿大",
            vodActor = "提莫西·查拉梅, 赞达亚, 丽贝卡·弗格森, 哈维尔·巴登",
            vodDirector = "丹尼斯·维伦纽瓦",
            vodContent = "保罗·厄崔迪与契妮和弗雷曼人联手，对毁灭他家族的阴谋者展开复仇。在挚爱与已知宇宙命运的抉择面前，他努力阻止只有他能预见的可怕未来。",
            vodPlayFrom = "玩偶4K原盘$$$极速云HDR$$$量子秒播$$$非凡蓝光",
            vodPlayUrl = buildMultiSourcePlayUrl(episodeCount = 3, isMovie = true),
            typeName = "科幻动作",
            vodScore = "8.9"
        ),
        VodInfo(
            vodId = "vod_oppenheimer",
            vodName = "奥本海默 (Oppenheimer)",
            vodPic = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&q=80",
            vodRemarks = "IMAX 4K REMUX",
            vodYear = "2023",
            vodArea = "美国 / 英国",
            vodActor = "基里安·墨菲, 艾米莉·布朗特, 马特·达蒙, 小罗伯特·唐尼",
            vodDirector = "克里斯托弗·诺兰",
            vodContent = "聚焦基里安·墨菲饰演的“原子弹之父”罗伯特·奥本海默，讲述他在二战期间领导曼哈顿计划研发核武器，以及战后听证会上的道德与政治风暴。",
            vodPlayFrom = "玩偶4K原盘$$$极速云HDR$$$量子秒播",
            vodPlayUrl = buildMultiSourcePlayUrl(episodeCount = 2, isMovie = true),
            typeName = "4K 电影",
            vodScore = "8.8"
        ),
        VodInfo(
            vodId = "vod_long_season",
            vodName = "漫长的季节 (4K 导演剪辑版)",
            vodPic = "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=800&q=80",
            vodRemarks = "12集全 • 4K HDR",
            vodYear = "2023",
            vodArea = "中国大陆",
            vodActor = "范伟, 秦昊, 陈明昊, 李庚希, 刘奕铁",
            vodDirector = "辛爽",
            vodContent = "小城桦林，出租车司机王响、妹夫龚彪与老刑警马德胜跨越十八年的命运交织，在秋日玉米地与钢厂往事中追寻真相与和解。",
            vodPlayFrom = "玩偶4K原盘$$$极速云HDR$$$量子秒播$$$非凡蓝光",
            vodPlayUrl = buildMultiSourcePlayUrl(episodeCount = 12, isMovie = false),
            typeName = "热播剧集",
            vodScore = "9.4"
        ),
        VodInfo(
            vodId = "vod_interstellar",
            vodName = "星际穿越 (Interstellar 10周年重映版)",
            vodPic = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&q=80",
            vodRemarks = "4K UHD • 杜比全景声",
            vodYear = "2024",
            vodArea = "美国 / 英国",
            vodActor = "马修·麦康纳, 安妮·海瑟薇, 杰西卡·查斯坦",
            vodDirector = "克里斯托弗·诺兰",
            vodContent = "在不远的未来，地球黄沙遍野，农作物相继枯萎。前NASA宇航员库珀穿越土星附近的虫洞，跨越五维时空为人类寻找新家园。",
            vodPlayFrom = "玩偶4K原盘$$$极速云HDR$$$非凡蓝光",
            vodPlayUrl = buildMultiSourcePlayUrl(episodeCount = 2, isMovie = true),
            typeName = "科幻动作",
            vodScore = "9.4"
        ),
        VodInfo(
            vodId = "vod_blossoms_shanghai",
            vodName = "繁花 (沪语 4K 典藏版)",
            vodPic = "https://images.unsplash.com/photo-1477959858617-67f85cf4f1df?w=800&q=80",
            vodRemarks = "30集全 • 4K 臻彩",
            vodYear = "2024",
            vodArea = "中国大陆",
            vodActor = "胡歌, 马伊琍, 唐嫣, 辛芷蕾, 游本昌",
            vodDirector = "王家卫",
            vodContent = "九十年代的上海黄河路霓虹闪烁，青年阿宝在爷叔指点下蜕变为宝总，在资本浪潮与时代变迁中书写一段海派传奇。",
            vodPlayFrom = "玩偶4K原盘$$$极速云HDR$$$量子秒播",
            vodPlayUrl = buildMultiSourcePlayUrl(episodeCount = 30, isMovie = false),
            typeName = "热播剧集",
            vodScore = "8.7"
        ),
        VodInfo(
            vodId = "vod_planet_earth_3",
            vodName = "脉动地球 第三季 (Planet Earth III)",
            vodPic = "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=800&q=80",
            vodRemarks = "8集全 • 4K HLG",
            vodYear = "2023",
            vodArea = "英国",
            vodActor = "大卫·爱登堡",
            vodDirector = "BBC Studios",
            vodContent = "大卫·爱登堡爵士再次带我们深入地球最壮丽的角落，以8K微距与高速摄影揭开海洋、荒漠、雨林与人类世共生的生命奇迹。",
            vodPlayFrom = "玩偶4K原盘$$$非凡蓝光",
            vodPlayUrl = buildMultiSourcePlayUrl(episodeCount = 8, isMovie = false),
            typeName = "高分纪录",
            vodScore = "9.7"
        ),
        VodInfo(
            vodId = "vod_linglong",
            vodName = "灵笼 第二季 (Incarnation II)",
            vodPic = "https://images.unsplash.com/photo-1563089145-599997674d42?w=800&q=80",
            vodRemarks = "更新至第16集 • 4K 60帧",
            vodYear = "2025",
            vodArea = "中国大陆",
            vodActor = "夏一可, 图特哈蒙, 藤新",
            vodDirector = "董相博",
            vodContent = "灯塔坠落之后，白月魁与猎荒者小队深入地面玛娜生态腹地，揭开旧世界基因工程与人类进化终极秘密。",
            vodPlayFrom = "玩偶4K原盘$$$极速云HDR$$$量子秒播",
            vodPlayUrl = buildMultiSourcePlayUrl(episodeCount = 64, isMovie = false),
            typeName = "国漫番剧",
            vodScore = "9.1"
        ),
        VodInfo(
            vodId = "vod_blade_runner_2049",
            vodName = "银翼杀手 2049 (Blade Runner 2049)",
            vodPic = "https://images.unsplash.com/photo-1511447333015-45b65e60f6d5?w=800&q=80",
            vodRemarks = "4K DoVi • 全景声",
            vodYear = "2017",
            vodArea = "美国",
            vodActor = "瑞恩·高斯林, 哈里森·福特, 安娜·德·阿玛斯",
            vodDirector = "丹尼斯·维伦纽瓦",
            vodContent = "新一代银翼杀手 K 在执行猎杀任务时意外发现一段尘封三十年的复制人奇迹，踏上寻找前代银翼杀手瑞克·戴克之旅。",
            vodPlayFrom = "玩偶4K原盘$$$极速云HDR$$$量子秒播$$$非凡蓝光",
            vodPlayUrl = buildMultiSourcePlayUrl(episodeCount = 2, isMovie = true),
            typeName = "4K 电影",
            vodScore = "8.5"
        )
    )

    private fun buildMultiSourcePlayUrl(episodeCount: Int, isMovie: Boolean): String {
        val streamA = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        val streamB = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"

        val group1 = (1..episodeCount).joinToString("#") { ep ->
            val label = if (isMovie) {
                when (ep) {
                    1 -> "4K原盘正片"
                    2 -> "杜比视界轨"
                    else -> "导演花絮$ep"
                }
            } else {
                "第%02d集".format(ep)
            }
            "$label$$streamA"
        }
        val group2 = (1..episodeCount).joinToString("#") { ep ->
            val label = if (isMovie) "极速HDR正片$ep" else "第%02d集".format(ep)
            "$label$$streamB"
        }
        val group3 = (1..episodeCount).joinToString("#") { ep ->
            val label = if (isMovie) "秒播1080P-$ep" else "第%02d集".format(ep)
            "$label$$streamA"
        }
        val group4 = (1..episodeCount).joinToString("#") { ep ->
            val label = if (isMovie) "蓝光专线-$ep" else "第%02d集".format(ep)
            "$label$$streamB"
        }
        return listOf(group1, group2, group3, group4).joinToString("$$$")
    }

    fun getHomePayload(site: SiteBean): VodResponsePayload {
        return VodResponsePayload(
            categories = categories,
            list = catalog.map { it.copy(siteKey = site.key, siteName = site.name) }
        )
    }

    fun getCategoryPayload(site: SiteBean, typeId: String, page: Int): VodResponsePayload {
        val filtered = if (typeId == "all" || typeId.isBlank()) {
            catalog
        } else {
            val catName = categories.find { it.typeId == typeId }?.typeName.orEmpty()
            catalog.filter { it.typeName == catName }.ifEmpty { catalog }
        }
        return VodResponsePayload(
            categories = categories,
            list = filtered.map { it.copy(siteKey = site.key, siteName = site.name) },
            page = page,
            pageCount = 1
        )
    }

    fun getDetailVod(site: SiteBean, vodId: String): VodInfo? {
        return (catalog.find { it.vodId == vodId } ?: catalog.firstOrNull())
            ?.copy(siteKey = site.key, siteName = site.name)
    }

    fun searchVod(site: SiteBean, keyword: String): List<VodInfo> {
        val kw = keyword.trim().lowercase()
        val matched = if (kw.isEmpty()) {
            catalog
        } else {
            catalog.filter {
                it.vodName.lowercase().contains(kw) ||
                    it.vodActor.lowercase().contains(kw) ||
                    it.vodDirector.lowercase().contains(kw) ||
                    it.typeName.lowercase().contains(kw)
            }.ifEmpty {
                // Return top 2 relevant items so user can always test concurrent multi-source search
                catalog.take(2)
            }
        }
        return matched.map { it.copy(siteKey = site.key, siteName = site.name) }
    }
}
