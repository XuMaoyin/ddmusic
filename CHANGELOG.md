# 改动记录（CHANGELOG）

本文件记录本项目的每一次**已验证**的修改。约定：

- 新改动加在「最新」一节的最前面，倒序排列；
- 每条必须写清四件事：**问题现象 → 根因 → 改了什么 → 怎么验证的**；
- 只记录验证通过的改动；未验证或验证失败的写在条目标题里注明「未验证」；
- 涉及用户数据（曲库文件、内嵌标签等）的改动要单独标注，并说明是否可回滚。

---

# 2026-11-04

## 发布 1.0.1：更名「点点音悦」、包名改为 `top.juluo.ddmusic`

**背景**：应用要从"车机专用"升级为通用版本（与桌面版同名同源），需统一名称与包名。

**改动**：
- `app_name`：「点点音悦车机版」→「**点点音悦**」
- `applicationId` 与 `AndroidManifest` 的 package：`top.juluo.ddMusicCar` → **`top.juluo.ddmusic`**，源码目录同步迁移到 `java/top/juluo/ddmusic/`
- 全量替换 41 个文件中的包名引用，含布局里自定义 View 的全限定名（`PlayingIndicatorView`、`HeightFitSquareLayout`）以及 `build.bat`、`DEV_ENV.md` 里的 adb 命令
- `versionCode` 1 → **2**，`versionName` 1.0 → **1.0.1**

**涉及用户数据**：换包名等于新应用，旧版的设置项（`ui_settings` / `scan_settings`）与播放次数统计**不会迁移**；安装前需卸载旧包。**不可回滚**——装回旧包同样拿不回数据。

**验证**：干净安装后启动、播放、界面列表标记、设置页、关于页全部正常；媒体会话包名为 `top.juluo.ddmusic`；关于页显示「版本 1.0.1 (2)」；无崩溃。

> 车机侧注意：小迪通过 broadcast action（`cn.kuwo.kwmusicauto.action.*`）寻址，action 未变所以仍能收到；但若车机缓存了旧组件名，可能需要在语音设置里重新选择播放器。

---

## 调研：vivo Jovi 语音为什么接不进来自定义音乐 App

**结论（不可行，避免重复调研）**：vivo 的语音音乐控制**按"默认音乐源"包名精确路由**，第三方 App 无法参与，也复刻不了小迪那种"弹框选 App"。

**证据链**：
1. 语音"播放某首歌"的完整链路：Jovi → `com.tencent.qqmusic/.third.DispacherActivityForThird`（`qqmusic://qq.com/...` 私有深链）→ QQ音乐回调 `gptagent://qqmusiccallbackverify/...` 完成鉴权 → 播放。全程无广播、无媒体键。
2. 我们注册了同 scheme（`qqmusic://`）的探针后再发指令：**探针从未被唤起，也没有任何应用选择器**——说明 Jovi 是显式组件调用，不是隐式 Intent。
3. Jovi 的包可见性（`<queries>`）里只有 `com.tencent.qqmusic` 等合作方；`com.vivo.agent.permission.VOICE_SKILL` 为 `signature|privileged`。
4. Jovi 有「默认音乐源」设置页（`DefaultMusicSourceSettingActivity`，受 `com.vivo.ai.copilot.permission.COPILOT_SETTINGS` 保护），选完后写三个键：`system music_widget_mix_play_control_pkg_key`、`secure music_source`、`secure MusicWidgetMix_MusicSource`；候选列表固定。
5. 播放控制走 `com.vivo.media.FeatureService.handleMediaButtonEventTransfer`，**按上述包名转发**。实测把 `music_widget_mix_play_control_pkg_key` 改成我们的包后，语音"暂停"确实停的是本 App（说明路由只认包名，不认当前活跃的 MediaSession）。

**对比小迪（车机）**：小迪发的是**隐式 Intent**，酷我和我们的 App 都注册了同一 action，系统因此弹出应用选择框——这条路在 vivo 上不存在。

**仍可用的语音入口**：Jovi 的"打开应用"是通用的，说「打开点点音悦」即可拉起本应用（配合「启动时自动播放」直接进播放页）。

---

## 功能：列表标记正在播放的曲目

**问题**：曲库列表里看不出当前播放的是哪首。

**改动**（参考桌面版点点音悦的做法）：
- 每行最左侧栏位显示**序号**；当前播放行用**三根跳动的频谱**替掉序号
- 当前行另有**整行圆角高亮** + 歌名改为强调色；**封面不做任何遮挡**
- 新增 `PlayingIndicatorView`：`postInvalidateOnAnimation` + `sin(t*speed+phase)` 逐条错相位，不用 `ValueAnimator`，回收时自动停
- `SongAdapter.setPlayingState()` 只对受影响的行 `notifyItemChanged`，避免上千条整表重绘

**验证**：
- 播放中连拍两帧 MD5 不同（在动）；暂停后两帧完全相同（静止，保留静态条标识当前曲目）
- 四位数序号（1193–1201）不截断
- 切歌后标记从旧行移动到新行

---

## 功能：设置页拆出二级页 + 默认排序方式

**改动**：
- 新增 `ScanDirsActivity`（曲库扫描目录）：原先嵌在设置页底部的目录开关列表整体迁入
- 新增 `AboutActivity`（关于）：版本号运行时读 `PackageManager`（改版本号即自动更新）、开发者、GitHub 占位
- 设置页改为纯入口列表，外层套 `ScrollView`，小屏不被裁
- 新增「歌曲列表 → 默认排序方式」：按名称 / 按最近添加 / 按播放次数，**初始默认按最近添加**
- 排序值与工具栏排序按钮**共用同一个存储**，两个入口不会互相打架；`onResume` 检测到值变化即重排

**验证**：二级页开关实际写入 `scan_settings.xml`；选「按名称」后返回列表立即重排（`sort_mode=0`）；点工具栏排序按钮后 prefs 同步为 1。

---

## 优化：状态栏——竖屏随页面配色、横屏全屏

**问题**：播放页背景是随封面取色的渐变，顶部却是一条固定深色状态栏，割裂感强；横屏（车机主场景）还白占一条高度。

**改动**：`PlayerActivity.applySystemBars()` + `updateStatusBarColor()`
- 竖屏：状态栏颜色设成当前背景色，并挂在背景取色的同一个 `ValueAnimator` 回调里同步过渡；图标明暗按背景亮度切换（`SYSTEM_UI_FLAG_LIGHT_STATUS_BAR`）
- 横屏：`SYSTEM_UI_FLAG_FULLSCREEN` 全屏隐藏状态栏

**验证**：竖屏状态栏区域（y=20）与页面（y=120）像素完全相同；横屏顶部无状态栏、内容顶到屏幕边缘；横屏转回竖屏时状态栏正常恢复并重新着色。

---

## 优化：横屏布局重构（进度条到底部）+ 歌词行距/换行

**问题**：① 进度条和按钮一起挤在左侧 36% 宽的栏里，21:9 手机上又短又难点；② 按钮图标在窄屏被 padding 挤得很小；③ 歌词行距过大、超长句换行后被裁、淡化遮罩吃掉可读区。

**根因**：控制区是整块嵌在左栏的；按钮 padding 写死 22dp；歌词行高是固定值且 XML 里还叠了 20dp padding。

**改动**：
- 抽出 `player_progress_row.xml` / `player_buttons_row.xml`；横屏进度条**移到屏幕最底部、横跨全宽**，左栏只留按钮
- 按钮图标改为按**目标图标尺寸反推 padding**（普通 32dp、播放键 28dp，限制 6–22dp）：宽屏自然回落到原来的 22/24dp，只有窄屏才缩小留白
- 横屏歌词 `padVertical` 40dp → **10dp**；`item_player_lyric_line.xml` **去掉 20dp XML padding**（收紧后它把 48sp 文字压到 113px，导致上下被裁）
- 行高由"固定值"改为"**最小高度**"：超长句换行后该行自动撑高；居中计算改用目标行的真实高度
- 淡化遮罩高度改为歌词区高度的 1/5（限制 24–90dp），手机横屏不再被遮罩吃光

**验证**：
- 底部进度条：点击 x=1400 → 1:35，拖到 x=800 → 0:39
- 长句「出品：北京昌禾文化发展有限公司」完整显示为两行，无裁切
- 车机 4:3 宽屏上按钮图标尺寸与改动前一致（padding 22dp）
- 横竖屏切换后布局、行距、遮罩均正确

---

## 优化：进度条变细并去掉圆点

**问题**：轨道太粗（实测 13.7dp），拖动时圆点位置与线条不协调。

**根因（重要坑）**：`ProgressBar` 会把 `progressDrawable` 的 bounds **拉伸铺满内容区**——改 drawable 里的 `<size android:height>` 完全无效，改成 2dp 后实测仍是 13.7dp。粗细只能靠 View 的上下 padding 控制。

**改动**：
- `SeekBar` 保持 `layout_height="44dp"`（触摸区不变，好点按），上下各 `padding=21dp` → 内容区 2dp
- `thumb="@null"` 去掉圆点，当前进度由填充段右端体现
- 顺带修掉一个被掩盖的缺陷：`getThumb()` 为 null 时 NPE 会被同一个 `try` 块吞掉，导致后面的 `getProgressDrawable()` 着色被跳过（切到浅色封面时进度条会变成纯白）。已拆成两个独立 `try`

**验证**：轨道实测 6px ≈ 1.7dp（原 48px ≈ 13.7dp）；点击中点 0:04 → 2:00，拖动至左侧 → 0:11，定位准确。

---

## 修复：语音点歌会把界面拉回列表页

**问题**：小迪语音"播放某首歌"时，界面被拉到歌曲列表。期望：只在后台切歌、不切换界面。但冷启动（应用原本没运行）时仍应播放并进入播放页。

**根因**：酷我协议的 activity intent 原本由 `MainActivity` 承接，而 MainActivity 是 `launchMode="singleTask"`。被语音拉起时，系统会把它之上的播放页从栈里清掉，于是用户从播放页"掉回"列表页。

**改动**：
- 新增 `VoiceRelayActivity`：透明、`noHistory`、`excludeFromRecents`、**独立 `taskAffinity`**
- 把 MainActivity 上的酷我 action 全部迁移到该中转页（MainActivity 只保留 MAIN / MUSIC_PLAYER）
- 中转页处理完立即 `finish()`，返回栈不动；**仅当"应用原本未运行 + intent 带歌名"**时，用 `FLAG_ACTIVITY_NEW_TASK` 打开播放页
- `PlayerActivity.updateSong()` 增加空曲目重试（300ms × 5），避免冷启动时语音还在装队列就被判定"没有在播放"而自行关闭
- 新增 `VoiceRelayTheme`（透明、无转场动画）

**关键点**：`taskAffinity` 必须与主界面不同。第一版没设置它，结果拉起中转页等于把整个应用任务栈带到前台——应用在后台时依然会被迫弹出（实测失败后修正）。

**验证**（adb 发送 activity intent 模拟小迪，四个场景全部实测）：

| 场景 | 期望 | 结果 |
| --- | --- | --- |
| 停在播放页 | 界面不变、切歌 | ActivityRecord 未变，歌曲 123我爱你 → 9420 |
| 停在列表 | 界面不变、切歌 | ActivityRecord 未变，歌曲 → DNA |
| 应用在后台（桌面） | 前台仍是桌面 | 前台仍是 Launcher，歌曲 → DNA |
| 进程不存在（冷启动） | 进播放页并播放 | 进入 PlayerActivity，播放 9420 |

另外回归确认：点图标冷启动仍会自动播放并跳转播放页（该行为受「启动时自动播放」开关控制）。

**涉及文件**：新增 `VoiceRelayActivity.java`、`styles.xml`（VoiceRelayTheme）；修改 `AndroidManifest.xml`、`PlayerActivity.java`。

---

# 2026-11-03

## 环境搭建（从零到可构建）

**问题**：本机没有 JDK，Android SDK 只有一个空的 `cmdline-tools`，没有 `local.properties`，`~/.gradle` 为空，项目无法构建。

**根因**：全新机器，且项目使用 Gradle 6.1.1 + AGP 4.0.1，对工具链版本有硬性要求。

**改动**：
- 安装 Oracle JDK 8u144 → `C:\Program Files\Java\jdk1.8.0_144`（**必须 JDK 8**，Gradle 6.1.1 只支持到 Java 13）
- 安装 Android SDK 组件：`platform-tools`、`platforms;android-29`、`build-tools;29.0.3`
- 额外安装兼容 JDK 8 的旧版命令行工具到 `cmdline-tools/8.0`（`latest` 需要 Java 17，本机不具备）
- 新增 `local.properties` 指向本机 SDK
- 新增 `build.bat`：固定 JDK/SDK 路径的一键构建脚本
- 新增 `DEV_ENV.md`：环境说明
- `gradle/wrapper/gradle-wrapper.properties` 发行版地址改为腾讯云镜像（官方地址重定向到国内无法解析的域名）

**验证**：`gradlew --version`（Gradle 6.1.1 / JVM 1.8.0_144）、`clean assembleDebug assembleRelease` 通过、APK badging 信息正确。

---

## 修复：外部 .lrc 歌词匹配到别的歌（影响 75 首）

**问题**：部分歌曲显示的歌词属于另一首歌。实测「情歌 - 梁静茹」（4:20）加载到了刀郎《西海情歌》（5:02）的歌词。

**根因**：`LyricsLoader` 用 `.*<标题>.*\.(lrc|txt)` 做**包含匹配**，再取 `File.listFiles()` 返回顺序里的**第一个**。目录顺序由文件系统决定，于是「情歌」同时命中「西海情歌」「小情歌」「单身情歌」「情歌悠扬」等 8 个文件，谁在前用谁。

**改动**：`LyricsLoader.java` 改为分层匹配 + 层内确定排序：
1. 与音频文件完全同名
2. 与标题完全相等
3. 以标题开头且紧跟分隔符（排除「西海情歌」「青花瓷」这类"包含但不以标题开头"的）
4. 以歌手名开头且包含标题
5. 标题与歌手名同时出现
6. 都不满足 → 返回 null（显示「暂无歌词」，不猜）

层内排序：`.lrc` 优于 `.txt` → 含歌手名优先 → 名字短优先 → 字典序（与目录顺序无关）。

**验证**：
- 真机 `情歌` 日志：`pick title=情歌 tier=0 -> 情歌 - 梁静茹.lrc`，`lines=42/lastTs=302550` → `lines=43/lastTs=239130`
- 全库模拟（1209 首 + 1199 个 lrc）：旧算法取错 **75 首**，新算法 **0 首取错**，1197 首命中 tier0，12 首无歌词

**副作用**：无匹配时不再"猜"，会显示「暂无歌词」。

---

## 修复：断点续播每次都跳过一首

**问题**：冷启动恢复到上次曲目时，总是播放保存曲目的**下一首**。

**根因**：`MusicService.resumePlaying()` 在播放器还没装载音源时直接调 `mediaPlayer.start()`。老 Android 抛 `IllegalStateException`（代码里 `catch` 就是为此写的），但新版本改为通过 `onError` 上报 `-38`（`MEDIA_ERROR_INVALID_OPERATION`），被 `onErrorListener` 误判成"文件不可读"→ `playNextSong(true)` → 跳过该曲目。

**改动**：`MusicService.java` 增加 `loadedIndex` 字段记录播放器实际装载的队列下标：
- 只有 `loadedIndex == position` 才 `start()`，否则先 `playSongAt(position)` 装载
- `playOrPause()` 同样加了保护（冷启动后点播放会踩同一个坑）
- `playSongAt` 失败、`release` 时复位

**验证**：`SAVED_POSITION=8` → 重启后恢复「99次我爱他」（索引 8），修复前会跳到索引 9。

---

## 修复：歌内进度不恢复 + 进度只在退出时保存

**问题**：续播只回到"哪首歌"，每首都从头播；且 `POSITION_IN_TRACK` 一直在存却从不读。另外 `savePosition()` 只在 `onDestroy` 和歌曲播完时调用，车机断电（进程被直接杀）时进度丢失。

**根因**：`restoreQueuesAndPosition()` 只使用 `SAVED_POSITION`；保存时机过少。

**改动**：`MusicService.java`
- 新增 `pendingSeekMs` / `pendingSeekPosition`，恢复时记录待跳转位置，在 `playSongAt()` 的 `prepare()` 之后由 `applyPendingSeek()` 消费（绑定下标、用完即清、越界钳位）
- 新增 5 秒定时保存（仅在 `isPlaying()` 时写盘）
- `pauseSong()` / `playOrPause()` 暂停分支即时保存

**验证**：播放 12 秒后 `am force-stop`（不触发 onDestroy），重启后 `position=9747`，界面显示 0:12。

---

## 修复：GBK 编码歌词乱码（回退分支是死代码）

**问题**：GBK 编码的 `.lrc` 全部显示为乱码。

**根因**：`new String(data, StandardCharsets.UTF_8)` 走 REPLACE 语义，非法字节只会被替换成 `U+FFFD`，**永不抛异常**，所以外层 `catch (Exception)` 里的 GBK 回退永远不会执行。

**改动**：`LyricsLoader.java` 改用 `CharsetDecoder` + `CodingErrorAction.REPORT` 实现 `decodeStrict()`，形成「严格 UTF-8 → 严格 GBK → 宽松 UTF-8 兜底」三级链。

**验证**：
- JDK 单元测试 5/5 通过（UTF-8 / GBK / ASCII / 生僻字 / 非法字节不抛异常），并确认旧逻辑对 GBK 会产生 `U+FFFD`
- 真机：给通话录音配 GBK 编码 `.lrc`，四行中文完整正确显示

---

## 修复：使用已废弃的 framework PreferenceManager

**问题**：`MusicService` 引用 `android.preference.PreferenceManager`，而依赖里声明的是 `androidx.preference`（声明了却没用）。

**改动**：改为 `androidx.preference.PreferenceManager`。两者读写的是同一个文件 `<包名>_preferences`，**已有数据不受影响**。

**验证**：`-Xlint:deprecation` 确认该警告消失；续播位置跨重启仍能读到。

---

## 修复：搜索无结果时提示"本地没有音乐"

**问题**：搜索不到歌时显示「本地没有音乐，请先放入歌曲」，误导性强（排除目录后更常见）。

**改动**：`MainActivity.updateEmpty()` 按是否处于搜索状态选择文案，启用早已定义却未使用的 `search_no_result`。

**验证**：搜索「Weight」返回「没有找到相关歌曲」。

---

## 功能：歌词平滑滚动 + 上下边界淡入淡出

**问题**：`smoothScrollToLine()` 名为 smooth，实际调用 `scrollToPositionWithOffset()`，是瞬移没有动画；歌词区上下边缘无过渡，文字被硬切。

**改动**：`PlayerActivity.java` + 横竖屏布局
- 目标行已布局时改用 `smoothScrollBy(0, dy)` 逐像素动画；未布局（首帧/大跨度）仍瞬移保证立即可见
- 上下各加 90dp 渐变遮罩（背景色 → 透明），颜色在背景取色动画的同一 `ValueAnimator` 回调里同步刷新，避免切歌瞬间出现色带
- 遮罩复用 `GradientDrawable` 只改颜色，不每帧新建对象
- 歌词 RecyclerView 设 `overScrollMode="never"`，避免系统边缘辉光与遮罩叠加
- **顺带修正居中偏移**：原公式把 `paddingTop` 也算进偏移，高亮行整体下偏约 40dp，导致上下淡出区不对称

**验证**：竖屏/横屏截图确认淡出效果；连拍抓到滚动中间帧（高亮行未到中心）证明是动画而非瞬移。

---

## 功能：设置页

新增 `SettingsActivity`，四个分组：

| 分组 | 设置项 | 默认值 | 说明 |
| --- | --- | --- | --- |
| 播放行为 | 启动时自动播放 | 开 | 关闭后打开应用停在列表；**语音指令不受影响**（`handleCarIntent` 会强制置回 true） |
| 播放页 | 横屏歌词显示在左侧 | 关 | 运行时交换横屏布局两栏顺序，`layout_weight` 随之保留，歌词栏左右内边距镜像 |
| 曲库维护 | 扫描歌曲 | — | 见下一条 |
| 曲库扫描目录 | 目录排除列表 | 无排除 | 按前缀排除，关掉父目录会连带子目录 |

**验证**：逐项开关后核对 `ui_settings.xml` / `scan_settings.xml`；关闭自动播放后冷启动停在列表、无播放、不跳转。

---

## 功能：设置页「扫描歌曲」

**问题**：用 adb 覆盖音频文件后，MediaProvider 只留一条元数据残缺的记录（`artist`/`duration` 为 NULL），会被 `SongLoader` 的 `duration > 0` 过滤掉，表现为"歌从曲库消失"。此前的解决办法是连电脑执行 `content call --method scan_volume`。

**改动**：新增 `LibraryScanner`，在设置页提供按钮：
- 后台线程递归收集 MediaStore 已知歌曲目录（+ `/sdcard/Music`）下的音频文件
- 交给 `MediaScannerConnection.scanFile` 逐个重新解析
- 完成后 `SongLoader.invalidate()`，界面显示「扫描完成：N 个音频文件，曲库现有 M 首」

**验证**：把 `情侣装 - 许嵩.mp3` 原样推回设备制造 stub 记录（`artist=NULL, duration=NULL`），点扫描后恢复为 `title=情侣装, artist=许嵩, duration=201064`。

---

## 数据：补全《情歌 - 梁静茹》的内嵌歌词

**问题**：该 FLAC 的 `LYRICS` 字段为空（长度 0），只能走外部 `.lrc`，而旧匹配逻辑取到了《西海情歌》。

**改动**：用 jaudiotagger 把同名 `.lrc` 内容（924 字符、44 行带时间戳）写入 `FieldKey.LYRICS`，推回设备后执行媒体库重扫。
- 音频时长 260s、内嵌封面、文件大小（29,929,480 B）均未变化
- 原始文件备份在 PC 临时目录 `%TEMP%\qingge.flac`

**验证**：日志 `embedded song=情歌 len=924 hasTs=true -> useEmbedded=true`，`lines=43 lastTs=239130`；界面显示正确歌词。

**注意**：替换后 MediaStore 的 `_id` 由 `1000014016` 变为 `1000105498`，该曲的播放次数统计（按 song id 存）会归零。

---

## 开发过程中发现并修复的其他缺陷

这些都是实现新功能时自己引入、在真机验证阶段发现的，一并记录避免回归：

1. **`ScanSettings` 开关一点就崩溃**：`getExcludedDirs()` 在无排除项时返回 `Collections.emptySet()`（不可变），而 `setExcluded()` 直接 `add()` → `UnsupportedOperationException`。改为始终返回可变 `HashSet`。
2. **目录排除区分大小写导致漏排**：设备上同一目录存在 `Download/...` 与 `download/...` 两种写法（内部存储大小写不敏感）。改用 `regionMatches(true, ...)` 不区分大小写比较。
3. **扫描量翻倍**：`LibraryScanner` 按字符串去重，把大小写变体当成两个根目录各遍历一遍，报告 2450 个文件。改用 `TreeSet(String.CASE_INSENSITIVE_ORDER)` 去重，变为 1230 个。
4. **设置页目录路径无法区分**：`ellipsize="middle"` 把 `Download/Sync/music` 和 `Download/Sync/music/new` 截成同一个样子。改为去掉 `/storage/emulated/0/` 前缀显示。

---

## 签名

新增 release 签名并接入构建：
- `keystore/carmusic-release.jks`（RSA 2048，alias `carmusic`，SHA1 `4E:98:13:5A:FA:9F:67:5A:6A:EA:21:6B:89:B4:96:25:C0:E6:E7:20`）
- `keystore.properties` 存放路径与密码
- `app/build.gradle` 从 properties 读取；文件缺失时仍可构建（产出未签名包）
- `DEV_ENV.md` 第 7 节记录

**验证**：`keytool -printcert -jarfile` 确认证书 SHA1 与密钥库一致。

> 密钥库丢失将无法升级已装机版本，务必单独备份。

---

## 已确认、暂不处理的事项

避免以后重复调研：

1. **Jovi 无法按小迪的方式接入**——详见上文《调研：vivo Jovi 语音为什么接不进来自定义音乐 App》。要点：按"默认音乐源"包名精确路由、显式组件深链、无隐式 Intent 因而无选择框；控制类指令由 `FeatureService.handleMediaButtonEventTransfer` 按包名转发，**不是**发给当前活跃的 MediaSession（实测本 App 正在播放时，语音"暂停"停的仍是 QQ 音乐）。
2. **内嵌歌词优先于外部 .lrc**，这是刻意设计。凡内嵌了带时间戳歌词的歌，改 `.lrc` 不生效。
3. **曲库唯一"暂无歌词"的歌是 `My Soul - July`**，本身即纯音乐，属正常。此前"12 首没有歌词"的说法是统计口径错误（把所有歌当成同一目录比对，且忽略了内嵌歌词）。
4. **`versionCode` / `versionName`**：自 1.0.1 起开始跟随迭代递增（当前 2 / 1.0.1）。

---

## 验证基线（改动后回归用）

```bat
build.bat clean assembleDebug assembleRelease
```

预期：`BUILD SUCCESSFUL`，产物 `app-debug.apk`（约 3.4 MB）/ `app-release.apk`（约 2.9 MB，已签名）。

真机回归要点：
- 打开应用 → 恢复上次曲目与歌内位置
- 歌词：中文正常、高亮行居中、上下淡出、滚动有动画
- 设置页四项均可切换并持久化
- 扫描歌曲：能修复 stub 记录
