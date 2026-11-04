package top.juluo.ddmusic.util;

import java.util.concurrent.TimeUnit;

public class MusicUtil {

    /** 秒/毫秒 -> mm:ss */
    public static String formatDuration(int durationMs) {
        long totalSec = TimeUnit.MILLISECONDS.toSeconds(durationMs);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format(java.util.Locale.CHINA, "%d:%02d", min, sec);
    }

    private MusicUtil() {
    }
}
