package top.juluo.ddmusic.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 歌曲播放次数统计：SharedPreferences 持久化，key=songId，value=int。
 */
public class PlayCountManager {

    private static final String PREF = "play_counts";

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static int getCount(Context ctx, long songId) {
        return sp(ctx).getInt(String.valueOf(songId), 0);
    }

    public static void increment(Context ctx, long songId) {
        SharedPreferences.Editor ed = sp(ctx).edit();
        ed.putInt(String.valueOf(songId), sp(ctx).getInt(String.valueOf(songId), 0) + 1);
        ed.apply();
    }
}
