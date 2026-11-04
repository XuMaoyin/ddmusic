package top.juluo.ddmusic.views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * 正方形容器：宽高取较小者，用于封面区域（宽=高）。
 */
public class HeightFitSquareLayout extends FrameLayout {

    public HeightFitSquareLayout(Context context) {
        super(context);
    }

    public HeightFitSquareLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HeightFitSquareLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        int spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        super.onMeasure(spec, spec);
    }
}
