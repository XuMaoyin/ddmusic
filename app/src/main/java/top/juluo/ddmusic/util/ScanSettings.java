package top.juluo.ddmusic.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 曲库扫描设置：记录被排除的歌曲目录。
 *
 * <p>目录以绝对路径为键保存在独立的 SharedPreferences 中。判定按"路径前缀"进行，
 * 因此排除父目录会连带排除其所有子目录，符合用户直觉。
 */
public final class ScanSettings {

    private static final String PREF = "scan_settings";
    private static final String KEY_EXCLUDED_DIRS = "excluded_dirs";

    /**
     * 读取全部被排除目录。
     *
     * <p>必须返回<b>可变</b>副本：调用方（setExcluded/setIncluded）会直接增删。
     * 空集也不能返回 {@link Collections#emptySet()}，那是不可变集合，add() 会抛
     * UnsupportedOperationException。
     */
    public static Set<String> getExcludedDirs(Context context) {
        SharedPreferences sp = prefs(context);
        Set<String> raw = sp.getStringSet(KEY_EXCLUDED_DIRS, null);
        if (raw == null || raw.isEmpty()) return new HashSet<>();
        return new HashSet<>(raw);
    }

    /** 判断某个歌曲文件路径是否被排除 */
    public static boolean isExcluded(Context context, String filePath) {
        return isExcluded(getExcludedDirs(context), filePath);
    }

    /** 判断某个歌曲文件路径是否落在被排除目录（或其子目录）下 */
    public static boolean isExcluded(Set<String> excludedDirs, String filePath) {
        if (excludedDirs == null || excludedDirs.isEmpty() || filePath == null) return false;
        for (String dir : excludedDirs) {
            if (dir == null || dir.isEmpty()) continue;
            if (startsWithPath(filePath, dir)) return true;
        }
        return false;
    }

    /**
     * 路径前缀比较，忽略大小写。
     *
     * <p>Android 内部存储（sdcardfs/FUSE）大小写不敏感，MediaStore 里同一条路径可能被存成
     * Download 和 download 两种写法；若按区分大小写比较，排除其中一个会漏掉另一个。
     * 前缀必须落在路径分隔符上，避免 /a/b 误匹配 /a/bc。
     */
    private static boolean startsWithPath(String path, String dir) {
        if (path.equalsIgnoreCase(dir)) return true;
        String prefix = dir.endsWith("/") ? dir : dir + "/";
        return path.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /** 设置/取消排除某个目录 */
    public static void setExcluded(Context context, String dir, boolean excluded) {
        if (dir == null || dir.isEmpty()) return;
        Set<String> dirs = getExcludedDirs(context);
        if (excluded) {
            if (!dirs.add(dir)) return;
        } else {
            if (!dirs.remove(dir)) return;
        }
        prefs(context).edit().putStringSet(KEY_EXCLUDED_DIRS, dirs).apply();
    }

    /**
     * 把一个目录重新纳入扫描。
     * 由于排除是按前缀生效的，若它的某个祖先目录仍被排除，本目录依然会被过滤掉、
     * 开关会立刻弹回去，因此这里连同祖先的排除项一起移除。
     */
    public static void setIncluded(Context context, String dir) {
        if (dir == null || dir.isEmpty()) return;
        Set<String> dirs = getExcludedDirs(context);
        boolean changed = dirs.remove(dir);
        String cur = dir;
        while (true) {
            int slash = cur.lastIndexOf('/');
            if (slash <= 0) break;
            cur = cur.substring(0, slash);
            if (dirs.remove(cur)) changed = true;
        }
        if (changed) prefs(context).edit().putStringSet(KEY_EXCLUDED_DIRS, dirs).apply();
    }

    /** 判断某个目录是否被排除（自身被排除，或祖先目录被排除） */
    public static boolean isDirExcluded(Set<String> excludedDirs, String dir) {
        if (excludedDirs == null || excludedDirs.isEmpty() || dir == null) return false;
        for (String d : excludedDirs) {
            if (d == null || d.isEmpty()) continue;
            if (startsWithPath(dir, d)) return true;
        }
        return false;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private ScanSettings() {
    }
}
