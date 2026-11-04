package top.juluo.ddmusic.util;

import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;
import androidx.core.graphics.ColorUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 播放页背景取色：封面 Palette dominant（面积最大）+ 降饱和 50%。
 */
public class BackgroundColorUtil {

    @ColorInt
    public static int getColor(@Nullable Palette palette, @ColorInt int fallback) {
        if (palette != null) {
            List<Palette.Swatch> swatches = palette.getSwatches();
            if (!swatches.isEmpty()) {
                // 取占面积最大的主色（dominant）：反映封面整体色调，避免一小块鲜艳色主导背景
                Palette.Swatch dominant = Collections.max(swatches, SwatchComparator.getInstance());
                // 降低饱和度，让背景更柔和、不刺眼
                return desaturate(dominant.getRgb(), 0.5f);
            }
        }
        return fallback;
    }

    /** 把颜色饱和度降到原来的 saturationRatio（0~1） */
    private static int desaturate(@ColorInt int color, float saturationRatio) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[1] = Math.min(1f, hsl[1] * saturationRatio);
        return ColorUtils.HSLToColor(hsl);
    }

    private static class SwatchComparator implements Comparator<Palette.Swatch> {
        private static SwatchComparator sInstance;

        static SwatchComparator getInstance() {
            if (sInstance == null) {
                sInstance = new SwatchComparator();
            }
            return sInstance;
        }

        @Override
        public int compare(Palette.Swatch lhs, Palette.Swatch rhs) {
            int populationA = lhs.getPopulation();
            int populationB = rhs.getPopulation();
            if (populationA != populationB) {
                return populationA - populationB;
            }
            float h1 = hsl(lhs.getRgb())[0];
            float h2 = hsl(rhs.getRgb())[0];
            return Float.compare(h1, h2);
        }

        private float[] hsl(int color) {
            float[] hsl = new float[3];
            ColorUtils.colorToHSL(color, hsl);
            return hsl;
        }
    }

    private BackgroundColorUtil() {
    }
}
