package top.juluo.ddmusic.ui.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import top.juluo.ddmusic.R;
import top.juluo.ddmusic.model.Song;
import top.juluo.ddmusic.util.MusicUtil;
import top.juluo.ddmusic.views.PlayingIndicatorView;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.VH> {

    public interface OnSongClickListener {
        void onSongClick(Song song, int position);
    }

    private final Context context;
    private final List<Song> songs = new ArrayList<>();
    private final OnSongClickListener listener;

    /** 正在播放的歌曲 id；-1 表示没有 */
    private long playingSongId = -1;
    private boolean playing = false;

    private final int accentColor;
    private final int primaryTextColor;
    /** 当前播放行的圆角高亮底 */
    private final GradientDrawable playingRowBg;

    public SongAdapter(Context context, OnSongClickListener listener) {
        this.context = context;
        this.listener = listener;
        accentColor = ContextCompat.getColor(context, R.color.accent);
        primaryTextColor = ContextCompat.getColor(context, R.color.text_primary);
        playingRowBg = new GradientDrawable();
        playingRowBg.setColor(0x24FFFFFF);
        playingRowBg.setCornerRadius(8f * context.getResources().getDisplayMetrics().density);
    }

    public void setSongs(List<Song> newSongs) {
        songs.clear();
        if (newSongs != null) songs.addAll(newSongs);
        notifyDataSetChanged();
    }

    /**
     * 更新"正在播放"标记：只刷新受影响的行，不整表重绘（曲库上千首）。
     *
     * @param songId    当前曲目 id，-1 表示没有
     * @param isPlaying 是否正在播放；暂停时仍标记该曲目，只是频谱不再跳动
     */
    public void setPlayingState(long songId, boolean isPlaying) {
        int oldPos = indexOfSong(playingSongId);
        boolean songChanged = songId != playingSongId;
        playingSongId = songId;
        playing = isPlaying;
        int newPos = indexOfSong(playingSongId);
        if (songChanged && oldPos >= 0) notifyItemChanged(oldPos);
        if (newPos >= 0) notifyItemChanged(newPos);
    }

    private int indexOfSong(long id) {
        if (id < 0) return -1;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == id) return i;
        }
        return -1;
    }

    public List<Song> getSongs() {
        return new ArrayList<>(songs);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Song song = songs.get(position);
        holder.title.setText(song.title);
        holder.subtitle.setText(song.artistName + " · " + song.albumName);
        holder.duration.setText(MusicUtil.formatDuration(song.duration));
        Uri art = Uri.parse("content://media/external/audio/albumart/" + song.albumId);
        Glide.with(context).load(art).placeholder(R.color.primaryDark).into(holder.cover);

        // 当前播放项：频谱替掉序号 + 整行高亮 + 歌名变强调色（封面不遮挡）
        boolean isCurrent = playingSongId >= 0 && song.id == playingSongId;
        holder.index.setText(String.valueOf(position + 1));
        holder.index.setVisibility(isCurrent ? View.INVISIBLE : View.VISIBLE);
        holder.indicator.setBarColor(accentColor);
        holder.indicator.setVisibility(isCurrent ? View.VISIBLE : View.INVISIBLE);
        holder.indicator.setAnimating(isCurrent && playing);
        holder.title.setTextColor(isCurrent ? accentColor : primaryTextColor);
        Drawable rowBg = isCurrent ? playingRowBg : holder.normalBackground;
        if (holder.itemView.getBackground() != rowBg) {
            holder.itemView.setBackground(rowBg);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSongClick(song, position);
        });
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;
        TextView subtitle;
        TextView duration;
        PlayingIndicatorView indicator;
        TextView index;
        /** 行原本的点击涟漪背景，切回普通行时恢复 */
        Drawable normalBackground;

        VH(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.cover);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            duration = itemView.findViewById(R.id.duration);
            indicator = itemView.findViewById(R.id.playing_indicator);
            index = itemView.findViewById(R.id.track_index);
            normalBackground = itemView.getBackground();
        }
    }
}
