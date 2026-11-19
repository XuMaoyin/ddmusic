package top.juluo.ddmusic.util;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import top.juluo.ddmusic.model.Song;

/**
 * 仪表盘媒体信息上报（BYD DiLink 私有通道），两条链路并行：
 *
 * <p>A. MultiMediaControl 链路（AudioManager 隐藏扩展）：
 * setPlayMediaInfo/setPlayState/setPlayProgress，mode 固定 1(MODE_MUSIC)。
 * 注意系统校验 mode==mMediaMode，须先 setMediaType(8=LOCAL_AUDIO) 使 mediaMode=1。
 *
 * <p>B. 仪表盘直推链路（BYDAutoInstrumentDevice / BYDAutoAudioDevice），
 * 与媒体中心 MCUToolsImpl 相同的协议：
 * <ul>
 *   <li>sendMusicInfo(UTF-16LE 字节, ≤96B) —— 仪表盘歌名</li>
 *   <li>sendSingerName(歌手) —— 歌手名</li>
 *   <li>sendMusicState(1 播放 / 2 暂停)</li>
 *   <li>sendMusicPlaybackProgress(百分比 0-100)</li>
 *   <li>sendAudioTotalTime / sendAudioPlayingTime(时,分,秒)</li>
 *   <li>sendMusicSource(1=本地) —— 仅首次上报时发一次</li>
 * </ul>
 * 直推链路在系统侧要求调用者是当前音频焦点持有者（播放中天然满足）。
 *
 * <p>全部反射调用，任何失败静默降级停用，不影响播放。
 */
public final class InstrumentReporter {

    private static final String TAG = "InstrumentReporter";
    private static final int MODE_MUSIC = 1;
    private static final int STATE_PLAY = 0;
    private static final int STATE_PAUSE = 1;
    private static final int STATE_STOP = 2;
    /** MULTIMEDIA_TYPE_LOCAL_AUDIO：系统据此推导 mediaMode=1(music) */
    private static final int MEDIA_TYPE_LOCAL_AUDIO = 8;
    /** 仪表盘音乐源：1=本地/USB（媒体中心 MCUToolsImpl 的映射） */
    private static final int INSTRUMENT_SOURCE_LOCAL = 1;
    /** 仪表盘状态：1 播放 / 2 暂停 */
    private static final int INSTR_STATE_PLAY = 1;
    private static final int INSTR_STATE_PAUSE = 2;
    /** 歌名字节上限（与媒体中心一致：96 字节 UTF-16LE ≈ 48 个汉字） */
    private static final int TITLE_BYTE_LIMIT = 96;

    private static boolean sUnavailable = false;
    // A 链路：AudioManager 扩展
    private static Class<?> sMediaInfoClass;
    private static Method sSetInfo;
    private static Method sSetState;
    private static Method sSetProgress;
    private static Method sSetType;
    private static Method sGetType;
    // B 链路：仪表盘 / 音频设备
    private static Object sInstrumentDevice;
    private static Method sSendMusicInfo;
    private static Method sSendMusicState;
    private static Method sSendMusicProgress;
    private static Method sSendMusicSource;
    private static Object sAudioDevice;
    private static Method sSendSinger;
    private static Method sSendTotalTime;
    private static Method sSendPlayingTime;
    private static boolean sSourceSent = false;
    /** 最近一次待送达仪表的歌曲；B 链路被"非焦点持有者"丢弃时在秒级心跳里补送 */
    private static Song sPendingSong;
    private static boolean sInfoDelivered = false;
    private static Method sGetFocusPkg;

    private InstrumentReporter() {
    }

    private static boolean ensure(Context ctx) {
        if (sUnavailable) return false;
        if (sSetInfo != null && sInstrumentDevice != null) return true;
        try {
            // A 链路
            sMediaInfoClass = Class.forName("android.hardware.bydauto.multimedia.MediaInfo");
            sSetInfo = AudioManager.class.getMethod("setPlayMediaInfo", int.class, sMediaInfoClass);
            sSetState = AudioManager.class.getMethod("setPlayState", int.class, int.class);
            sSetProgress = AudioManager.class.getMethod("setPlayProgress", int.class, int.class);
            sSetType = AudioManager.class.getMethod("setMediaType", int.class);
            sGetType = AudioManager.class.getMethod("getMediaType");
            // B 链路：仪表盘
            Class<?> instCls = Class.forName("android.hardware.bydauto.instrument.BYDAutoInstrumentDevice");
            sInstrumentDevice = instCls.getMethod("getInstance", Context.class)
                    .invoke(null, ctx.getApplicationContext());
            sSendMusicInfo = instCls.getMethod("sendMusicInfo", byte[].class);
            sSendMusicState = instCls.getMethod("sendMusicState", int.class);
            sSendMusicProgress = instCls.getMethod("sendMusicPlaybackProgress", int.class);
            sSendMusicSource = instCls.getMethod("sendMusicSource", int.class);
            // B 链路：音频设备（歌手/时间）
            Class<?> audioCls = Class.forName("android.hardware.bydauto.audio.BYDAutoAudioDevice");
            sAudioDevice = audioCls.getMethod("getInstance", Context.class)
                    .invoke(null, ctx.getApplicationContext());
            sSendSinger = audioCls.getMethod("sendSingerName", String.class);
            sSendTotalTime = audioCls.getMethod("sendAudioTotalTime", int.class, int.class, int.class);
            sSendPlayingTime = audioCls.getMethod("sendAudioPlayingTime", int.class, int.class, int.class);
            Log.i(TAG, "BYD instrument API found (both paths)");
            return true;
        } catch (Throwable t) {
            Throwable cause = t;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            Log.i(TAG, "BYD instrument API not available: " + t + " | cause: " + cause, cause);
            // 只禁用出问题的链路：A 链路与 B 链路独立降级
            if (sSetInfo == null && sInstrumentDevice == null) {
                sUnavailable = true;
            }
            return sSetInfo != null || sInstrumentDevice != null;
        }
    }

    /** 当前音频焦点是否属于本 App（delegate 的 isFocusOwner 同款判定） */
    private static boolean focusIsOurs(Context ctx) {
        try {
            if (sGetFocusPkg == null) {
                sGetFocusPkg = AudioManager.class.getMethod("getCurrentAudioFocusPackage");
            }
            Object pkg = sGetFocusPkg.invoke(audioManager(ctx));
            return ctx.getPackageName().equals(pkg);
        } catch (Throwable t) {
            return false;
        }
    }

    private static AudioManager audioManager(Context ctx) {
        return (AudioManager) ctx.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    /** 确保系统媒体类型为 LOCAL_AUDIO，否则 A 链路上报被丢弃（mediaMode=-1） */
    private static void ensureMediaType(Context ctx) {
        if (sSetType == null || sGetType == null) return;
        try {
            Object cur = sGetType.invoke(audioManager(ctx));
            if (!(cur instanceof Integer) || (Integer) cur != MEDIA_TYPE_LOCAL_AUDIO) {
                sSetType.invoke(audioManager(ctx), MEDIA_TYPE_LOCAL_AUDIO);
                Log.i(TAG, "mediaType set to LOCAL_AUDIO (was " + cur + ")");
            }
        } catch (Throwable t) {
            Log.w(TAG, "ensureMediaType failed: " + root(t));
        }
    }

    /** 歌曲切换时上报：歌名/歌手/专辑/时长（A+B 双链路） */
    public static void reportSong(Context ctx, Song song) {
        if (song == null || !ensure(ctx)) return;
        ensureMediaType(ctx);
        // A 链路：MultiMediaControl
        try {
            if (sSetInfo == null) throw new IllegalStateException("A path unavailable");
            Object info = sMediaInfoClass.newInstance();
            setField(info, "fileName", song.title == null ? "" : song.title);
            setField(info, "artistName", song.artistName == null ? "" : song.artistName);
            setField(info, "albumName", song.albumName == null ? "" : song.albumName);
            setField(info, "duration", song.duration);
            sSetInfo.invoke(audioManager(ctx), MODE_MUSIC, info);
        } catch (Throwable t) {
            Log.w(TAG, "setPlayMediaInfo failed: " + root(t));
        }
        // B 链路：仪表盘直推（delegate 要求调用时持有音频焦点，否则静默丢弃）
        sPendingSong = song;
        sInfoDelivered = false;
        sendDirectInfoIfFocused(ctx);
    }

    /** 焦点在手才直推仪表；成功后置位，避免心跳重复发送 */
    private static void sendDirectInfoIfFocused(Context ctx) {
        if (sInfoDelivered || sPendingSong == null) return;
        if (!focusIsOurs(ctx)) return;
        Song song = sPendingSong;
        try {
            if (!sSourceSent) {
                sSendMusicSource.invoke(sInstrumentDevice, INSTRUMENT_SOURCE_LOCAL);
                sSourceSent = true;
            }
            sSendMusicInfo.invoke(sInstrumentDevice, titleBytes(song.title));
            if (song.artistName != null) {
                sSendSinger.invoke(sAudioDevice, song.artistName);
            }
            int durSec = Math.max(0, song.duration / 1000);
            sSendTotalTime.invoke(sAudioDevice, durSec / 3600, durSec % 3600 / 60, durSec % 60);
            sInfoDelivered = true;
            Log.i(TAG, "instrument info delivered: " + song.title);
        } catch (Throwable t) {
            Log.w(TAG, "instrument direct report failed: " + root(t));
        }
    }

    private static String root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return String.valueOf(c);
    }

    /** 播放/暂停状态（A+B 双链路） */
    public static void reportState(Context ctx, boolean playing) {
        if (!ensure(ctx)) return;
        ensureMediaType(ctx);
        try {
            if (sSetState != null) {
                sSetState.invoke(audioManager(ctx), MODE_MUSIC, playing ? STATE_PLAY : STATE_PAUSE);
            }
        } catch (Throwable t) {
            Log.w(TAG, "setPlayState failed: " + root(t));
        }
        if (focusIsOurs(ctx)) {
            try {
                sSendMusicState.invoke(sInstrumentDevice, playing ? INSTR_STATE_PLAY : INSTR_STATE_PAUSE);
            } catch (Throwable t) {
                Log.w(TAG, "sendMusicState failed: " + root(t));
            }
        }
    }

    /** 停止（服务销毁时） */
    public static void reportStop(Context ctx) {
        if (!ensure(ctx)) return;
        try {
            if (sSetState != null) sSetState.invoke(audioManager(ctx), MODE_MUSIC, STATE_STOP);
            if (sSendMusicState != null) sSendMusicState.invoke(sInstrumentDevice, INSTR_STATE_PAUSE);
        } catch (Throwable t) {
            Log.w(TAG, "reportStop failed: " + t);
        }
    }

    /** 播放进度：A 链路秒数，B 链路百分比 + 时分秒 */
    public static void reportProgress(Context ctx, int positionSec, int durationSec) {
        if (!ensure(ctx)) return;
        try {
            if (sSetProgress != null) sSetProgress.invoke(audioManager(ctx), MODE_MUSIC, positionSec);
        } catch (Throwable t) {
            Log.w(TAG, "setPlayProgress failed: " + root(t));
        }
        sendDirectInfoIfFocused(ctx);
        if (focusIsOurs(ctx)) {
            try {
                int percent = durationSec > 0 ? Math.round(positionSec * 100f / durationSec) : 0;
                sSendMusicProgress.invoke(sInstrumentDevice, Math.max(0, Math.min(100, percent)));
                sSendPlayingTime.invoke(sAudioDevice,
                        positionSec / 3600, positionSec % 3600 / 60, positionSec % 60);
            } catch (Throwable t) {
                Log.w(TAG, "instrument progress failed: " + root(t));
            }
        }
    }

    /** 与媒体中心一致：UTF-16LE，超 96 字节截断 */
    private static byte[] titleBytes(String title) {
        String t = title == null ? "" : title;
        try {
            byte[] b = t.getBytes("UnicodeLittleUnmarked");
            if (b.length > TITLE_BYTE_LIMIT) {
                byte[] cut = new byte[TITLE_BYTE_LIMIT];
                System.arraycopy(b, 0, cut, 0, TITLE_BYTE_LIMIT);
                return cut;
            }
            return b;
        } catch (Exception e) {
            return t.getBytes();
        }
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getField(name);
        f.set(obj, value);
    }
}
