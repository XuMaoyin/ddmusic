package top.juluo.ddmusic.lyrics;

import android.content.Context;

import top.juluo.ddmusic.model.Song;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 歌词数据来源：优先内嵌歌词（需含时间戳），否则在同目录按优先级挑选 .lrc/.txt。
 *
 * <p>为什么不再用"包含匹配"：旧实现把标题拼成 {@code .*<标题>.*\.lrc} 去找文件，
 * 再取 {@link File#listFiles()} 返回顺序里的第一个。目录顺序由文件系统决定，
 * 于是「情歌」会命中「西海情歌」「小情歌」「单身情歌」「情歌悠扬」等任意一个，
 * 出现"播这首歌、显示那首歌的歌词"。
 *
 * <p>现在的挑选策略是分层的，层内也按确定规则排序，结果与目录顺序无关：
 * <ol>
 *   <li>与音频文件完全同名（情歌 - 梁静茹.flac → 情歌 - 梁静茹.lrc）</li>
 *   <li>与歌曲标题完全相等（情歌.lrc）</li>
 *   <li>以标题开头、且紧跟分隔符（情歌 - 梁静茹.lrc；排除 西海情歌、小情歌、情歌悠扬）</li>
 *   <li>以歌手名开头、且包含标题（梁静茹 - 情歌.lrc）</li>
 *   <li>同时包含标题与歌手名（最后的兜底，两者必须都出现）</li>
 * </ol>
 * 五层都不满足就返回 null —— 宁可显示"暂无歌词"，也不显示别人的歌词。
 */
public class LyricsLoader {

    public static String loadLyrics(Context context, Song song) {
        if (song == null || song.data == null) return null;
        File file = new File(song.data);
        if (!file.exists()) return null;

        // 1. 内嵌歌词
        String embedded = readEmbeddedLyrics(file);

        // 2. 同目录按优先级挑选外部歌词
        String external = readExternalLyrics(file, song.title, song.artistName);

        // 3. 决策：内嵌含时间戳 → 内嵌；外部含时间戳 → 外部；都没有 → 内嵌纯文本兜底
        boolean embeddedHasTs = embedded != null && containsTimeStamp(embedded);
        boolean externalHasTs = external != null && containsTimeStamp(external);
        // 诊断用：内嵌歌词来自哪首歌、多长、有没有时间戳，便于区分"用了内嵌"还是"用了外部 lrc"
        top.juluo.ddmusic.util.LogKit.d("LYRIC", "embedded song=" + song.title
                + " len=" + (embedded == null ? 0 : embedded.length())
                + " hasTs=" + embeddedHasTs
                + " -> useEmbedded=" + embeddedHasTs);
        if (embeddedHasTs) return embedded;
        if (externalHasTs) return external;
        if (embedded != null && !embedded.trim().isEmpty()) return embedded;
        return external;
    }

    // ---------------- 外部 .lrc 文件的挑选 ----------------

    private static String readExternalLyrics(File audioFile, String title, String artist) {
        File dir = audioFile.getAbsoluteFile().getParentFile();
        if (dir == null || !dir.isDirectory()) return null;

        File[] files = dir.listFiles(f -> f != null && f.isFile() && isLyricsFile(f.getName()));
        if (files == null || files.length == 0) return null;

        final String base = stripExtension(audioFile.getName()).trim();
        final String safeTitle = safe(title);
        final String safeArtist = normalizeArtist(artist);

        // 先按契合度分层，层内再按确定规则排序；不匹配的文件直接丢弃
        final Map<File, Integer> tiers = new HashMap<>();
        List<File> candidates = new ArrayList<>();
        for (File f : files) {
            int tier = matchTier(stripExtension(f.getName()).trim(), base, safeTitle, safeArtist);
            if (tier >= 0) {
                tiers.put(f, tier);
                candidates.add(f);
            }
        }
        if (candidates.isEmpty()) return null;

        Collections.sort(candidates, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                int ta = tiers.get(a);
                int tb = tiers.get(b);
                if (ta != tb) return ta < tb ? -1 : 1;

                String na = stripExtension(a.getName()).trim();
                String nb = stripExtension(b.getName()).trim();

                // 同一层里优先 .lrc 而不是 .txt
                int fa = lyricsRank(a.getName());
                int fb = lyricsRank(b.getName());
                if (fa != fb) return fa < fb ? -1 : 1;

                // 再优先文件名里带歌手名的（纪念 - 高鱼.lrc 胜过 纪念 - 本兮.lrc）
                boolean aa = !safeArtist.isEmpty() && containsIgnoreCase(na, safeArtist);
                boolean ab = !safeArtist.isEmpty() && containsIgnoreCase(nb, safeArtist);
                if (aa != ab) return aa ? -1 : 1;

                // 最后按长度、字典序兜底，保证结果稳定可复现
                if (na.length() != nb.length()) return na.length() < nb.length() ? -1 : 1;
                return na.compareTo(nb);
            }
        });

        for (File f : candidates) {
            try {
                String content = readFile(f);
                if (content != null && !content.trim().isEmpty()) {
                    top.juluo.ddmusic.util.LogKit.d("LYRIC",
                            "pick title=" + safeTitle + " tier=" + tiers.get(f) + " -> " + f.getName());
                    return content;
                }
            } catch (Exception ignored) {
                // 读失败就试下一个候选
            }
        }
        return null;
    }

    /**
     * 判定候选歌词文件与歌曲的契合程度，返回越小越优先；-1 表示不匹配。
     */
    private static int matchTier(String nameBase, String base, String title, String artist) {
        if (nameBase.isEmpty()) return -1;
        // 0. 与音频文件完全同名
        if (!base.isEmpty() && nameBase.equalsIgnoreCase(base)) return 0;
        // 1. 与歌曲标题完全相等
        if (!title.isEmpty() && nameBase.equalsIgnoreCase(title)) return 1;
        // 2. 以标题开头且紧跟分隔符：排除"包含但不以标题开头"的同名/相似歌名
        if (!title.isEmpty() && startsWithFollowedBySeparator(nameBase, title)) return 2;
        // 3. 以歌手名开头且包含标题（"梁静茹 - 情歌"式命名）
        if (!artist.isEmpty() && !title.isEmpty()
                && startsWithFollowedBySeparator(nameBase, artist)
                && containsIgnoreCase(nameBase, title)) return 3;
        // 4. 标题与歌手名同时出现（最后的兜底）
        if (!artist.isEmpty() && !title.isEmpty()
                && containsIgnoreCase(nameBase, title)
                && containsIgnoreCase(nameBase, artist)) return 4;
        return -1;
    }

    private static boolean isLyricsFile(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".lrc") || lower.endsWith(".txt");
    }

    /** .lrc 优先于 .txt */
    private static int lyricsRank(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".lrc") ? 0 : 1;
    }

    /** 文件名以 prefix 开头，后面紧跟分隔符（或到此结束） */
    private static boolean startsWithFollowedBySeparator(String name, String prefix) {
        if (name.length() < prefix.length()) return false;
        if (!name.regionMatches(true, 0, prefix, 0, prefix.length())) return false;
        if (name.length() == prefix.length()) return true;
        return isSeparator(name.charAt(prefix.length()));
    }

    private static boolean isSeparator(char c) {
        return c == '-' || c == '—' || c == '–' || c == '_' || c == ' '
                || c == '(' || c == '（' || c == '[' || c == '【'
                || c == '·' || c == '.' || c == '、' || c == '：' || c == ':';
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (needle == null || needle.isEmpty()) return true;
        int max = haystack.length() - needle.length();
        for (int i = 0; i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) return true;
        }
        return false;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /** 把 MediaStore 里的"未知歌手"占位值当作空串，否则会误参与匹配 */
    private static String normalizeArtist(String artist) {
        String a = safe(artist);
        if (a.isEmpty()) return "";
        String lower = a.toLowerCase(java.util.Locale.ROOT);
        if ("<unknown>".equals(lower) || "unknown".equals(lower) || "未知".equals(a)
                || "未知艺术家".equals(a) || "未知歌手".equals(a)) {
            return "";
        }
        return a;
    }

    /** 粗略判断歌词文本是否包含 LRC 时间戳 [mm:ss.xx] */
    private static boolean containsTimeStamp(String text) {
        return text.matches("(?s).*\\[\\d{1,2}:\\d{2}.*");
    }

    /** 用 jaudiotagger 读取内嵌歌词（MP3 ID3v2 USLT / FLAC Vorbis LYRICS 等） */
    private static String readEmbeddedLyrics(File file) {
        try {
            org.jaudiotagger.audio.AudioFile af = org.jaudiotagger.audio.AudioFileIO.read(file);
            org.jaudiotagger.tag.Tag tag = af.getTag();
            if (tag == null) return null;
            String lyrics = tag.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS);
            return (lyrics == null || lyrics.trim().isEmpty()) ? null : lyrics;
        } catch (Throwable t) {
            // 非音乐格式 / 无内嵌标签 / 解析失败：静默回退到外部文件
            return null;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String readFile(File file) throws java.io.IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(file);
        try {
            byte[] data = new byte[(int) file.length()];
            int off = 0;
            while (off < data.length) {
                int n = fis.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            // 编码检测：UTF-8 BOM → UTF-8；UTF-16 BOM → UTF-16LE/BE；其余先试 UTF-8，失败回退 GBK
            if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
                return new String(data, 3, data.length - 3, "UTF-8");
            }
            if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
                return new String(data, 2, data.length - 2, "UTF-16LE");
            }
            if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
                return new String(data, 2, data.length - 2, "UTF-16BE");
            }
            // 无 BOM：先按 UTF-8 严格解码（遇非法字节抛 CharacterCodingException），
            // 失败回退 GBK（中文歌词文件常见编码），再失败按 UTF-8 宽松兜底不抛异常。
            // 注意：new String(bytes, UTF_8) 不会抛异常，只会把非法字节替换为 U+FFFD，
            // 所以这里必须用 CharsetDecoder 显式设置 CodingErrorAction.REPORT 才能探测编码。
            try {
                return decodeStrict(data, "UTF-8");
            } catch (java.nio.charset.CharacterCodingException e) {
                try {
                    return decodeStrict(data, "GBK");
                } catch (java.nio.charset.CharacterCodingException ignored) {
                    return new String(data, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } finally {
            fis.close();
        }
    }

    /**
     * 严格解码：遇到非法字节或无法映射的字符直接抛 {@link java.nio.charset.CharacterCodingException}，
     * 用于探测文本真实编码（宽松解码会把非法字节替换成 U+FFFD，无法判断编码是否匹配）。
     */
    private static String decodeStrict(byte[] data, String charsetName)
            throws java.nio.charset.CharacterCodingException {
        java.nio.charset.CharsetDecoder decoder = java.nio.charset.Charset.forName(charsetName)
                .newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        return decoder.decode(java.nio.ByteBuffer.wrap(data)).toString();
    }

    private LyricsLoader() {
    }
}
