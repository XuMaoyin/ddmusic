package top.juluo.ddmusic.helper;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import top.juluo.ddmusic.model.Song;
import top.juluo.ddmusic.service.MusicService;

import java.util.Collections;
import java.util.List;

/**
 * 播放控制静态入口。通过静态持有 MusicService 实例，供 Activity / 语音 Receiver 直接调用。
 */
public class MusicPlayerRemote {

    public static MusicService musicService;

    private static final Object lock = new Object();

    /** 启动并绑定播放服务（语音冷启动用：先拉起服务再控制） */
    public static void startAndBindService(final Context context, final Runnable onConnected) {
        Intent intent = new Intent(context, MusicService.class);
        context.startService(intent);
        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                MusicService.LocalBinder lb = (MusicService.LocalBinder) binder;
                musicService = lb.getService();
                if (onConnected != null) onConnected.run();
                // 保留连接不解除，避免重复绑定
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);
    }

    /** 打开队列并从指定位置播放 */
    public static void openQueue(List<Song> queue, int startPosition, boolean startPlaying) {
        if (musicService == null || queue == null || queue.isEmpty()) return;
        musicService.openQueue(queue, startPosition, startPlaying);
    }

    public static void resumePlaying() {
        if (musicService != null) musicService.resumePlaying();
    }

    public static void pauseSong() {
        if (musicService != null) musicService.pauseSong();
    }

    public static void playOrPause() {
        if (musicService != null) musicService.playOrPause();
    }

    public static void playNextSong() {
        if (musicService != null) musicService.playNextSong();
    }

    public static void playPreviousSong() {
        if (musicService != null) musicService.playPreviousSong();
    }

    public static void seekTo(int ms) {
        if (musicService != null) musicService.seekTo(ms);
    }

    public static void cycleRepeatMode() {
        if (musicService != null) musicService.cycleRepeatMode();
    }

    public static void setShuffleMode(int mode) {
        if (musicService != null) musicService.setShuffleMode(mode);
    }

    public static boolean isPlaying() {
        return musicService != null && musicService.isPlaying();
    }

    public static Song getCurrentSong() {
        if (musicService == null) return null;
        return musicService.getCurrentSong();
    }

    public static List<Song> getPlayingQueue() {
        if (musicService == null) return Collections.emptyList();
        return musicService.getPlayingQueue();
    }

    public static int getPosition() {
        if (musicService == null) return -1;
        return musicService.getPosition();
    }

    public static int getSongProgressMillis() {
        if (musicService == null) return 0;
        return musicService.getSongProgressMillis();
    }

    public static int getSongDurationMillis() {
        if (musicService == null) return 0;
        return musicService.getSongDurationMillis();
    }

    public static int getRepeatMode() {
        if (musicService == null) return MusicService.REPEAT_MODE_NONE;
        return musicService.getRepeatMode();
    }

    public static int getShuffleMode() {
        if (musicService == null) return MusicService.SHUFFLE_MODE_NONE;
        return musicService.getShuffleMode();
    }
}
