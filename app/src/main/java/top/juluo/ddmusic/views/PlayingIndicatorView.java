package top.juluo.ddmusic.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * 列表里的"正在播放"频谱指示器：三根上下跳动的小竖条。
 *
 * <p>放在歌曲行最左侧的栏位里（替代其它行的序号位置），不覆盖封面。
 * 用 {@link #postInvalidateOnAnimation()} 跟随 vsync 重绘，
 * 不为每个 ViewHolder 创建动画对象。暂停时停止跳动但仍显示静态条，
 * 标记"当前曲目"。
 */
public class PlayingIndicatorView extends View {

    private static final int BAR_COUNT = 3;
    /** 各竖条的相位与速度，错开之后才像频谱而不是整体缩放 */
    private static final float[] PHASE = {0.0f, 2.1f, 4.0f};
    private static final float[] SPEED = {2.6f, 3.4f, 2.1f};

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private boolean animating;
    private long startTime;

    public PlayingIndicatorView(Context context) {
        super(context);
        init();
    }

    public PlayingIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PlayingIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(Color.WHITE);
    }

    public void setBarColor(int color) {
        if (barPaint.getColor() == color) return;
        barPaint.setColor(color);
        invalidate();
    }

    /** 是否正在播放：true 跳动，false 显示静态条 */
    public void setAnimating(boolean animating) {
        if (this.animating == animating) return;
        this.animating = animating;
        if (animating) startTime = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        // 回收后不再申请重绘，避免看不见的列表项继续跑动画
        animating = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float gap = w / (BAR_COUNT * 2f);
        float barWidth = gap * 0.62f;
        float totalWidth = barWidth * BAR_COUNT + gap * (BAR_COUNT - 1);
        float left = (w - totalWidth) / 2f;
        // 竖条整体高度不要撑满，留出上下呼吸空间
        float minHeight = h * 0.28f;
        float maxHeight = h * 0.82f;

        float t = animating ? (SystemClock.uptimeMillis() - startTime) / 1000f : 0f;
        for (int i = 0; i < BAR_COUNT; i++) {
            float level;
            if (animating) {
                level = (float) ((Math.sin(t * SPEED[i] + PHASE[i]) + 1f) / 2f);
            } else {
                // 暂停时给一组高低不同的静态条，仍然能看出"这是当前曲目"
                level = 0.3f + 0.25f * i;
            }
            float barHeight = minHeight + (maxHeight - minHeight) * level;
            float x = left + i * (barWidth + gap);
            float top = (h - barHeight) / 2f;
            float r = barWidth / 2f;
            rect.set(x, top, x + barWidth, top + barHeight);
            canvas.drawRoundRect(rect, r, r, barPaint);
        }

        if (animating) {
            postInvalidateOnAnimation();
        }
    }
}
