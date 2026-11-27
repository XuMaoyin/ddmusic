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
    /** 分类/流派槽：高频分类词（情歌、民谣…）会被小迪 NLU 解析到这里而不是歌名槽 */
    public static final String EXTRA_BYD_TAGS = "EXTRA_PROGRAM_TAGS";

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
            String tags = extras == null ? null : extras.getString(EXTRA_BYD_TAGS);
            Log.d(TAG, "BYD 点播: keyword=" + keyword + " artist=" + artist + " tags=" + tags);
            resolveAndPlay(context, keyword, artist, tags);
            return;
        }

        if (ACTION_BYD_PLAY_RANDOM.equals(action)) {
            Log.d(TAG, "BYD 随机播放");
            List<Song> all = SongLoader.getAllSongs(context);
            if (!all.isEmpty()) {
                int start = all.size() == 1 ? 0 : new java.util.Random().nextInt(all.size());
                playSongs(context, all, start, true);
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

    /**
     * 小迪槽位不可靠（实测）："播放周杰伦的歌"只给 artist；"播放情歌"把歌名解析进 PROGRAM_TAGS；
     * "播放爱"会粘连成 keyword=放爱。按组合逐级兜底重解读：
     * 1) 有主词(歌名或分类词)+歌手：精确 -> 歌手LIKE+词LIKE -> 全库模糊词 -> 剥离粘连首字重试
     * 2) 主词全空但有歌手：该歌手全部歌曲随机播（"播放周杰伦的歌"）
     * 3) 什么都没给：等价打开 App 播放
     */
    private void resolveAndPlay(@NonNull Context context, @Nullable String keyword,
                                @Nullable String artist, @Nullable String tags) {
        String art = notBlank(artist) ? artist.trim() : null;
        String word = notBlank(keyword) ? stripPlayPrefix(keyword)
                : (notBlank(tags) ? stripPlayPrefix(tags) : null);

        if (word == null && art == null) {
            openMainActivity(context);
            return;
        }

        // 有主词：走"歌手+歌名"精确匹配（命中即整库定位播放）
        if (word != null) {
            if (tryPlayByTitle(context, art, word)) return;
            // 粘连兜底："放爱"这类动词粘进词首，剥掉首字再试
            if (word.length() > 1 && isStickyLead(word)) {
                if (tryPlayByTitle(context, art, word.substring(1))) return;
            }
            if (art != null) {
                // 歌手存在而歌名怎么都对不上：退化为按歌手随机播（如 歌手=梁静茹 词=情歌 但库里没有该歌手的《情歌》）
                List<Song> byArtist = SongLoader.getSongsByArtistLike(context, art);
                if (!byArtist.isEmpty()) {
                    showToast(context, "未找到《" + word + "》，按歌手随机播放 " + byArtist.size() + " 首");
                    playFromPool(context, byArtist);
                    return;
                }
            }
            showToast(context, "本地未找到: " + word);
            Log.w(TAG, "本地曲库没有匹配: " + word);
            return;
        }

        // 只有歌手（"播放周杰伦的歌"）：该歌手歌曲随机播
        if (art != null) {
            List<Song> byArtist = SongLoader.getSongsByArtistLike(context, art);
            if (!byArtist.isEmpty()) {
                showToast(context, "按歌手播放 " + byArtist.size() + " 首: " + art);
                playFromPool(context, byArtist);
                return;
            }
            showToast(context, "本地未找到歌手: " + art);
        }
    }

    /** 精确/模糊标题匹配：命中则整库为队列定位播放；未命中返回 false 继续兜底 */
    private boolean tryPlayByTitle(@NonNull Context context, @Nullable String art, @NonNull String word) {
        List<Song> hits;
        if (art != null) {
            hits = SongLoader.getSongsByArtistAndTitle(context, art, word);
            if (hits.isEmpty()) {
                hits = SongLoader.getSongsByArtistTitleLike(context, art, word);
            }
        } else {
            hits = SongLoader.getSongs(context, word);
        }
        if (hits.isEmpty()) return false;
        // 整库为队列、命中歌定位（保留原验收行为：语音切歌可切遍整库）
        final List<Song> all = SongLoader.getAllSongs(context);
        int pos = 0;
        if (!all.isEmpty()) {
            long targetId = hits.get(0).id;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id == targetId) { pos = i; break; }
            }
            playSongs(context, all, pos);
        } else {
            playSongs(context, hits, 0);
        }
        Log.d(TAG, "命中 " + hits.size() + " 首: " + word);
        showToast(context, "命中本地 " + hits.size() + " 首: " + word);
        return true;
    }

    /** 歌手/分类池播放：队列=命中集合，随机起点 */
    private void playFromPool(@NonNull Context context, @NonNull List<Song> pool) {
        int start = pool.size() == 1 ? 0 : new java.util.Random().nextInt(pool.size());
        playSongs(context, pool, start);
    }

    /** 动词粘连判定："放爱/播爱/听爱/来爱"这类首字被切进关键词的情况 */
    private boolean isStickyLead(@NonNull String word) {
        char c = word.charAt(0);
        return c == '放' || c == '播' || c == '听' || c == '来';
    }

    private static boolean notBlank(@Nullable String s) {
        return s != null && !s.trim().isEmpty();
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
     * 播放歌曲列表（从指定 position 开始）。MusicService 是异步绑定的，冷启动/语音唤起时
     * 必须先确保服务就绪再 openQueue。
     */
    private void playSongs(@NonNull final Context context, @NonNull final List<Song> songs, final int position) {
        playSongs(context, songs, position, false);
    }

    /**
     * 播放歌曲列表（从指定位置开始，可选开启随机模式）。
     * 冷启动注意：Android 10 禁止后台 receiver startService，
     * 必须用 startForegroundService 通道唤醒 MusicService（服务侧 5 秒内会 startForeground）。
     */
    private void playSongs(@NonNull final Context context, @NonNull final List<Song> songs,
                           final int position, final boolean enableShuffle) {
        try {
            if (MusicPlayerRemote.musicService != null) {
                MusicPlayerRemote.openQueue(songs, position, true);
                if (enableShuffle) {
                    MusicPlayerRemote.musicService.setShuffleMode(MusicService.SHUFFLE_MODE_SHUFFLE);
                }
                return;
            }
            final Context appCtx = context.getApplicationContext();
            final Intent si = new Intent(appCtx, MusicService.class);
            androidx.core.content.ContextCompat.startForegroundService(appCtx, si);
            appCtx.bindService(si,
                    new android.content.ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName name, IBinder service) {
                            MusicPlayerRemote.musicService =
                                    ((MusicService.LocalBinder) service).getService();
                            try {
                                MusicPlayerRemote.openQueue(songs, position, true);
                                if (enableShuffle) {
                                    MusicPlayerRemote.musicService.setShuffleMode(
                                            MusicService.SHUFFLE_MODE_SHUFFLE);
                                }
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
