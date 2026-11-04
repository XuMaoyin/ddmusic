package top.juluo.ddmusic.lyrics;

/**
 * 一行带时间戳的歌词。
 */
public class LyricLine {
    public final long timeMs;
    public final String text;

    public LyricLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text;
    }
}
