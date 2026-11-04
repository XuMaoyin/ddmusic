package top.juluo.ddmusic.activity;

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import top.juluo.ddmusic.R;
import top.juluo.ddmusic.helper.MusicPlayerRemote;
import top.juluo.ddmusic.lyrics.LrcParser;
import top.juluo.ddmusic.lyrics.LyricLine;
import top.juluo.ddmusic.lyrics.LyricsLoader;
import top.juluo.ddmusic.model.Song;
import top.juluo.ddmusic.service.MusicService;
import top.juluo.ddmusic.ui.player.PlayerLyricsAdapter;
import top.juluo.ddmusic.util.BackgroundColorUtil;
import top.juluo.ddmusic.util.ColorUtil;
import top.juluo.ddmusic.util.MusicUtil;

import java.util.List;

/**
 * 横屏播放页（车机主场景）：
 * - 左封面（正方形、padding 40dp、24dp 圆角）+ 下方控制 / 右整块滚动歌词
 * - 歌名歌手显示在封面上方
 * - 背景取色 = 封面 Palette dominant + 降饱和 50%
 * - 颜色随背景亮度自适应：背景暗→白系，背景亮→黑系；当前句歌词=背景反色荧光
 * - 进度条细线+白色圆点+时间外置；四按钮等距；循环/随机合并为单个模式按钮
 */
public class PlayerActivity extends AppCompatActivity {

    private View colorBackground;
    private ImageView playerCover;
    private TextView playerTitle;
    private TextView playerSubtitle;
    private RecyclerView lyricsRecycler;
    private TextView lyricsEmpty;
    private View lyricsFadeTop;
    private View lyricsFadeBottom;
    /** 上下边缘渐变遮罩：与背景同色 → 透明，等价于歌词"淡入淡出" */
    private android.graphics.drawable.GradientDrawable fadeTopDrawable;
    private android.graphics.drawable.GradientDrawable fadeBottomDrawable;
    private PlayerLyricsAdapter lyricsAdapter;
    private SeekBar progressSlider;
    private TextView songCurrentProgress;
    private TextView songTotalTime;
    private ImageButton modeButton;
    private ImageButton prevButton;
    private ImageButton playPauseButton;
    private ImageButton nextButton;

    private int lastColor = 0xFF000000;
    private int lastScrolledLine = -1;
    /**
     * 冷启动场景：语音指令先触发了播放，播放页随后启动，此时服务可能已连接但队列还没装好。
     * 不能立刻判定"没有在播放"并关闭，先短暂重试等待曲目就绪。
     */
    private static final int MAX_EMPTY_SONG_RETRIES = 5;
    private static final long EMPTY_SONG_RETRY_MS = 300;
    private int emptySongRetries = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgressViews();
            handler.postDelayed(this, 200);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            MusicService.LocalBinder lb = (MusicService.LocalBinder) binder;
            MusicPlayerRemote.musicService = lb.getService();
            MusicService.addEventListener(musicEventListener);
            updateSong();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    private final MusicService.MusicEventListener musicEventListener = new MusicService.MusicEventListener() {
        @Override
        public void onPlayStateChanged() {
            updatePlayPauseIcon();
        }

        @Override
        public void onSongChanged() {
            updateSong();
        }

        @Override
        public void onQueueChanged() {
            updateModeButton();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        top.juluo.ddmusic.util.LogKit.init(this);
        setContentView(R.layout.activity_player);
        applySystemBars();
        bindViews();
        applyLyricsSide();
        setupLyricsFade();
        setupControls();
        applyAdaptiveControlSizes();
        bindService(new Intent(this, MusicService.class), serviceConnection, BIND_AUTO_CREATE);
        handler.post(progressRunnable);
        top.juluo.ddmusic.util.LogKit.d("LIFE", "onCreate");
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressRunnable);
        MusicService.removeEventListener(musicEventListener);
        try {
            unbindService(serviceConnection);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private void bindViews() {
        colorBackground = findViewById(R.id.color_background);
        playerCover = findViewById(R.id.player_cover);
        playerTitle = findViewById(R.id.player_title_text);
        playerSubtitle = findViewById(R.id.player_subtitle_text);
        lyricsRecycler = findViewById(R.id.player_lyrics_recycler);
        lyricsEmpty = findViewById(R.id.player_lyrics_empty);
        lyricsFadeTop = findViewById(R.id.lyrics_fade_top);
        lyricsFadeBottom = findViewById(R.id.lyrics_fade_bottom);
        progressSlider = findViewById(R.id.player_progress_slider);
        songCurrentProgress = findViewById(R.id.player_song_current_progress);
        songTotalTime = findViewById(R.id.player_song_total_time);
        modeButton = findViewById(R.id.player_mode_button);
        prevButton = findViewById(R.id.player_prev_button);
        playPauseButton = findViewById(R.id.player_play_pause_button);
        nextButton = findViewById(R.id.player_next_button);
    }

    private void setupControls() {
        // 封面圆角 24dp（竖屏布局无封面，playerCover 为 null 时跳过）
        if (playerCover != null) {
            final float radius = getResources().getDisplayMetrics().density * 24f;
            playerCover.setClipToOutline(true);
            playerCover.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
        }

        lyricsRecycler.setLayoutManager(new LinearLayoutManager(this));
        lyricsAdapter = new PlayerLyricsAdapter();
        lyricsRecycler.setAdapter(lyricsAdapter);

        prevButton.setOnClickListener(v -> MusicPlayerRemote.playPreviousSong());
        nextButton.setOnClickListener(v -> MusicPlayerRemote.playNextSong());
        playPauseButton.setOnClickListener(v -> MusicPlayerRemote.playOrPause());
        modeButton.setOnClickListener(v -> {
            int repeat = MusicPlayerRemote.getRepeatMode();
            int shuffle = MusicPlayerRemote.getShuffleMode();
            if (repeat == MusicService.REPEAT_MODE_NONE && shuffle == MusicService.SHUFFLE_MODE_NONE) {
                // 顺序 → 单曲循环
                MusicPlayerRemote.musicService.setRepeatMode(MusicService.REPEAT_MODE_THIS);
            } else if (repeat == MusicService.REPEAT_MODE_THIS && shuffle == MusicService.SHUFFLE_MODE_NONE) {
                // 单曲循环 → 随机（先清单曲，避免进入随机后 repeat 残留导致切不回）
                MusicPlayerRemote.musicService.setRepeatMode(MusicService.REPEAT_MODE_NONE);
                MusicPlayerRemote.setShuffleMode(MusicService.SHUFFLE_MODE_SHUFFLE);
            } else {
                // 随机 → 顺序（全部清零）
                MusicPlayerRemote.musicService.setRepeatMode(MusicService.REPEAT_MODE_NONE);
                MusicPlayerRemote.setShuffleMode(MusicService.SHUFFLE_MODE_NONE);
            }
            updateModeButton();
        });

        progressSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 用户拖动/点击时让时间立刻跟随手指，不必等 200ms 的定时刷新
                if (fromUser) {
                    songCurrentProgress.setText(MusicUtil.formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 拖动期间停止用播放进度覆盖滑块位置，否则手指还没松开就被拽回去
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                MusicPlayerRemote.seekTo(seekBar.getProgress());
                isSeeking = false;
                onUpdateProgressViews(MusicPlayerRemote.getSongProgressMillis(),
                        MusicPlayerRemote.getSongDurationMillis());
            }
        });
    }

    /**
     * 按设置决定横屏时歌词在左还是右。
     *
     * <p>横屏布局是「封面栏 + 歌词栏」两块的横向排列，交换它们在父容器中的顺序即可，
     * 两块自身的 layout_weight 会随之保留（歌词栏 0.68 更宽）。
     * 竖屏布局没有封面栏，找不到对应控件时直接跳过。
     */
    private void applyLyricsSide() {
        if (getResources().getConfiguration().orientation
                != android.content.res.Configuration.ORIENTATION_LANDSCAPE) return;
        android.view.ViewGroup panes = findViewById(R.id.landscape_panes);
        android.view.View coverPane = findViewById(R.id.landscape_left_column);
        android.view.View lyricsPane = findViewById(R.id.lyrics_pane);
        if (panes == null || coverPane == null || lyricsPane == null) return;

        boolean wantLyricsLeft = top.juluo.ddmusic.util.UiSettings.isLyricsOnLeft(this);
        boolean isLyricsLeft = panes.indexOfChild(lyricsPane) < panes.indexOfChild(coverPane);
        if (wantLyricsLeft == isLyricsLeft) return;

        panes.removeView(lyricsPane);
        panes.addView(lyricsPane, 0);

        // 歌词栏左右内边距随位置镜像：靠屏幕边缘的一侧留更宽
        if (lyricsRecycler != null) {
            float d = getResources().getDisplayMetrics().density;
            int near = (int) (16 * d + 0.5f);
            int far = (int) (24 * d + 0.5f);
            lyricsRecycler.setPadding(wantLyricsLeft ? far : near,
                    lyricsRecycler.getPaddingTop(),
                    wantLyricsLeft ? near : far,
                    lyricsRecycler.getPaddingBottom());
        }
    }

    /**
     * 初始化歌词区上下边缘的渐变遮罩。
     *
     * <p>播放页背景是随封面取色的纯色，所以用「背景色 → 透明」的线性渐变叠在歌词区上下两端，
     * 视觉上就是歌词滚到边缘时淡出/淡入。颜色在背景切换时同步刷新（见 updateLyricsFadeColor）。
     */
    private void setupLyricsFade() {
        if (lyricsFadeTop == null || lyricsFadeBottom == null) return;
        fadeTopDrawable = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{lastColor, Color.TRANSPARENT});
        fadeBottomDrawable = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{lastColor, Color.TRANSPARENT});
        lyricsFadeTop.setBackground(fadeTopDrawable);
        lyricsFadeBottom.setBackground(fadeBottomDrawable);

        // 遮罩高度按歌词区实际高度自适应：横屏手机（21:9）歌词区只有 230dp 左右，
        // 固定 90dp 上下各一块会把中间可读区域挤没。车机 4:3 上高度足够，仍保持 90dp。
        lyricsRecycler.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                  oldLeft, oldTop, oldRight, oldBottom) -> {
            int height = bottom - top;
            if (height > 0) updateLyricsFadeHeight(height);
        });
    }

    /** 上下遮罩各占歌词区高度的 1/5，并限制在 24~90dp */
    private void updateLyricsFadeHeight(int recyclerHeight) {
        float d = getResources().getDisplayMetrics().density;
        int fade = Math.max(Math.round(24 * d),
                Math.min(Math.round(90 * d), recyclerHeight / 5));
        setViewHeightIfChanged(lyricsFadeTop, fade);
        setViewHeightIfChanged(lyricsFadeBottom, fade);
    }

    private void setViewHeightIfChanged(View view, int height) {
        if (view == null) return;
        android.view.ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null && lp.height != height) {
            lp.height = height;
            view.setLayoutParams(lp);
        }
    }

    /** 让上下渐变遮罩跟随背景色（复用同一个 Drawable，只改颜色，避免每帧分配对象） */
    private void updateLyricsFadeColor(int color) {
        if (fadeTopDrawable != null) {
            fadeTopDrawable.setColors(new int[]{color, Color.TRANSPARENT});
        }
        if (fadeBottomDrawable != null) {
            fadeBottomDrawable.setColors(new int[]{color, Color.TRANSPARENT});
        }
    }

    private void updateSong() {
        Song song = MusicPlayerRemote.getCurrentSong();
        if (song == null) {
            if (MusicPlayerRemote.musicService != null) {
                // 服务已就绪但没有当前曲目：可能是语音播放刚触发、队列还在装载，稍等再判定
                if (emptySongRetries < MAX_EMPTY_SONG_RETRIES) {
                    emptySongRetries++;
                    handler.postDelayed(this::updateSong, EMPTY_SONG_RETRY_MS);
                    return;
                }
                Toast.makeText(this, "当前没有在播放的音乐", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            return;
        }
        emptySongRetries = 0;
        playerTitle.setText(song.title);
        playerSubtitle.setText(song.artistName + " · " + song.albumName);
        updatePlayPauseIcon();
        updateModeButton();

        Uri art = Uri.parse("content://media/external/audio/albumart/" + song.albumId);
        Glide.with(this)
                .load(art)
                .asBitmap()
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                        // 竖屏不显示封面，但仍用封面图做背景取色（横竖屏背景都要自适应）
                        if (playerCover != null) {
                            playerCover.setImageBitmap(resource);
                        }
                        Palette.from(resource).generate(palette ->
                                animateColorChange(BackgroundColorUtil.getColor(palette, 0xFF000000)));
                    }

                    @Override
                    public void onLoadFailed(Exception e, Drawable errorDrawable) {
                        if (playerCover != null) {
                            playerCover.setImageResource(R.color.primaryDark);
                        }
                        animateColorChange(0xFF000000);
                    }
                });

        loadLyrics(song);
    }

    /** 记录当前正在加载歌词的歌曲 data，避免旧歌线程覆盖新歌歌词 */
    private String lyricsLoadKey = null;

    private void loadLyrics(final Song song) {
        lyricsAdapter.setLines(new java.util.ArrayList<>());
        lyricsRecycler.setVisibility(View.GONE);
        lastScrolledLine = -1;
        if (lyricsEmpty != null) lyricsEmpty.setVisibility(View.VISIBLE);
        if (song == null) return;
        currentLyricSongId = song.id;
        lyricsLoadKey = song.data;
        top.juluo.ddmusic.util.LogKit.d("LYRIC", "load start songId=" + song.id + " title=" + song.title);
        new Thread(() -> {
            String lrc = LyricsLoader.loadLyrics(PlayerActivity.this, song);
            List<LyricLine> lines = (lrc == null) ? new java.util.ArrayList<>() : LrcParser.parse(lrc);
            if (lines.isEmpty()) {
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                lrc = LyricsLoader.loadLyrics(PlayerActivity.this, song);
                lines = (lrc == null) ? new java.util.ArrayList<>() : LrcParser.parse(lrc);
            }
            final List<LyricLine> finalLines = lines;
            final String finalKey = song.data;
            runOnUiThread(() -> {
                if (!finalKey.equals(lyricsLoadKey)) {
                    top.juluo.ddmusic.util.LogKit.d("LYRIC", "stale drop songId=" + song.id);
                    return;
                }
                if (finalLines.isEmpty()) {
                    top.juluo.ddmusic.util.LogKit.d("LYRIC", "no lyrics songId=" + song.id);
                    return;
                }
                long firstTs = finalLines.get(0).timeMs;
                long lastTs = finalLines.get(finalLines.size() - 1).timeMs;
                top.juluo.ddmusic.util.LogKit.d("LYRIC", "loaded songId=" + song.id + " lines=" + finalLines.size()
                        + " firstTs=" + firstTs + " lastTs=" + lastTs);
                lyricsAdapter.setLines(finalLines);
                lyricsRecycler.setVisibility(View.VISIBLE);
                if (lyricsEmpty != null) lyricsEmpty.setVisibility(View.GONE);
                applyLyricsColors();
                int progress = MusicPlayerRemote.getSongProgressMillis();
                int total = MusicPlayerRemote.getSongDurationMillis();
                top.juluo.ddmusic.util.LogKit.d("LYRIC", "locate songId=" + song.id + " progress=" + progress + " total=" + total);
                lyricsAdapter.updateCurrentTime(progress, total);
                int cl = lyricsAdapter.getCurrentLine();
                top.juluo.ddmusic.util.LogKit.d("LYRIC", "locate result songId=" + song.id + " currentLine=" + cl);
                if (cl >= 0) {
                    lastScrolledLine = cl;
                    smoothScrollToLine(cl);
                }
            });
        }).start();
    }

    private void updateProgressViews() {
        int progress = MusicPlayerRemote.getSongProgressMillis();
        int total = MusicPlayerRemote.getSongDurationMillis();
        if (total <= 0) {
            total = MusicPlayerRemote.getCurrentSong() != null
                    ? MusicPlayerRemote.getCurrentSong().duration : 0;
        }
        onUpdateProgressViews(progress, total);
    }

    private boolean isSeeking = false;

    private void onUpdateProgressViews(int progress, int total) {
        int max = Math.max(total, 1);
        // 每 200ms 重复 setMax 会让进度重新换算、滑块抖动，仅在时长变化时设置
        if (progressSlider.getMax() != max) {
            progressSlider.setMax(max);
        }
        // 拖动中不要覆盖用户的位置
        if (!isSeeking) {
            progressSlider.setProgress(progress);
            songCurrentProgress.setText(MusicUtil.formatDuration(progress));
        }
        songTotalTime.setText(MusicUtil.formatDuration(total));
        if (lyricsAdapter.isSynchronized() && currentLyricSongId != -1
                && currentLyricSongId == getCurrentSongId()) {
            lyricsAdapter.updateCurrentTime(progress, total);
            int currentLine = lyricsAdapter.getCurrentLine();
            if (currentLine >= 0 && currentLine != lastScrolledLine) {
                top.juluo.ddmusic.util.LogKit.d("PROG", "songId=" + currentLyricSongId
                        + " progress=" + progress + " total=" + total + " line=" + currentLine
                        + " lastScroll=" + lastScrolledLine);
                lastScrolledLine = currentLine;
                smoothScrollToLine(currentLine);
            }
        }
    }

    /** 当前正在播放歌曲的 id，用于防止旧歌歌词列表被新歌进度驱动而错位 */
    private long currentLyricSongId = -1;

    private long getCurrentSongId() {
        Song s = MusicPlayerRemote.getCurrentSong();
        return s == null ? -1 : s.id;
    }

    /** 将指定歌词行精确定位到视口垂直中心。
     *  目标行顶部偏移 = 视口高/2 - 行高/2，使其中心落在视口中心；
     *  系统对 scrollToPositionWithOffset 自带边界钳位：
     *   - 中间段落：目标行居中，上方下方歌词照常可见；
     *   - 开头：目标行居中，上方留白、下方歌词可见；
     *   - 结尾：目标行居中，上方歌词可见、下方留白。
     *  不依赖任何动态 padding，歌词列表正常渲染前后行。 */
    private void smoothScrollToLine(int position) {
        if (lyricsRecycler == null || lyricsRecycler.getVisibility() != View.VISIBLE) return;
        if (lyricsRecycler.getLayoutManager() == null) return;
        if (position < 0 || lyricsAdapter == null || position >= lyricsAdapter.getItemCount()) return;
        // 布局未完成则等待下一帧后重试
        if (lyricsRecycler.getHeight() == 0 || lyricsAdapter.getItemHeightPx() <= 0) {
            lyricsRecycler.post(() -> {
                if (lyricsRecycler != null) smoothScrollToLine(position);
            });
            return;
        }
        int itemH = lyricsAdapter.getItemHeightPx();
        int recyclerH = lyricsRecycler.getHeight();
        // 超长歌词会换行、该行实际高度大于 itemHeightPx，居中要用目标行的真实高度
        View measured = ((LinearLayoutManager) lyricsRecycler.getLayoutManager()).findViewByPosition(position);
        if (measured != null && measured.getHeight() > 0) {
            itemH = measured.getHeight();
        }
        // 高亮行中心对齐歌词区正中心。
        // 注意不能写成 paddingTop + recyclerH/2 - itemH/2：那会把 paddingTop 也算进偏移，
        // 整体向下偏 40dp，使上下淡化区不对称。
        int targetTop = (recyclerH - itemH) / 2;
        // scrollToPositionWithOffset 的偏移基准是 paddingTop 之后的内容区
        int offset = targetTop - lyricsRecycler.getPaddingTop();
        LinearLayoutManager lm = (LinearLayoutManager) lyricsRecycler.getLayoutManager();
        if (lm == null) return;

        // scrollToPositionWithOffset 是瞬移、没有动画；目标行已布局时改成逐像素平滑滚动。
        // 子 View 的 top 已包含 paddingTop，与 scrollToPositionWithOffset 落位规则一致，
        // 因此平滑滚动结束后的位置与瞬移完全相同。
        View target = lm.findViewByPosition(position);
        if (target != null) {
            int dy = target.getTop() - targetTop;
            if (dy != 0) {
                lyricsRecycler.smoothScrollBy(0, dy);
            }
        } else {
            // 首次加载或跨度很大（目标行尚未布局）：先瞬移保证立即可见，
            // 后续换行时会走上面的平滑分支
            lm.scrollToPositionWithOffset(position, offset);
        }
    }

    private void updatePlayPauseIcon() {
        if (playPauseButton == null) return;
        playPauseButton.setImageResource(MusicPlayerRemote.isPlaying()
                ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp);
        int color = !ColorUtil.isColorLight(lastColor) ? Color.WHITE : Color.BLACK;
        playPauseButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void updateModeButton() {
        if (modeButton == null) return;
        int repeat = MusicPlayerRemote.getRepeatMode();
        int shuffle = MusicPlayerRemote.getShuffleMode();
        int icon;
        if (shuffle == MusicService.SHUFFLE_MODE_SHUFFLE) {
            icon = R.drawable.ic_shuffle_white_24dp;
        } else if (repeat == MusicService.REPEAT_MODE_THIS) {
            icon = R.drawable.ic_repeat_one_white_24dp;
        } else {
            icon = R.drawable.ic_repeat_white_24dp;
        }
        modeButton.setImageResource(icon);
        modeButton.setColorFilter(!ColorUtil.isColorLight(lastColor) ? Color.WHITE : Color.BLACK,
                PorterDuff.Mode.SRC_IN);
    }

    /** 背景颜色切换：过渡动画 + 全部控件颜色自适应（B2.28 规则） */
    private void animateColorChange(final int newColor) {
        int from = lastColor;
        lastColor = newColor;
        if (colorBackground == null) return;
        animateBackground(from, newColor);
        boolean dark = !ColorUtil.isColorLight(newColor);
        int fg = dark ? Color.WHITE : Color.BLACK;

        // 文字 / 按钮 / 进度 / 时间
        playerTitle.setTextColor(fg);
        playerSubtitle.setTextColor(fg);
        int tint = dark ? 0xCCFFFFFF : 0xCC000000;
        prevButton.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
        nextButton.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
        playPauseButton.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
        modeButton.setColorFilter(fg, PorterDuff.Mode.SRC_IN);
        songCurrentProgress.setTextColor(tint);
        songTotalTime.setTextColor(tint);
        // 滑块已被移除（thumb=@null），getThumb() 返回 null。
        // 两者必须分开 try：否则一起抛异常时进度条上色会被跳过，进度条不随背景变色。
        try {
            android.graphics.drawable.Drawable thumb = progressSlider.getThumb();
            if (thumb != null) {
                thumb.mutate().setColorFilter(fg, PorterDuff.Mode.SRC_IN);
            }
        } catch (Exception ignored) {
        }
        try {
            progressSlider.getProgressDrawable().mutate().setColorFilter(fg, PorterDuff.Mode.SRC_IN);
        } catch (Exception ignored) {
        }
        applyLyricsColors();
    }

    /**
     * 系统栏处理：
     * <ul>
     *   <li>横屏（车机主场景）——全屏，隐藏状态栏，歌词/封面能用到整块屏幕；</li>
     *   <li>竖屏——显示状态栏，并把它的颜色设成与播放页背景一致（随封面色变化），
     *       避免出现一条与页面割裂的深色横条。图标明暗按背景亮度自适应。</li>
     * </ul>
     */
    private void applySystemBars() {
        boolean landscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        View decor = getWindow().getDecorView();
        if (landscape) {
            // compileSdk 29，拿不到 API 30 的 WindowInsetsController，只能用这个旧 flag（仍然生效）
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            updateStatusBarColor(lastColor);
        }
    }

    /** 竖屏下让状态栏跟随页面背景色，并切换图标明暗；横屏状态栏隐藏，直接忽略 */
    private void updateStatusBarColor(int color) {
        if (getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            return;
        }
        Window window = getWindow();
        window.setStatusBarColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (ColorUtil.isColorLight(color)) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    private void animateBackground(int from, int to) {
        try {
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofArgb(from, to);
            animator.setDuration(300);
            animator.addUpdateListener(animation -> {
                int color = (Integer) animation.getAnimatedValue();
                colorBackground.setBackgroundColor(color);
                // 遮罩跟背景一起过渡，否则切歌瞬间边缘会出现一条突兀的色带
                updateLyricsFadeColor(color);
                // 竖屏状态栏也一起过渡，保持与页面连成一片
                updateStatusBarColor(color);
            });
            animator.start();
        } catch (Exception e) {
            colorBackground.setBackgroundColor(to);
            updateLyricsFadeColor(to);
            updateStatusBarColor(to);
        }
    }

    /** 歌词颜色随背景亮度自适应：当前句=背景反色荧光，其余句=暗背景半透明白/亮背景半透明黑 */
    private void applyLyricsColors() {
        if (lyricsAdapter == null) return;
        boolean dark = !ColorUtil.isColorLight(lastColor);
        int base = lastColor;
        int r = Color.red(base), g = Color.green(base), b = Color.blue(base);
        int highlightColor = Color.rgb(255 - r, 255 - g, 255 - b);
        int normalColor = dark ? 0x99FFFFFF : 0x99000000;
        // 横屏 48/40sp（车机远距离阅读）；竖屏字号减半为 24/20sp
        boolean landscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        float highlightSize = landscape ? 48f : 24f;
        float normalSize = landscape ? 40f : 20f;
        // 固定行高（px）= 高亮字号 × 1.3 行距 + item 上下留白，保证文字完整不截断。
        // 横屏留白收到 10dp：手机上横屏可视高度只有 300dp 出头，原来上下各 20dp 的留白
        // 会让行间距显得非常空，一屏只能看到三四行。竖屏保持原样。
        float density = getResources().getDisplayMetrics().density;
        int lineHeight = (int) (highlightSize * 1.3f * density + 0.5f);
        int padVertical = (int) ((landscape ? 10f : 40f) * density + 0.5f);
        lyricsAdapter.setItemHeightPx(lineHeight + padVertical);
        lyricsAdapter.setColors(highlightColor, normalColor, highlightSize, normalSize, dark);
    }

    /**
     * 按按钮实际尺寸调整图标留白。
     *
     * <p>四个按钮是等宽 weight 布局，宽度随屏幕变化；图标用 fitCenter 会放大填满
     * 「按钮尺寸 - padding」的区域。原来的固定 22dp 在 4:3 车机（按钮约 86×76dp）上
     * 得到的图标约 32dp，观感合适；21:9 手机横屏时按钮只有约 66×76dp，
     * 同样的 padding 会把图标压到 22dp，显得太小。
     *
     * <p>因此这里不以 padding 比例为准，而是**固定图标目标尺寸**反推 padding：
     * 图标保持 32dp（播放/暂停 28dp，沿用原设计"稍小"的观感），
     * 宽屏上 padding 自然回到 22dp/24dp、与改动前完全一致，只有窄屏才会缩小留白。
     */
    private void applyAdaptiveControlSizes() {
        final float density = getResources().getDisplayMetrics().density;
        final int minPad = Math.round(6 * density);
        final int maxPad = Math.round(22 * density);
        final int[] ids = {
                R.id.player_mode_button,
                R.id.player_prev_button,
                R.id.player_play_pause_button,
                R.id.player_next_button
        };
        for (final int id : ids) {
            final View button = findViewById(id);
            if (button == null) continue;
            button.post(new Runnable() {
                @Override
                public void run() {
                    int w = button.getWidth();
                    int h = button.getHeight();
                    if (w <= 0 || h <= 0) return;
                    float targetIconDp = (id == R.id.player_play_pause_button) ? 28f : 32f;
                    int targetIcon = Math.round(targetIconDp * density);
                    int pad = Math.round((Math.min(w, h) - targetIcon) / 2f);
                    pad = Math.max(minPad, Math.min(maxPad, pad));
                    button.setPadding(pad, pad, pad, pad);
                }
            });
        }
    }
}
