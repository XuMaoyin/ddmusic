package top.juluo.ddmusic.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 界面与播放行为设置（与曲库扫描设置 {@link ScanSettings} 分开存放）。
 */
public final class UiSettings {

    private static final String PREF = "ui_settings";

    /** 启动时是否自动播放并跳转播放页 */
    private static final String KEY_AUTO_PLAY_ON_LAUNCH = "auto_play_on_launch";

    /** 是否在通知栏常驻播放控制（前台服务通知）。默认开。 */
    private static final String KEY_SHOW_PLAY_NOTIFICATION = "show_play_notification";

    /** 语音指令调试 Toast（收到指令 / 命中几首）。默认开。 */
    private static final String KEY_VOICE_TOAST = "voice_toast";
    /** 横屏播放页歌词是否放在左侧（默认右侧，与原有布局一致） */
    private static final String KEY_LYRICS_ON_LEFT = "lyrics_on_left";
    /** 曲库列表排序方式，取值同 SongLoader.SORT_* */
    private static final String KEY_SORT_MODE = "sort_mode";
    /** 歌词字号缩放，0.6~2.0，默认 1.0 */
    private static final String KEY_LYRICS_FONT_SCALE = "lyrics_font_scale";
    /** 歌词行距额外增量（dp），0~48，叠加在默认留白上 */
    private static final String KEY_LYRICS_SPACING_EXTRA_DP = "lyrics_spacing_extra_dp";

    /** 默认开：保持原有"打开即播放并进入播放页"的车机行为 */
    public static boolean isAutoPlayOnLaunch(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_PLAY_ON_LAUNCH, true);
    }

    public static void setAutoPlayOnLaunch(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_PLAY_ON_LAUNCH, enabled).apply();
    }

    public static boolean isShowPlayNotification(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_PLAY_NOTIFICATION, true);
    }

    public static void setShowPlayNotification(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SHOW_PLAY_NOTIFICATION, enabled).apply();
    }

    public static boolean isVoiceToast(Context context) {
        return prefs(context).getBoolean(KEY_VOICE_TOAST, true);
    }

    public static void setVoiceToast(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_VOICE_TOAST, enabled).apply();
    }

    /** 默认关：保持原有"封面在左、歌词在右"的布局 */
    public static boolean isLyricsOnLeft(Context context) {
        return prefs(context).getBoolean(KEY_LYRICS_ON_LEFT, false);
    }

    public static void setLyricsOnLeft(Context context, boolean left) {
        prefs(context).edit().putBoolean(KEY_LYRICS_ON_LEFT, left).apply();
    }

    /**
     * 曲库列表排序方式。取值与 {@link top.juluo.ddmusic.loader.SongLoader} 的
     * SORT_NAME / SORT_DATE_ADDED / SORT_PLAY_COUNT 一致。
     *
     * <p>初始默认「按最近添加」：新车机装好曲库后，用户通常最想先看到刚拷进去的歌。
     * 主界面工具栏的排序按钮与设置页改的是同一个值，避免两个入口互相打架。
     */
    public static int getSortMode(Context context) {
        return prefs(context).getInt(KEY_SORT_MODE,
                top.juluo.ddmusic.loader.SongLoader.SORT_DATE_ADDED);
    }

    public static void setSortMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_SORT_MODE, mode).apply();
    }

    public static float getLyricsFontScale(Context context) {
        float v = prefs(context).getFloat(KEY_LYRICS_FONT_SCALE, 1f);
        if (v < 0.6f) v = 0.6f;
        if (v > 2f) v = 2f;
        return v;
    }

    public static void setLyricsFontScale(Context context, float scale) {
        if (scale < 0.6f) scale = 0.6f;
        if (scale > 2f) scale = 2f;
        prefs(context).edit().putFloat(KEY_LYRICS_FONT_SCALE, scale).apply();
    }

    public static int getLyricsSpacingExtraDp(Context context) {
        int v = prefs(context).getInt(KEY_LYRICS_SPACING_EXTRA_DP, 0);
        if (v < 0) v = 0;
        if (v > 48) v = 48;
        return v;
    }

    public static void setLyricsSpacingExtraDp(Context context, int dp) {
        if (dp < 0) dp = 0;
        if (dp > 48) dp = 48;
        prefs(context).edit().putInt(KEY_LYRICS_SPACING_EXTRA_DP, dp).apply();
    }

    /** 排序方式的显示名资源 id，供主界面 Toast 与设置页共用 */
    public static int getSortLabelRes(int mode) {
        switch (mode) {
            case top.juluo.ddmusic.loader.SongLoader.SORT_NAME:
                return top.juluo.ddmusic.R.string.settings_sort_name;
            case top.juluo.ddmusic.loader.SongLoader.SORT_PLAY_COUNT:
                return top.juluo.ddmusic.R.string.settings_sort_play_count;
            case top.juluo.ddmusic.loader.SongLoader.SORT_DATE_ADDED:
            default:
                return top.juluo.ddmusic.R.string.settings_sort_date;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private UiSettings() {
    }
}
