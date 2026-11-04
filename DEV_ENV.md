# 本地开发环境说明（Windows）

本文档记录 CarMusic（点点音悦车机版）在本机的构建环境。环境已搭建完成并验证通过。

## 1. 版本矩阵

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Gradle | 6.1.1 | 由 `gradle/wrapper` 锁定 |
| Android Gradle Plugin | 4.0.1 | `build.gradle` 中声明 |
| JDK | Oracle JDK **8u144** | **必须是 JDK 8**：Gradle 6.1.1 只支持到 Java 13，AGP 4.0.1 面向 Java 8 |
| compileSdk / targetSdk | 29 | |
| minSdk | 21 | |
| build-tools | 29.0.3 | |
| 构建系统 | Gradle Wrapper | 无需本机安装 Gradle |

> 注意：**不要**把 `JAVA_HOME` 指向 JDK 11/17。本项目在 JDK 8 上验证通过，换高版本会触发 Gradle 6.1.1 的兼容性问题。

## 2. 安装位置

| 项 | 路径 |
| --- | --- |
| JDK 8 | `C:\Program Files\Java\jdk1.8.0_144` |
| Android SDK | `C:\Users\xmm\AppData\Local\Android\Sdk` |
| Gradle 发行版缓存 | `C:\Users\xmm\.gradle\wrapper\dists\gradle-6.1.1-all` |
| 依赖缓存 | `C:\Users\xmm\.gradle\caches`（约 212 MB） |

Android SDK 已安装组件：

```
platform-tools/          adb 1.0.41 (37.0.1)
platforms/android-29/
build-tools/29.0.3/
cmdline-tools/8.0/       sdkmanager 3.0（兼容 JDK 8，用这个装 SDK 组件）
cmdline-tools/latest/    sdkmanager 新版（需要 JDK 17，本机不具备，勿用）
```

## 3. 环境变量（已写入用户级 `HKCU\Environment`）

```
JAVA_HOME        = C:\Program Files\Java\jdk1.8.0_144
ANDROID_HOME     = C:\Users\xmm\AppData\Local\Android\Sdk
ANDROID_SDK_ROOT = C:\Users\xmm\AppData\Local\Android\Sdk
PATH             += %JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools
```

> 这些变量对**新开的进程**生效。已经运行的终端 / IDE / DSH 需要重启后才会读到。

## 4. 构建

任选一种：

```bat
rem 方式一：一键脚本（推荐，内部已固定 JDK 8 与 SDK 路径）
build.bat                       rem 等价 assembleDebug
build.bat clean assembleDebug
build.bat assembleRelease
build.bat installDebug          rem 装到已连接的车机

rem 方式二：已重启过终端，环境变量生效
gradlew.bat assembleDebug

rem 方式三：临时手动指定
set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_144"
set "PATH=%JAVA_HOME%\bin;%PATH%"
gradlew.bat assembleDebug
```

产物：

```
app\build\outputs\apk\debug\app-debug.apk                   3.4 MB
app\build\outputs\apk\release\app-release-unsigned.apk      2.8 MB（未签名）
```

## 5. 本次为搭建环境所做的改动

> 全部代码与功能改动（含问题根因、验证方式）记录在 **[CHANGELOG.md](CHANGELOG.md)**，本节只列环境相关文件。

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `local.properties` | **新增** | 指向本机 SDK：`sdk.dir=C\:\\Users\\xmm\\AppData\\Local\\Android\\Sdk`。属机器相关配置，不应提交到版本库 |
| `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl` 改为腾讯云镜像 | 官方 `services.gradle.org` 会重定向到 `downloads.gradle-dn.com`，该域名在国内无法解析，导致 wrapper 下载 `SocketTimeoutException` |
| `build.bat` | **新增** | 一键构建，固定工具链路径，避免 `JAVA_HOME` 未生效时构建失败 |
| `DEV_ENV.md` | **新增** | 本文档 |

镜像地址：

```
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-6.1.1-all.zip
```

如后续需要恢复官方源，把上面这行换回 `https\://services.gradle.org/distributions/gradle-6.1.1-all.zip`（前提是网络能访问其 CDN）。

## 6. 已知注意点

- **装 SDK 组件要用 `cmdline-tools\8.0\bin\sdkmanager.bat`**，并显式传 `--sdk_root`：
  ```bat
  set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_144"
  "C:\Users\xmm\AppData\Local\Android\Sdk\cmdline-tools\8.0\bin\sdkmanager.bat" --sdk_root="C:\Users\xmm\AppData\Local\Android\Sdk" --list
  ```
  `cmdline-tools\latest` 是 class 文件版本 61（Java 17）编译的，在 JDK 8 下会报 `UnsupportedClassVersionError`。
- **依赖仓库**：`build.gradle` 里已配置阿里云镜像 + Google Maven + Maven Central，本机实测均可访问（Google Maven、repo1.maven.org 返回 200）。
- **平台签名**：车机通常不带 debug 证书信任，正式装车需要配置 `signingConfigs` 并使用平台/自有签名；当前 release 产物是 `-unsigned`。
- **连接车机**：`adb devices` 目前无设备；车机需开启 ADB 调试（部分车机需工程模式 / USB 双公头线）。

## 7. release 签名

已生成一套本机使用的 release 签名，构建时会自动应用。

| 项 | 值 |
| --- | --- |
| 密钥库 | `keystore/carmusic-release.jks`（RSA 2048，有效期 10000 天） |
| alias | `carmusic` |
| 证书 SHA1 | `4E:98:13:5A:FA:9F:67:5A:6A:EA:21:6B:89:B4:96:25:C0:E6:E7:20` |
| 密码 | 不随仓库分发，见本地 `keystore.properties`（该文件已在 `.gitignore` 中排除） |

`app/build.gradle` 从项目根目录的 `keystore.properties` 读取签名配置，**文件不存在时 release 仍可构建，只是产出未签名包**。

产出：`app/build/outputs/apk/release/app-release.apk`（已签名）。

> **务必备份 `keystore/carmusic-release.jks` 与 `keystore.properties`。**
> 密钥库一旦丢失，已安装的 release 版本将无法升级——只能用新密钥重新签名，而 Android 不允许跨签名覆盖安装，必须先卸载。
> 这两个文件含密码，不要提交到公开仓库。

**签名不通用**：debug 包与 release 包签名不同，不能互相覆盖安装。从 debug 换到 release（或反之）都要先卸载。

## 8. 验证记录

| 检查项 | 结果 |
| --- | --- |
| `gradlew.bat --version` | Gradle 6.1.1 / JVM 1.8.0_144 / Windows 10 amd64 |
| `assembleDebug` | BUILD SUCCESSFUL |
| `clean assembleDebug assembleRelease` | BUILD SUCCESSFUL in 1m 2s（47 tasks） |
| APK badging | `top.juluo.ddmusic`，label「点点音悦车机版」，minSdk 21，targetSdk 29，compileSdk 29 |
| `adb version` | 1.0.41 |
