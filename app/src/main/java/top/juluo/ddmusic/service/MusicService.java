package top.juluo.ddmusic.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.preference.PreferenceManager;

import top.juluo.ddmusic.R;
import top.juluo.ddmusic.activity.MainActivity;
import top.juluo.ddmusic.loader.SongLoader;
import top.juluo.ddmusic.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 播放引擎：前台服务。
 * - 播放队列 + 循环/随机模式
 * - MediaSession（通知栏、方向盘、系统媒体键）
 * - 队列/进度持久化（重启/冷启动后从上次位置续播）
 */
public class MusicService extends Service {

    public static final int REPEAT_MODE_NONE = 0;
    public static final int REPEAT_MODE_ALL = 1;
    public static final int REPEAT_MODE_THIS = 2;
    public static final int SHUFFLE_MODE_NONE = 0;
    public static final int SHUFFLE_MODE_SHUFFLE = 1;

    public static final String SAVED_POSITION = "POSITION";
    public static final String SAVED_POSITION_IN_TRACK = "POSITION_IN_TRACK";

    public static final String ACTION_TOGGLE = "top.juluo.ddmusic.action.TOGGLE";
    public static final String ACTION_NEXT = "top.juluo.ddmusic.action.NEXT";
    public static final String ACTION_PREVIOUS = "top.juluo.ddmusic.action.PREVIOUS";
    public static final String ACTION_PLAY_PAUSE = "top.juluo.ddmusic.action.PLAY_PAUSE";

    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIFICATION_ID = 100;

    private final List<Song> playingQueue = new ArrayList<>();
    private int position = -1;
    /**
     * MediaPlayer 当前实际装载的队列下标；-1 表示播放器还是空的（Idle）。
     * 冷启动从持久化位置恢复时，队列已填好但播放器尚未装载任何音源，
     * 此时不能直接 start()：新版 Android 会通过 onError(-38 INVALID_OPERATION)
     * 上报而不是抛 IllegalStateException，会被错误监听误判为"文件不可读"而跳过该曲目。
     */
    private int loadedIndex = -1;
    /**
     * 冷启动恢复时待应用的"歌内进度"（毫秒）及其所属队列下标。
     * 恢复时播放器还未装载音源，无法立刻 seek，必须等 playSongAt() prepare() 之后再跳转；
     * 绑定下标可避免用户此时直接点了别的歌、结果被错误地跳到中间。
     */
    private int pendingSeekMs = -1;
    private int pendingSeekPosition = -1;
    private int repeatMode = REPEAT_MODE_NONE;
    private int shuffleMode = SHUFFLE_MODE_NONE;

    /** 播放中定时落盘的间隔：车机断电或进程被直接杀掉时 onDestroy 不一定执行，靠它兜住进度 */
    private static final long SAVE_INTERVAL_MS = 5000;
    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                savePosition();
            }
            saveHandler.postDelayed(this, SAVE_INTERVAL_MS);
        }
    };

    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private MediaSessionManager mediaSessionManager;
    private final Random random = new Random();

    private final LocalBinder binder = new LocalBinder();

    private final CopyOnWriteArrayList<MusicEventListener> listeners = new CopyOnWriteArrayList<>();

    public interface MusicEventListener {
        void onPlayStateChanged();

        void onSongChanged();

        void onQueueChanged();
    }

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        service = this;
        createNotificationChannel();
        initMediaSession();
        initMediaPlayer();
        restoreQueuesAndPosition();
        registerNoisyReceiver();
        // 播放中每 5 秒落盘一次，保证断电/强杀后仍能续播到秒
        saveHandler.postDelayed(saveRunnable, SAVE_INTERVAL_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_TOGGLE.equals(action)) playOrPause();
            else if (ACTION_PLAY_PAUSE.equals(action)) playOrPause();
            else if (ACTION_NEXT.equals(action)) playNextSong();
            else if (ACTION_PREVIOUS.equals(action)) playPreviousSong();
            else if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
                // 系统媒体按键（方向盘）转发
                MediaButtonReceiver.handleIntent(mediaSession, intent);
            }
        }
        // 无论以何种方式启动（含系统重启服务、无 action 的启动），有当前曲目就保持前台通知
        if (getCurrentSong() != null) {
            showNotification();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        service = null;
        saveHandler.removeCallbacks(saveRunnable);
        unregisterReceiver(noisyReceiver);
        if (mediaPlayer != null) {
            savePosition();
            mediaPlayer.release();
            mediaPlayer = null;
            loadedIndex = -1;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        super.onDestroy();
    }

    // ---------------- 播放控制 ----------------

    public void openQueue(List<Song> queue, int startPosition, boolean startPlaying) {
        if (queue == null || queue.isEmpty()) return;
        playingQueue.clear();
        playingQueue.addAll(queue);
        // 尊重用户点击的 startPosition：随机只影响"切下一首"的顺序，不覆盖初始播放位置
        position = Math.max(0, Math.min(startPosition, queue.size() - 1));
        // 用户显式指定了要播的歌：作废冷启动恢复留下的"跳到中间"意图
        pendingSeekMs = -1;
        pendingSeekPosition = -1;
        notifyQueueChanged();
        playSongAt(position);
        if (startPlaying) {
            mediaPlayer.start();
        }
        updateMediaSessionState();
        showNotification();
    }

    public void playSongAt(int index) {
        if (playingQueue.isEmpty()) return;
        if (index < 0 || index >= playingQueue.size()) return;
        position = index;
        Song song = playingQueue.get(position);
        try {
            mediaPlayer.reset();
            loadedIndex = -1;
            mediaPlayer.setDataSource(song.data);
            mediaPlayer.prepare();
            loadedIndex = position;
            applyPendingSeek(index);
            mediaPlayer.start();
            top.juluo.ddmusic.util.PlayCountManager.increment(this, song.id);
            notifySongChanged();
            updateMediaSessionState();
            showNotification();
        } catch (Exception e) {
            // 文件不可读：跳过
            loadedIndex = -1;
            playNextSong(true);
        }
    }

    public void playNextSong() {
        playNextSong(false);
    }

    private void playNextSong(boolean force) {
        if (playingQueue.isEmpty()) return;
        int next;
        if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
            next = random.nextInt(playingQueue.size());
            if (playingQueue.size() > 1 && next == position) next = (next + 1) % playingQueue.size();
        } else if (position + 1 < playingQueue.size()) {
            next = position + 1;
        } else {
            next = repeatMode == REPEAT_MODE_ALL ? 0 : -1;
        }
        if (next < 0) {
            // 末尾且非列表循环：停止
            mediaPlayer.pause();
            mediaPlayer.seekTo(0);
            updateMediaSessionState();
            notifyPlayStateChanged();
            return;
        }
        playSongAt(next);
        mediaPlayer.start();
    }

    public void playPreviousSong() {
        if (playingQueue.isEmpty()) return;
        // 已播超过 3 秒则回到本曲开头
        if (getSongProgressMillis() > 3000) {
            mediaPlayer.seekTo(0);
            return;
        }
        int prev = position > 0 ? position - 1 : (repeatMode == REPEAT_MODE_ALL ? playingQueue.size() - 1 : 0);
        playSongAt(prev);
        mediaPlayer.start();
    }

    public void playOrPause() {
        if (mediaPlayer == null || playingQueue.isEmpty()) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            savePosition(); // 暂停点即刻落盘，不必等定时器
        } else if (loadedIndex != position) {
            // 尚未装载当前曲目（如冷启动后队列已恢复但播放器为空）：先装载再播放
            playSongAt(position);
        } else {
            mediaPlayer.start();
        }
        notifyPlayStateChanged();
        updateMediaSessionState();
        showNotification();
    }

    public void pauseSong() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            savePosition(); // 暂停点即刻落盘
            notifyPlayStateChanged();
            updateMediaSessionState();
            showNotification();
        }
    }

    public void resumePlaying() {
        if (mediaPlayer == null) return;
        if (playingQueue.isEmpty()) {
            // 有上次位置但队列空：尝试恢复
            restoreQueuesAndPosition();
        }
        if (playingQueue.isEmpty()) return;
        if (getCurrentSong() == null) return;
        if (loadedIndex != position) {
            // 播放器尚未装载当前曲目（冷启动恢复、或上一次装载失败）：
            // 直接装载，不能盲目 start()，否则会被 onError(-38) 误判成坏文件而跳曲
            playSongAt(position);
        } else {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException e) {
                loadedIndex = -1;
                playSongAt(position);
            }
        }
        notifyPlayStateChanged();
        updateMediaSessionState();
        showNotification();
    }

    public void seekTo(int ms) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(ms);
            updateMediaSessionState();
        }
    }

    public void cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
        notifyQueueChanged();
        updateMediaSessionState();
    }

    public void setRepeatMode(int mode) {
        repeatMode = mode;
        notifyQueueChanged();
        updateMediaSessionState();
    }

    public void setShuffleMode(int mode) {
        shuffleMode = mode;
        notifyQueueChanged();
        updateMediaSessionState();
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public int getShuffleMode() {
        return shuffleMode;
    }

    public List<Song> getPlayingQueue() {
        return new ArrayList<>(playingQueue);
    }

    public int getPosition() {
        return position;
    }

    public Song getCurrentSong() {
        if (playingQueue.isEmpty() || position < 0 || position >= playingQueue.size()) return null;
        return playingQueue.get(position);
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getSongProgressMillis() {
        if (mediaPlayer == null) return 0;
        try {
            return mediaPlayer.getCurrentPosition();
        } catch (Exception e) {
            return 0;
        }
    }

    public int getSongDurationMillis() {
        if (mediaPlayer == null) return 0;
        try {
            return mediaPlayer.getDuration();
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------------- 持久化 ----------------

    private void restoreQueuesAndPosition() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int savedPos = prefs.getInt(SAVED_POSITION, -1);
        if (savedPos < 0) return;
        int savedMs = prefs.getInt(SAVED_POSITION_IN_TRACK, 0);
        List<Song> all = SongLoader.getAllSongs(this);
        if (all.isEmpty()) return;
        playingQueue.clear();
        playingQueue.addAll(all);
        position = Math.min(savedPos, all.size() - 1);
        // 记住待跳转的歌内进度：播放器此刻还没有音源，等 playSongAt() prepare() 后再 seek
        pendingSeekPosition = position;
        pendingSeekMs = Math.max(0, savedMs);
        notifyQueueChanged();
    }

    /**
     * 应用恢复出的歌内进度。只在确实轮到恢复的那首曲子时生效，
     * 用完即清，避免影响后续正常切歌/选歌。
     */
    private void applyPendingSeek(int index) {
        if (pendingSeekPosition < 0 || pendingSeekMs <= 0) return;
        int targetMs = pendingSeekMs;
        boolean sameTrack = (index == pendingSeekPosition);
        pendingSeekMs = -1;
        pendingSeekPosition = -1;
        if (!sameTrack) return;
        try {
            int duration = mediaPlayer.getDuration();
            if (duration > 0 && targetMs >= duration) {
                targetMs = Math.max(0, duration - 1);
            }
            mediaPlayer.seekTo(targetMs);
        } catch (Exception ignored) {
            // 跳转失败不影响正常从头播放
        }
    }

    private void savePosition() {
        if (playingQueue.isEmpty() || position < 0) return;
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putInt(SAVED_POSITION, position);
        editor.putInt(SAVED_POSITION_IN_TRACK, getSongProgressMillis());
        editor.apply();
    }

    // ---------------- MediaPlayer ----------------

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnCompletionListener(mp -> {
            if (repeatMode == REPEAT_MODE_THIS) {
                mediaPlayer.seekTo(0);
                mediaPlayer.start();
                return;
            }
            savePosition();
            playNextSong();
        });
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            playNextSong(true);
            return true;
        });
    }

    private void initMediaSession() {
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mediaSession = new MediaSessionCompat(this, "CarMusicPlayer");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                resumePlaying();
            }

            @Override
            public void onPause() {
                pauseSong();
            }

            @Override
            public void onSkipToNext() {
                playNextSong();
            }

            @Override
            public void onSkipToPrevious() {
                playPreviousSong();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public void onStop() {
                pauseSong();
            }
        });
        // 系统媒体按键（方向盘）通过 MediaButtonReceiver 转发到本服务
        PendingIntent mediaButtonReceiver = PendingIntent.getBroadcast(
                this, 0,
                new Intent(Intent.ACTION_MEDIA_BUTTON),
                PendingIntent.FLAG_UPDATE_CURRENT);
        mediaSession.setMediaButtonReceiver(mediaButtonReceiver);
        mediaSession.setActive(true);
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) return;
        Song song = getCurrentSong();
        if (song == null) return;
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO
                        | PlaybackStateCompat.ACTION_STOP)
                .setState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        getSongProgressMillis(), 1f)
                .build();
        mediaSession.setPlaybackState(state);
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artistName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.albumName)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                .build();
        mediaSession.setMetadata(metadata);
    }

    // ---------------- 通知 ----------------

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "播放控制",
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    private void showNotification() {
        Song song = getCurrentSong();
        if (song == null) return;
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent prevIntent = PendingIntent.getService(this, 1,
                new Intent(this, MusicService.class).setAction(ACTION_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent toggleIntent = PendingIntent.getService(this, 2,
                new Intent(this, MusicService.class).setAction(ACTION_TOGGLE), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent nextIntent = PendingIntent.getService(this, 3,
                new Intent(this, MusicService.class).setAction(ACTION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT);

        // 加载专辑封面（同步小图，失败兜底不显示）
        android.graphics.Bitmap largeIcon = null;
        try {
            Uri artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), song.albumId);
            java.io.InputStream is = getContentResolver().openInputStream(artUri);
            if (is != null) {
                largeIcon = android.graphics.BitmapFactory.decodeStream(is);
                is.close();
            }
        } catch (Exception ignored) {
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play_arrow_white_24dp)
                .setContentTitle(song.title)
                .setContentText(song.artistName)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(new NotificationCompat.Action(R.drawable.ic_skip_previous_white_24dp, "上一曲", prevIntent))
                .addAction(new NotificationCompat.Action(
                        isPlaying() ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp,
                        isPlaying() ? "暂停" : "播放", toggleIntent))
                .addAction(new NotificationCompat.Action(R.drawable.ic_skip_next_white_24dp, "下一曲", nextIntent))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon);
        }
        startForeground(NOTIFICATION_ID, builder.build());
    }

    // ---------------- 耳机拔出暂停 ----------------

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pauseSong();
            }
        }
    };

    private void registerNoisyReceiver() {
        try {
            registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        } catch (Exception ignored) {
        }
    }

    // ---------------- 事件通知 ----------------

    public static void addEventListener(MusicEventListener listener) {
        if (service != null) service.listeners.add(listener);
    }

    public static void removeEventListener(MusicEventListener listener) {
        if (service != null) service.listeners.remove(listener);
    }

    private static MusicService service;

    public static MusicService getInstance() {
        return service;
    }

    private void notifyPlayStateChanged() {
        for (MusicEventListener l : listeners) l.onPlayStateChanged();
    }

    private void notifySongChanged() {
        for (MusicEventListener l : listeners) l.onSongChanged();
    }

    private void notifyQueueChanged() {
        for (MusicEventListener l : listeners) l.onQueueChanged();
    }
}
