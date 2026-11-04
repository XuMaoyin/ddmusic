@echo off
rem ============================================================
rem  One-click build helper for CarMusic / ddmusic
rem  Purpose: pin the toolchain that this project needs
rem           (Gradle 6.1.1 + AGP 4.0.1 require JDK 8),
rem           so the build works even when the shell has no
rem           JAVA_HOME / ANDROID_HOME.
rem
rem  Usage:
rem    build.bat                     -> assembleDebug
rem    build.bat clean assembleDebug
rem    build.bat assembleRelease
rem    build.bat installDebug        -> build + install to a
rem                                     connected car head unit
rem ============================================================

set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_144"
set "ANDROID_HOME=C:\Users\xmm\AppData\Local\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK 8 not found at "%JAVA_HOME%".
    echo         Edit build.bat and point JAVA_HOME at your JDK 8 install.
    exit /b 1
)

if "%~1"=="" (
    call "%~dp0gradlew.bat" assembleDebug
) else (
    call "%~dp0gradlew.bat" %*
)
exit /b %ERRORLEVEL%
