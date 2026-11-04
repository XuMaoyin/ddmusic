package top.juluo.ddmusic.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 LRC 解析器：解析 [mm:ss.xx] 时间戳歌词，支持 [offset:] 与一行多时间戳。
 */
public class LrcParser {

    private static final Pattern LRC_LINE_PATTERN = Pattern.compile("((?:\\[.*?\\])+)(.*)");
    private static final Pattern LRC_TIME_PATTERN = Pattern.compile("\\[(\\d+):(\\d{2}(?:\\.\\d+)?)\\]");
    private static final Pattern LRC_ATTRIBUTE_PATTERN = Pattern.compile("\\[(\\D+):(.+)\\]");

    private static final float SECONDS_TO_MS = 1000f;
    private static final int MINUTES_TO_MS = 60000;

    /** 解析 LRC 文本，返回按时间升序的歌词行；不是同步歌词（无时间戳）返回空列表。 */
    public static List<LyricLine> parse(String data) {
        List<LyricLine> result = new ArrayList<>();
        if (data == null || data.isEmpty()) return result;

        int offset = 0;
        String[] lines = data.split("\r?\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher attrMatcher = LRC_ATTRIBUTE_PATTERN.matcher(line);
            if (attrMatcher.find()) {
                try {
                    String attr = attrMatcher.group(1).toLowerCase().trim();
                    if ("offset".equals(attr)) {
                        offset = Integer.parseInt(attrMatcher.group(2).trim());
                    }
                } catch (Exception ignored) {
                }
                continue;
            }

            Matcher matcher = LRC_LINE_PATTERN.matcher(line);
            if (matcher.find()) {
                String time = matcher.group(1);
                String text = matcher.group(2);
                if (text.trim().isEmpty()) continue;

                Matcher timeMatcher = LRC_TIME_PATTERN.matcher(time);
                while (timeMatcher.find()) {
                    try {
                        int m = Integer.parseInt(timeMatcher.group(1));
                        float s = Float.parseFloat(timeMatcher.group(2));
                        int ms = (int) (s * SECONDS_TO_MS) + m * MINUTES_TO_MS + offset;
                        if (ms >= 0) {
                            result.add(new LyricLine(ms, text.trim()));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        Collections.sort(result, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        return result;
    }
}
