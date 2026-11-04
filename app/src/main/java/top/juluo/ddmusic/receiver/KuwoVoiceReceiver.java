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
 * 接收比亚迪 DiLink 小迪语音通过酷我车机协议发出的音乐指令，
 * 把"搜索/播放XX"重定向到本地曲库搜索与播放。
 *
 * 验收过的行为：
 * - 歌手 + 歌名精确匹配（"播放梁静茹的《情歌》"→ 梁静茹《情歌》，不误播同名歌）
 * - 搜索命中后以整个本地曲库为播放队列、命中歌定位到整库位置播放（可切歌）
 * - "播放音乐"（无歌名）→ 等价打开 APP，走 MainActivity 自动播放（续播/从头播 + 跳播放页）
 * - 语音"下一曲/上一曲"：酷我 MEDIA_BUTTON 与系统 MEDIA_BUTTON 双通道（用户实测未生效，保留实现）
 */
public class KuwoVoiceReceiver extends BroadcastReceiver {
    private static final String TAG = "KuwoVoice";

    public static final String ACTION_SEARCH_MUSIC = "cn.kuwo.kwmusicauto.action.SEARCH_MUSIC";
    public static final String ACTION_PLAY_MUSIC = "cn.kuwo.kwmusicauto.action.PLAY_MUSIC";
    public static final String ACTION_SEARCH_AND_PLAY_BY_SONGLISTID = "cn.kuwo.kwmusicauto.action.SEARCH_AND_PLAY_MUSIC_BY_SONGLISTID";
    public static final String ACTION_PLAY_ALBUM = "cn.kuwo.kwmusicauto.action.PLAY_ALBUM";
    public static final String ACTION_RANDOM_PLAY = "cn.kuwo.kwmusicauto.action.RANDOM_PLAY_MUSIC";
    public static final String ACTION_STARTAPP = "cn.kuwo.kwmusicauto.action.STARTAPP";
    public static final String ACTION_JUMP = "cn.kuwo.kwmusicauto.action.JUMP";
    public static final String ACTION_PLAY_MUSIC_INDEX = "cn.kuwo.kwmusicauto.action.PLAY_MUSIC_INDEX";
    public static final String ACTION_PLAY_MUSIC_BY_TYPE = "cn.kuwo.kwmusicauto.action.PLAY_MUSIC_BY_TYPE";
    public static final String ACTION_PAGE_OPERATION = "cn.kuwo.kwmusicauto.action.PAGE_OPERATION";
    public static final String ACTION_MEDIA_BUTTON = "cn.kuwo.kwmusicauto.action.MEDIA_BUTTON";
    /** 系统媒体按键广播（方向盘/小迪语音"下一曲/上一曲/播放/暂停"都走这个） */
    public static final String ACTION_SYSTEM_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON";

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
        // 车机屏幕可见反馈：确认指令已到达本 APP
        showToast(context, "语音指令已到: " + (action == null ? "(无action)" : action));

        if (action == null) return;

        if (ACTION_STARTAPP.equals(action)) {
            openMainActivity(context);
            return;
        }

        // 搜索类 action：提取歌名(可附带歌手) -> 本地搜索 -> 播放
        if (ACTION_SEARCH_MUSIC.equals(action)
                || ACTION_PLAY_MUSIC.equals(action)
                || ACTION_SEARCH_AND_PLAY_BY_SONGLISTID.equals(action)
                || ACTION_PLAY_MUSIC_INDEX.equals(action)
                || ACTION_PLAY_MUSIC_BY_TYPE.equals(action)) {
            String query = extractQuery(extras);
            if (query != null && !query.isEmpty()) {
                playLocalByQuery(context, extractSinger(extras), query);
            } else {
                // 无歌名（如只说"播放音乐"）：等价打开 APP，MainActivity 自动播放（续播/从头播 + 跳播放页）
                Log.d(TAG, "未提取到歌名，按'播放音乐'处理：打开并播放");
                openMainActivity(context);
            }
            return;
        }

        if (ACTION_RANDOM_PLAY.equals(action)) {
            List<Song> all = SongLoader.getAllSongs(context);
            if (!all.isEmpty()) {
                MusicPlayerRemote.openQueue(all, 0, true);
                if (MusicPlayerRemote.musicService != null) {
                    MusicPlayerRemote.musicService.setShuffleMode(MusicService.SHUFFLE_MODE_SHUFFLE);
                }
            }
            return;
        }

        if (ACTION_PLAY_ALBUM.equals(action)) {
            String query = extractQuery(extras);
            if (query != null && !query.isEmpty()) {
                playLocalByQuery(context, null, query);
            }
            return;
        }

        if (ACTION_JUMP.equals(action) || ACTION_PAGE_OPERATION.equals(action)) {
            openMainActivity(context);
        }

        // 媒体控制（小迪"下一曲/上一曲/播放/暂停"）：EXTRA 里带命令
        if (ACTION_MEDIA_BUTTON.equals(action)) {
            String cmd = intent.getStringExtra("EXTRA");
            Log.d(TAG, "MEDIA_BUTTON cmd=" + cmd);
            handleMediaCommand(context, cmd);
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

    /** 系统媒体键码 -> 播放命令（与酷我 MediaButtonReceiver 一致：87→下一曲 / 88→上一曲 / 126→播放 / 127→暂停） */
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

    /** 从 extra 中提取歌名，兼容多个候选字段并去掉"播放"等口语前缀 */
    @Nullable
    private String extractQuery(@Nullable Bundle extras) {
        if (extras == null) return null;
        String[] candidates = {
                "keyword", "query", "songname", "songName", "song", "name",
                "value", "content", "key", "search", "title", "word", "searchKey"
        };
        for (String key : candidates) {
            if (extras.containsKey(key)) {
                Object v = extras.get(key);
                if (v != null) {
                    String s = v.toString().trim();
                    if (!s.isEmpty()) return stripPlayPrefix(s);
                }
            }
        }
        for (String key : extras.keySet()) {
            if (isSystemExtra(key)) continue;
            Object v = extras.get(key);
            if (v instanceof String) {
                String s = ((String) v).trim();
                if (!s.isEmpty() && !isSystemValue(s)) {
                    return stripPlayPrefix(s);
                }
            }
        }
        return null;
    }

    private boolean isSystemExtra(String key) {
        return "android.intent.extra.TEXT".equals(key) || key.startsWith("android.");
    }

    private boolean isSystemValue(String s) {
        return s.startsWith("cn.kuwo") || s.startsWith("com.kuwo") || s.startsWith("http");
    }

    /** 从 extra 中提取歌手名 */
    @Nullable
    private String extractSinger(@Nullable Bundle extras) {
        if (extras == null) return null;
        String[] candidates = {
                "singer", "singerName", "singername", "singer_name", "singers",
                "artist", "artistName", "artist_name", "artists",
                "albumArtist", "album_artist"
        };
        for (String key : candidates) {
            if (extras.containsKey(key)) {
                Object v = extras.get(key);
                if (v != null) {
                    String s = v.toString().trim();
                    if (!s.isEmpty()) return s;
                }
            }
        }
        return null;
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

    /** 主线程弹出 Toast */
    private void showToast(@NonNull final Context context, @NonNull final String msg) {
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
