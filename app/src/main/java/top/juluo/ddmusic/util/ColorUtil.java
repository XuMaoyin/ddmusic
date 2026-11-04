package top.juluo.ddmusic.util;

import android.graphics.Color;

/**
 * 颜色工具：亮度判断（决定文字/按钮用黑还是白）。
 */
public class ColorUtil {

    /** 颜色是否偏亮（用于决定前景文字颜色） */
    public static boolean isColorLight(int color) {
        return calculateLuminance(color) > 0.5;
    }

    /** 相对亮度 0~1（Rec.709 系数） */
    public static double calculateLuminance(int color) {
        int r = (Color.red(color) * 299) / 1000;
        int g = (Color.green(color) * 587) / 1000;
        int b = (Color.blue(color) * 114) / 1000;
        return (r + g + b) / 255.0;
    }

    /** 变暗 */
    public static int darkenColor(int color) {
        float factor = 0.9f;
        return Color.argb(Color.alpha(color),
                (int) (Color.red(color) * factor),
                (int) (Color.green(color) * factor),
                (int) (Color.blue(color) * factor));
    }

    private ColorUtil() {
    }
}
