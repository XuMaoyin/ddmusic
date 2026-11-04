package top.juluo.ddmusic.ui.player;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import top.juluo.ddmusic.R;
import top.juluo.ddmusic.lyrics.LyricLine;

import java.util.ArrayList;
import java.util.List;

/**
 * 横屏播放页右侧"内嵌滚动歌词"适配器（Salt Music 风格）。
 * 当前播放句高亮放大并居中，其余歌词行浅色，随播放进度自动滚动。
 */
public class PlayerLyricsAdapter extends RecyclerView.Adapter<PlayerLyricsAdapter.LyricViewHolder> {

    private final List<LyricLine> lines = new ArrayList<>();
    private int currentLine = -1;

    private int highlightTextColor = Color.WHITE;
    private int normalTextColor = Color.parseColor("#66FFFFFF");
    private float highlightTextSize = 48f;
    private float normalTextSize = 40f;
    private boolean darkBackground = true;
    /** 固定行高（px），保证滚动定位精确、高亮行不跳动 */
    private int itemHeightPx = 0;

    public void setLines(List<LyricLine> newLines) {
        lines.clear();
        if (newLines != null) lines.addAll(newLines);
        currentLine = -1;
        notifyDataSetChanged();
    }

    /** 更新当前播放句：仅局部刷新，避免全量 notify 导致滚动跳动 */
    public void updateCurrentTime(int timeMs, int totalMs) {
        if (totalMs > 0 && timeMs > totalMs) return;
        if (timeMs < 0) return;
        int idx = getLineIndex(timeMs);
        if (idx != currentLine) {
            int old = currentLine;
            currentLine = idx;
            // 只刷新旧句与新句两行，不触发布局重排
            if (old >= 0 && old < lines.size()) notifyItemChanged(old);
            if (idx >= 0 && idx < lines.size()) notifyItemChanged(idx);
        }
    }

    private int getLineIndex(int timeMs) {
        int idx = -1;
        // 防御：如果所有行 timeMs 都相同（时间戳未正常解析），固定在第一行，避免跳到最后一行
        if (lines.size() > 1) {
            boolean allSame = true;
            long first = lines.get(0).timeMs;
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).timeMs != first) { allSame = false; break; }
            }
            if (allSame) return 0;
        }
        for (int i = 0; i < lines.size(); i++) {
            if (timeMs >= lines.get(i).timeMs) {
                idx = i;
            } else {
                break;
            }
        }
        return idx;
    }

    public int getCurrentLine() {
        return currentLine;
    }

    public boolean isSynchronized() {
        return lines.size() > 0;
    }

    /** 配置颜色与字号（随封面背景亮度自适应） */
    public void setColors(int highlightColor, int normalColor, float highlightSize, float normalSize, boolean dark) {
        this.highlightTextColor = highlightColor;
        this.normalTextColor = normalColor;
        this.highlightTextSize = highlightSize;
        this.normalTextSize = normalSize;
        this.darkBackground = dark;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LyricViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_player_lyric_line, parent, false);
        return new LyricViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LyricViewHolder holder, int position) {
        String text = lines.get(position).text;
        holder.textView.setText(text == null ? "" : text);
        boolean active = (position == currentLine);
        holder.textView.setTextColor(active ? highlightTextColor : normalTextColor);
        holder.textView.setTextSize(active ? highlightTextSize : normalTextSize);
        holder.textView.setAlpha(active ? 1f : 0.6f);
        // itemHeightPx 作为**最小**行高：普通短句保持统一的行间节奏；
        // 超长句会换行，这一行随之撑高，避免被固定行高裁掉（横屏上出现过显示不全）。
        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.itemView.setLayoutParams(lp);
        }
        if (itemHeightPx > 0) {
            holder.itemView.setMinimumHeight(itemHeightPx);
        }
    }

    /** 设置最小行高（px），由播放页按横竖屏传入；超长句会在此基础上继续长高 */
    public void setItemHeightPx(int px) {
        this.itemHeightPx = px;
        notifyDataSetChanged();
    }

    public int getItemHeightPx() {
        return itemHeightPx;
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    public static class LyricViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        LyricViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.lyric_line_text);
        }
    }
}
