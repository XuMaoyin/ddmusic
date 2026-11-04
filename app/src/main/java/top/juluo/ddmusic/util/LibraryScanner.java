package top.juluo.ddmusic.util;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import top.juluo.ddmusic.loader.SongLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主动扫描本地音频并刷新媒体库。
 *
 * <p>为什么需要它：MediaStore 只会在文件被系统观察到变化时增量更新，而且
 * 用 adb 覆盖文件、或换用其它工具写入时，往往只留下一条元数据残缺的记录
 * （artist / duration 为 NULL）。这类记录会被 {@link SongLoader} 的
 * {@code duration > 0} 过滤掉，表现为"歌从曲库里消失了"。
 * App 内触发一次重新扫描可以把元数据补齐。
 *
 * <p>扫描范围：MediaStore 中已有歌曲所在的目录（递归，含子目录），
 * 以及标准的 {@code /sdcard/Music}。全部在后台线程执行。
 */
public final class LibraryScanner {

    private static final String[] AUDIO_EXT = {
            ".mp3", ".flac", ".m4a", ".aac", ".wav", ".ogg", ".wma", ".ape", ".opus", ".mp4"
    };
    /** 单个根目录递归深度上限，避免异常目录结构导致遍历过深 */
    private static final int MAX_DEPTH = 8;
    /** 单次扫描文件数上限，防止极端情况下长时间占用 */
    private static final int MAX_FILES = 20000;

    public interface Listener {
        /** @param found 待扫描文件总数（收集阶段结束时为最终值） */
        void onProgress(int found, int scanned);

        /** @param libraryCount 扫描完成后曲库中的歌曲数 */
        void onDone(int found, int scanned, int libraryCount);
    }

    public static void start(final Context context, final Listener listener) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<String> files = collectAudioFiles(app);
                final int total = files.size();
                notifyProgress(main, listener, total, 0);

                if (total == 0) {
                    SongLoader.invalidate();
                    final int count = SongLoader.getAllSongs(app).size();
                    notifyDone(main, listener, 0, 0, count);
                    return;
                }

                final AtomicInteger done = new AtomicInteger();
                MediaScannerConnection.scanFile(app, files.toArray(new String[0]), null,
                        new MediaScannerConnection.OnScanCompletedListener() {
                            @Override
                            public void onScanCompleted(String path, android.net.Uri uri) {
                                int n = done.incrementAndGet();
                                if (n % 50 == 0 && n < total) {
                                    notifyProgress(main, listener, total, n);
                                }
                                if (n >= total) {
                                    // 扫描会更新 MediaStore，缓存必须失效才能读到新数据
                                    SongLoader.invalidate();
                                    final int count = SongLoader.getAllSongs(app).size();
                                    notifyDone(main, listener, total, n, count);
                                }
                            }
                        });
            }
        }, "library-scan").start();
    }

    /** 收集待扫描的音频文件（去重） */
    private static List<String> collectAudioFiles(Context context) {
        // Android 内部存储大小写不敏感，MediaStore 里同一目录可能同时存在
        // Download/... 与 download/... 两种写法；用大小写不敏感的集合去重，
        // 否则同一批文件会被重复扫描两次、扫描量翻倍。
        Set<String> roots = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (SongLoader.DirEntry e : SongLoader.getDirectoryStats(context)) {
            roots.add(e.path);
        }
        File standard = new File(Environment.getExternalStorageDirectory(), "Music");
        if (standard.isDirectory()) {
            roots.add(standard.getAbsolutePath());
        }

        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String root : roots) {
            collect(new File(root), 0, result);
            if (result.size() >= MAX_FILES) break;
        }
        return new ArrayList<>(result);
    }

    private static void collect(File dir, int depth, Set<String> out) {
        if (dir == null || depth > MAX_DEPTH || out.size() >= MAX_FILES) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (out.size() >= MAX_FILES) return;
            if (f.isDirectory()) {
                collect(f, depth + 1, out);
            } else if (isAudio(f.getName())) {
                out.add(f.getAbsolutePath());
            }
        }
    }

    private static boolean isAudio(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : AUDIO_EXT) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private static void notifyProgress(final Handler main, final Listener l,
                                       final int found, final int scanned) {
        if (l == null) return;
        main.post(new Runnable() {
            @Override
            public void run() {
                l.onProgress(found, scanned);
            }
        });
    }

    private static void notifyDone(final Handler main, final Listener l,
                                   final int found, final int scanned, final int count) {
        if (l == null) return;
        main.post(new Runnable() {
            @Override
            public void run() {
                l.onDone(found, scanned, count);
            }
        });
    }

    private LibraryScanner() {
    }
}
