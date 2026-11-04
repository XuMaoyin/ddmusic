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
    /** 横屏播放页歌词是否放在左侧（默认右侧，与原有布局一致） */
    private static final String KEY_LYRICS_ON_LEFT = "lyrics_on_left";
    /** 曲库列表排序方式，取值同 SongLoader.SORT_* */
    private static final String KEY_SORT_MODE = "sort_mode";

    /** 默认开：保持原有"打开即播放并进入播放页"的车机行为 */
    public static boolean isAutoPlayOnLaunch(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_PLAY_ON_LAUNCH, true);
    }

    public static void setAutoPlayOnLaunch(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_PLAY_ON_LAUNCH, enabled).apply();
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
