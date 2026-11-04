# 点点音悦

一个**本地音乐播放器**，为车载大屏（横屏 4:3）设计，同时适配手机竖屏。

不做音乐库管理、不联网、不上传数据——扫描设备上已有的音频文件，把歌词、封面、续播、语音点歌这些"车上真正会用到的"做扎实。

## 功能

**播放**
- 本地音频扫描（MediaStore），支持按目录排除（铃声、录音等）
- 断点续播：记住上次的曲目与**歌内进度**，断电/杀进程也不丢（5 秒定时落盘）
- 三种排序：按名称 / 按最近添加 / 按播放次数，可在设置里改默认值
- 播放模式：顺序 → 单曲循环 → 随机循环

**播放页**
- 封面取色生成动态背景，整页渐变过渡
- 歌词：优先内嵌标签，其次同名 `.lrc`（UTF-8 / GBK 自动识别），逐行平滑滚动、上下边界淡入淡出、当前行高亮放大
- 超长歌词自动换行撑高，不会被裁
- 横竖屏两套布局：横屏为「封面左 / 歌词右」（可在设置里镜像），进度条横跨屏幕最底部
- 竖屏状态栏跟随页面配色，横屏全屏

**列表**
- 当前播放行：序号位置换成跳动频谱 + 整行高亮 + 歌名变色

**车机语音（小迪 / 酷我协议）**
- 接收 `cn.kuwo.kwmusicauto.action.*` 系列 action，支持"播放某首歌"
- 语音切歌**不打断当前界面**；仅冷启动时自动进入播放页

## 环境要求

- Android 5.0（API 21）及以上
- 编译环境见 [DEV_ENV.md](DEV_ENV.md)：**JDK 8** + Gradle 6.1.1 + AGP 4.0.1 + compileSdk 29

## 构建

```bat
build.bat assembleDebug
```

或直接用 Gradle Wrapper：

```bash
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

### 签名（可选）

Release 签名从项目根目录的 `keystore.properties` 读取，避免密码进入仓库：

```bash
cp keystore.properties.example keystore.properties
# 然后填写自己的密钥库路径、alias 与密码
```

文件不存在时 `assembleRelease` 仍可构建，只是产出未签名包。

## 项目结构

```
app/src/main/java/top/juluo/ddmusic/
├── activity/     主界面、播放页、设置页及其二级页、语音中转页
├── service/      MusicService（前台服务 + MediaSession）
├── receiver/     酷我车机协议广播接收器
├── lyrics/       歌词解析（LRC 时间轴）与匹配加载
├── loader/       曲库查询与目录统计
├── ui/           列表适配器、歌词适配器
├── views/        自定义 View（正方形封面容器、播放频谱指示器）
└── util/         设置项、扫描器、颜色工具等
```

## 已知限制

- **vivo Jovi 语音**：vivo 的音乐语音控制按"默认音乐源"包名精确路由、走私有深链（`qqmusic://`），第三方应用无法参与，也复刻不了小迪那种"弹框选 App"。详见 [CHANGELOG](CHANGELOG.md#调研vivo-jovi-语音为什么接不进来自定义音乐-app)。
- 内嵌歌词**优先于**外部 `.lrc`，这是刻意设计：内嵌通常与音频来自同一来源，不会错配。
- 换包名等于新应用，旧的设置与播放次数不会迁移。

## 文档

- [CHANGELOG.md](CHANGELOG.md) —— 每次已验证的改动：现象 / 根因 / 改动 / 验证
- [DEV_ENV.md](DEV_ENV.md) —— 从零搭建编译环境的完整步骤

## 开发者

点点、大肥鱼
