package top.juluo.ddmusic.util;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import top.juluo.ddmusic.model.Song;

/**
 * 把小迪（com.byd.autovoice）需要的“当前播放状态”上报回去。
 *
 * <p>小迪在 <code>com.byd.action.NOTIFY_AUTOVOICE_CURRENT_STATE</code> 上注册了动态接收器，
 * 车机自带的音乐应用（酷我/酷狗等）播放状态变化时都会发这条广播。
 * 第三方播放器如果不上报，小迪可能拿不到“当前在播什么”，
 * 表现之一就是语音“下一曲/暂停”回“当前应用只支持手动操作哦”。
 *
 * <p>字段名沿用系统里的写法（<code>EXTRA_PALY_STATE</code> 的 PALY 是原样拼写，不要改）。
 * 这套字段是抓包/反编译推测的，实测时以 logcat 里小迪是否响应为准。
 */
public final class VoiceStateReporter {

    private static final String TAG = "VoiceState";
    private static final String ACTION_NOTIFY = "com.byd.action.NOTIFY_AUTOVOICE_CURRENT_STATE";
    private static final String PKG_AUTOVOICE = "com.byd.autovoice";

    /** 避免同一首歌/同一状态被 updateMediaSessionState 重复触发时刷屏 */
    private static String sLastKey;

    private VoiceStateReporter() {
    }

    public static void report(Context context, boolean playing, Song song) {
        if (context == null || song == null) return;
        String key = (playing ? "play" : "pause") + "|" + song.title + "|" + song.artistName;
        if (key.equals(sLastKey)) return;
        sLastKey = key;
        try {
            Intent intent = new Intent(ACTION_NOTIFY);
            intent.setPackage(PKG_AUTOVOICE);
            intent.putExtra("EXTRA_PACKAGE_NAME", context.getPackageName());
            intent.putExtra("EXTRA_PALY_STATE", playing ? 1 : 0);
            intent.putExtra("EXTRA_PROGRAM_NAME", song.title);
            intent.putExtra("EXTRA_PROGRAM_TAGS", song.artistName);
            intent.putExtra("EXTRA_APPLICATION_TYPE", 1);
            context.sendBroadcast(intent);
            Log.d(TAG, "已上报小迪: " + key);
        } catch (Exception e) {
            Log.w(TAG, "上报小迪失败", e);
        }
    }
}
