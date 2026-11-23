package top.juluo.ddmusic.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import top.juluo.ddmusic.activity.MainActivity;
import top.juluo.ddmusic.helper.MusicPlayerRemote;
import top.juluo.ddmusic.loader.SongLoader;
import top.juluo.ddmusic.model.Song;
import top.juluo.ddmusic.service.MusicService;

import java.util.List;

/**
 * 接收比亚迪 DiLink 小迪语音指令，重定向到本地曲库搜索与播放。
 *
 * <p>两条通道：
 * <ul>
 *   <li>点播：本包名在小迪白名单（MediaUtil.e）内且经 {@code VoiceRegistrar} 注册后，
 *       小迪把 {@code com.byd.action.AUTOVOICE_*} 广播定向发给本包；</li>
 *   <li>播放控制：方向盘/语音的"上一曲/下一曲/播放/暂停"走系统
 *       {@code android.intent.action.MEDIA_BUTTON}（无活动会话时的兜底通道，
 *       正常播放中由 MediaSession 直接接收按键，不经过这里）。</li>
 * </ul>
 *
 * 验收过的行为：
 * - 歌手 + 歌名精确匹配（"播放梁静茹的《情歌》"→ 梁静茹《情歌》，不误播同名歌）
 * - 搜索命中后以整个本地曲库为播放队列、命中歌定位到整库位置播放（可切歌）
 * - "播放音乐"（无歌名）→ 随机播放整库，不打断当前界面
 */
public class VoiceReceiver extends BroadcastReceiver {
    private static final String TAG = "VoiceReceiver";

    /** 系统媒体按键广播（方向盘/小迪语音"下一曲/上一曲/播放/暂停"的兜底通道） */
    public static final String ACTION_SYSTEM_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON";

    /**
     * 比亚迪小迪第三方音乐协议。
     * 本应用注册为音乐源后，小迪把这些广播定向发给本包。
     */
    public static final String ACTION_BYD_SEARCH = "com.byd.action.AUTOVOICE_SEARCH";
    public static final String ACTION_BYD_SEARCH_PLUS = "com.byd.action.AUTOVOICE_SEARCH_PLUS";
    public static final String ACTION_BYD_PLAY_RANDOM = "com.byd.action.AUTOVOICE_PLAY_RANDOM";
    public static final String ACTION_BYD_PLAY_FAVORITE = "com.byd.action.AUTOVOICE_PLAY_FAVORITE";
    public static final String ACTION_BYD_PLAY_LATELY = "com.byd.action.AUTOVOICE_PLAY_LATELY";
    public static final String ACTION_BYD_PLAY_DOWNLOAD = "com.byd.action.AUTOVOICE_PLAY_DOWNLOAD";
    public static final String ACTION_BYD_RECOMMEND = "com.byd.action.AUTOVOICE_RECOMMEND";
    public static final String ACTION_BYD_JUMP_TO = "com.byd.action.AUTOVOICE_JUMP_TO";
    /** 小迪搜索广播里的歌名/歌手字段（注意是 BYD 自己的 EXTRA_ 命名） */
    public static final String EXTRA_BYD_KEYWORDS = "EXTRA_KEYWORDS_SEARCH";
    public static final String EXTRA_BYD_ARTIST = "EXTRA_ARTIST_SEARCH";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.d(TAG, "收到语音广播 action=" + action);

        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Log.d(TAG, "  extra[" + key + "] = " + extras.get(key));
            }
        }
        // 车机屏幕可见反馈：确认指令已到达本 APP（设置里可关）
        showToast(context, "语音指令已到: " + (action == null ? "(无action)" : action));

        if (action == null) return;

        // ---------------- 比亚迪小迪协议 ----------------

        if (ACTION_BYD_SEARCH.equals(action) || ACTION_BYD_SEARCH_PLUS.equals(action)) {
            String keyword = extras == null ? null : extras.getString(EXTRA_BYD_KEYWORDS);
            String artist = extras == null ? null : extras.getString(EXTRA_BYD_ARTIST);
            Log.d(TAG, "BYD 点播: keyword=" + keyword + " artist=" + artist);
            if (keyword != null && !keyword.trim().isEmpty()) {
                playLocalByQuery(context, artist, stripPlayPrefix(keyword));
            } else {
                openMainActivity(context);
            }
            return;
        }

        if (ACTION_BYD_PLAY_RANDOM.equals(action)) {
            Log.d(TAG, "BYD 随机播放");
            List<Song> all = SongLoader.getAllSongs(context);
            if (!all.isEmpty()) {
                MusicPlayerRemote.openQueue(all, 0, true);
                if (MusicPlayerRemote.musicService != null) {
                    MusicPlayerRemote.musicService.setShuffleMode(MusicService.SHUFFLE_MODE_SHUFFLE);
                }
            } else {
                openMainActivity(context);
            }
            return;
        }

        if (ACTION_BYD_PLAY_FAVORITE.equals(action)
                || ACTION_BYD_PLAY_LATELY.equals(action)
                || ACTION_BYD_PLAY_DOWNLOAD.equals(action)
                || ACTION_BYD_RECOMMEND.equals(action)
                || ACTION_BYD_JUMP_TO.equals(action)) {
            // 本地曲库没有收藏/最近/下载分类，先按“打开并继续播放”处理
            Log.d(TAG, "BYD 其它播放指令，按打开播放处理: " + action);
            openMainActivity(context);
        }

        // 系统媒体按键广播（方向盘/小迪语音走这个）：从 KEY_EVENT 取键码映射
        if (ACTION_SYSTEM_MEDIA_BUTTON.equals(action)) {
            KeyEvent keyEvent = intent.getParcelableExtra("android.intent.extra.KEY_EVENT");
            if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                String cmd = mapKeyCodeToCommand(keyEvent.getKeyCode());
                Log.d(TAG, "系统媒体键 keyCode=" + keyEvent.getKeyCode() + " cmd=" + cmd);
                if (cmd != null) {
                    handleMediaCommand(context, cmd);
                }
            }
        }
    }

    /** 系统媒体键码 -> 播放命令 */
    @Nullable
    private String mapKeyCodeToCommand(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return "MEDIA_NEXT";
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return "MEDIA_PRE";
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                return "MEDIA_PLAY";
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return "MEDIA_PAUSE";
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return "MEDIA_PLAY_PAUSE";
            case KeyEvent.KEYCODE_MEDIA_STOP:
                return "MEDIA_STOP";
            default:
                return null;
        }
    }

    private void handleMediaCommand(@NonNull Context context, @Nullable String cmd) {
        try {
            if ("MEDIA_NEXT".equals(cmd)) {
                Log.d(TAG, "下一曲");
                MusicPlayerRemote.playNextSong();
            } else if ("MEDIA_PRE".equals(cmd)) {
                Log.d(TAG, "上一曲");
                MusicPlayerRemote.playPreviousSong();
            } else if ("MEDIA_PLAY".equals(cmd)) {
                Log.d(TAG, "播放");
                MusicPlayerRemote.resumePlaying();
            } else if ("MEDIA_PAUSE".equals(cmd)) {
                Log.d(TAG, "暂停");
                MusicPlayerRemote.pauseSong();
            } else if ("MEDIA_PLAY_PAUSE".equals(cmd)) {
                Log.d(TAG, "播放/暂停切换");
                if (MusicPlayerRemote.isPlaying()) {
                    MusicPlayerRemote.pauseSong();
                } else {
                    MusicPlayerRemote.resumePlaying();
                }
            } else {
                Log.d(TAG, "未识别的媒体命令: " + cmd);
            }
        } catch (Exception e) {
            Log.w(TAG, "handleMediaCommand failed", e);
        }
    }

    /** 去掉"播放/来一首/放一首"等口语前缀，只保留歌名 */
    private String stripPlayPrefix(String raw) {
        String s = raw.trim();
        String[] prefixes = {"播放", "来一首", "来首", "放一首", "放首", "播放一下", "给我播放", "想听", "我想听", "听听", "来听"};
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                return s.substring(p.length()).trim();
            }
        }
        String[] enPrefixes = {"play ", "play:", "song ", "search "};
        for (String p : enPrefixes) {
            if (s.toLowerCase().startsWith(p)) {
                return s.substring(p.length()).trim();
            }
        }
        return s;
    }

    /**
     * 本地搜索并播放。
     * 优先"歌手 + 歌名"精确匹配；无歌手时按歌名/歌手模糊搜索。
     * 命中后用整个曲库作为播放队列并定位播放，切歌可切到整库其它歌曲。
     */
    private void playLocalByQuery(@NonNull Context context, @Nullable String singer, @NonNull String query) {
        final List<Song> hits;
        if (singer != null && !singer.isEmpty()) {
            Log.d(TAG, "按歌手+歌名搜索: 歌手=" + singer + " 歌名=" + query);
            hits = SongLoader.getSongsByArtistAndTitle(context, singer, query);
            Log.d(TAG, "歌手+歌名命中 " + hits.size() + " 首");
        } else {
            Log.d(TAG, "本地搜索歌曲: " + query);
            hits = SongLoader.getSongs(context, query);
            Log.d(TAG, "本地命中 " + hits.size() + " 首");
        }
        if (hits.isEmpty()) {
            showToast(context, "本地未找到: " + query);
            Log.w(TAG, "本地曲库没有匹配: " + query);
            return;
        }
        final List<Song> all = SongLoader.getAllSongs(context);
        if (all.isEmpty()) {
            showToast(context, "命中本地 " + hits.size() + " 首: " + query);
            playSongs(context, hits, 0);
            return;
        }
        int pos = 0;
        long targetId = hits.get(0).id;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id == targetId) {
                pos = i;
                break;
            }
        }
        showToast(context, "命中本地 " + hits.size() + " 首: " + query);
        playSongs(context, all, pos);
    }

    /**
     * 播放歌曲列表（从指定 position 开始）。MusicService 是异步绑定的，冷启动/语音唤起时
     * 必须先确保服务就绪再 openQueue。
     */
    private void playSongs(@NonNull final Context context, @NonNull final List<Song> songs, final int position) {
        try {
            if (MusicPlayerRemote.musicService != null) {
                MusicPlayerRemote.openQueue(songs, position, true);
                return;
            }
            final Context appCtx = context.getApplicationContext();
            appCtx.startService(new Intent(appCtx, MusicService.class));
            appCtx.bindService(
                    new Intent(appCtx, MusicService.class),
                    new android.content.ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName name, IBinder service) {
                            MusicPlayerRemote.musicService =
                                    ((MusicService.LocalBinder) service).getService();
                            try {
                                MusicPlayerRemote.openQueue(songs, position, true);
                            } catch (Exception e) {
                                Log.w(TAG, "openQueue failed", e);
                            }
                            try {
                                appCtx.unbindService(this);
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName name) {
                            MusicPlayerRemote.musicService = null;
                        }
                    },
                    Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.w(TAG, "playSongs failed", e);
        }
    }

    /** 主线程弹出 Toast（设置里可关） */
    private void showToast(@NonNull final Context context, @NonNull final String msg) {
        if (!top.juluo.ddmusic.util.UiSettings.isVoiceToast(context)) return;
        try {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            Log.w(TAG, "toast failed", e);
        }
    }

    private void openMainActivity(Context context) {
        try {
            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(launch);
        } catch (Exception e) {
            Log.w(TAG, "启动主界面失败", e);
        }
    }
}
