package top.juluo.ddmusic.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 排错日志：追加写入 app 外部私有目录 carmusic.log，便于用户上传。 */
public class LogKit {
    private static final String FILE_NAME = "carmusic.log";
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static File logFile;

    public static synchronized void init(Context ctx) {
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) dir = ctx.getFilesDir();
            logFile = new File(dir, FILE_NAME);
        } catch (Throwable t) {
            logFile = null;
        }
    }

    public static synchronized void d(String tag, String msg) {
        if (logFile == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println(TS.format(new Date()) + " [" + tag + "] " + msg);
        } catch (Throwable ignored) {
        }
    }

    /** 返回日志文件绝对路径（提示用户位置） */
    public static String path() {
        return logFile == null ? "null" : logFile.getAbsolutePath();
    }
}
