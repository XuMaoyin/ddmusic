package top.juluo.ddmusic.activity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import top.juluo.ddmusic.R;
import top.juluo.ddmusic.helper.MusicPlayerRemote;
import top.juluo.ddmusic.loader.SongLoader;
import top.juluo.ddmusic.model.Song;
import top.juluo.ddmusic.receiver.KuwoVoiceReceiver;
import top.juluo.ddmusic.service.MusicService;
import top.juluo.ddmusic.ui.adapter.SongAdapter;

import java.util.List;

/**
 * 曲库主界面（仅歌曲列表 + 搜索 + 排序）+ 底部迷你播放栏。
 * 打开 APP / 语音"播放音乐"：自动续播或从头播全部，并跳转横屏播放页。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_STORAGE = 100;

    private RecyclerView recyclerView;
    private EditText searchInput;
    private TextView emptyView;
    private ImageButton btnSort;
    private ImageButton btnLocate;
    private ImageButton btnSettings;
    private LinearLayout miniPlayer;
    private ImageView miniCover;
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageButton miniPlayPause;

    private SongAdapter adapter;
    private List<Song> currentList;

    /** 当前排序：名称 / 添加时间 / 播放次数 */
    private int sortMode = SongLoader.SORT_NAME;

    /** 打开 APP / 语音"播放音乐"时，服务就绪后自动播放并跳播放页；语音搜具体歌时不触发 */
    private boolean autoPlayPending = true;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            MusicService.LocalBinder lb = (MusicService.LocalBinder) binder;
            MusicPlayerRemote.musicService = lb.getService();
            MusicService.addEventListener(musicEventListener);
            updateMiniPlayer();
            updatePlayingIndicator();
            if (autoPlayPending) {
                autoPlayPending = false;
                playMusicAndExpand();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    private final MusicService.MusicEventListener musicEventListener = new MusicService.MusicEventListener() {
        @Override
        public void onPlayStateChanged() {
            updateMiniPlayer();
            updatePlayingIndicator();
        }

        @Override
        public void onSongChanged() {
            updateMiniPlayer();
            updatePlayingIndicator();
        }

        @Override
        public void onQueueChanged() {
            updateMiniPlayer();
            updatePlayingIndicator();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 是否"打开即播放并进入播放页"由设置决定；
        // 语音指令仍会强制自动播放（handleCarIntent 里会把 autoPlayPending 置回 true）
        autoPlayPending = top.juluo.ddmusic.util.UiSettings.isAutoPlayOnLaunch(this);
        // 列表默认排序由设置决定（初始值 = 按最近添加）
        sortMode = top.juluo.ddmusic.util.UiSettings.getSortMode(this);
        bindViews();
        setupList();
        setupSearch();
        setupSort();
        setupLocate();
        setupSettings();
        setupMiniPlayer();
        requestStoragePermission();
        bindService(new Intent(this, MusicService.class), serviceConnection, BIND_AUTO_CREATE);
        handleCarIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCarIntent(intent);
    }

    /** 曲库版本号：从设置页返回（或曲库被其它地方置为失效）时据此刷新列表 */
    private int lastLibraryRevision = -1;

    @Override
    protected void onResume() {
        super.onResume();
        // 设置页里可能改过默认排序：先同步到本地字段，再决定是否重排
        int savedSort = top.juluo.ddmusic.util.UiSettings.getSortMode(this);
        boolean sortChanged = savedSort != sortMode;
        if (sortChanged) sortMode = savedSort;

        int revision = SongLoader.getRevision();
        if (sortChanged || (lastLibraryRevision >= 0 && revision != lastLibraryRevision)) {
            loadList(searchInput == null ? "" : searchInput.getText().toString());
            updateMiniPlayer();
        }
        lastLibraryRevision = revision;
    }

    @Override
    protected void onDestroy() {
        MusicService.removeEventListener(musicEventListener);
        try {
            unbindService(serviceConnection);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.recycler_view);
        searchInput = findViewById(R.id.search_input);
        emptyView = findViewById(R.id.empty_view);
        btnSort = findViewById(R.id.btn_sort);
        btnLocate = findViewById(R.id.btn_locate);
        btnSettings = findViewById(R.id.btn_settings);
        miniPlayer = findViewById(R.id.mini_player);
        miniCover = findViewById(R.id.mini_cover);
        miniTitle = findViewById(R.id.mini_title);
        miniArtist = findViewById(R.id.mini_artist);
        miniPlayPause = findViewById(R.id.mini_play_pause);
    }

    private void setupList() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SongAdapter(this, (song, position) -> playSongAndOpen(song));
        recyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupSort() {
        btnSort.setOnClickListener(v -> {
            sortMode = (sortMode + 1) % 3;
            // 与设置页共用同一个值：这里改完，设置页的"当前"也会同步
            top.juluo.ddmusic.util.UiSettings.setSortMode(this, sortMode);
            Toast.makeText(this, "排序：" + getString(
                    top.juluo.ddmusic.util.UiSettings.getSortLabelRes(sortMode)),
                    Toast.LENGTH_SHORT).show();
            loadList(searchInput.getText().toString());
        });
    }

    /** 定位到当前正在播放的歌曲：清空搜索、加载全部、滚动到该位置 */
    private void setupLocate() {
        btnLocate.setOnClickListener(v -> {
            Song current = MusicPlayerRemote.getCurrentSong();
            if (current == null) {
                Toast.makeText(this, "当前没有在播放的歌曲", Toast.LENGTH_SHORT).show();
                return;
            }
            searchInput.setText("");
            loadList("");
            if (currentList != null) {
                for (int i = 0; i < currentList.size(); i++) {
                    if (currentList.get(i).id == current.id) {
                        recyclerView.scrollToPosition(i);
                        Toast.makeText(this, "已定位到当前播放", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
            Toast.makeText(this, "当前播放歌曲不在列表中", Toast.LENGTH_SHORT).show();
        });
    }

    /** 打开设置页（曲库扫描目录排除等） */
    private void setupSettings() {
        btnSettings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            } catch (Exception ignored) {
            }
        });
    }

    /** 按当前排序 + 搜索词加载列表 */
    private void loadList(String query) {
        if (query == null || query.trim().isEmpty()) {
            currentList = SongLoader.getAllSongs(this, sortMode);
        } else {
            currentList = SongLoader.getSongs(this, query.trim());
        }
        adapter.setSongs(currentList);
        updateEmpty();
        updatePlayingIndicator();
    }

    /** 列表里标记"正在播放"的曲目：在封面中央显示跳动的频谱 */
    private void updatePlayingIndicator() {
        if (adapter == null) return;
        Song current = MusicPlayerRemote.getCurrentSong();
        adapter.setPlayingState(current == null ? -1 : current.id, MusicPlayerRemote.isPlaying());
    }

    private void updateEmpty() {
        if (adapter.getItemCount() == 0) {
            // 区分"曲库为空"和"搜索/排除后没有匹配"，否则用户会误以为本地没有音乐
            boolean searching = searchInput != null && searchInput.getText().length() > 0;
            emptyView.setText(searching ? R.string.search_no_result : R.string.no_music_found);
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void setupMiniPlayer() {
        miniPlayer.setOnClickListener(v -> openPlayer());
        miniPlayPause.setOnClickListener(v -> MusicPlayerRemote.playOrPause());
        updateMiniPlayer();
    }

    private void updateMiniPlayer() {
        Song song = MusicPlayerRemote.getCurrentSong();
        boolean visible = song != null;
        miniPlayer.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        miniTitle.setText(song.title);
        miniArtist.setText(song.artistName);
        Glide.with(this)
                .load(android.net.Uri.parse("content://media/external/audio/albumart/" + song.albumId))
                .placeholder(R.color.primaryDark)
                .into(miniCover);
        miniPlayPause.setImageResource(MusicPlayerRemote.isPlaying()
                ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp);
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
        } else {
            loadList("");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            SongLoader.invalidate();
            loadList("");
        } else if (requestCode == REQ_STORAGE) {
            Toast.makeText(this, "需要存储权限才能读取本地音乐", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------- 播放行为 ----------------

    /** 点列表歌曲：整库队列定位播放 + 跳播放页 */
    public void playSongAndOpen(Song song) {
        List<Song> all = SongLoader.getAllSongs(this);
        int pos = indexOf(all, song);
        MusicPlayerRemote.openQueue(all, pos, true);
        openPlayer();
    }

    private int indexOf(List<Song> songs, Song target) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id == target.id) return i;
        }
        return 0;
    }

    /** 打开 APP / 语音"播放音乐"：续播或从头播全部，并跳播放页 */
    private void playMusicAndExpand() {
        if (!MusicPlayerRemote.getPlayingQueue().isEmpty()) {
            MusicPlayerRemote.resumePlaying();
        } else {
            List<Song> all = SongLoader.getAllSongs(this);
            if (!all.isEmpty()) {
                MusicPlayerRemote.openQueue(all, 0, true);
            }
        }
        openPlayer();
    }

    private void openPlayer() {
        try {
            startActivity(new Intent(this, PlayerActivity.class));
        } catch (Exception ignored) {
        }
    }

    private static long lastCarIntentTime = 0;

    /** 小迪语音（酷我协议 activity intent / 广播统一入口，双通道去重） */
    private void handleCarIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Bundle extras = intent.getExtras();
        boolean hasCarAction = action != null
                && (action.startsWith("cn.kuwo.kwmusicauto.action.")
                || "cn.kuwo.music.car.actions".equals(action));
        boolean hasSongExtra = extras != null
                && (extras.containsKey("name") || extras.containsKey("songname")
                || extras.containsKey("songName") || extras.containsKey("song")
                || extras.containsKey("keyword") || extras.containsKey("query"));
        if (hasCarAction || hasSongExtra) {
            long now = System.currentTimeMillis();
            if (now - lastCarIntentTime < 2000) {
                return;
            }
            lastCarIntentTime = now;
            if (hasConcreteSongQuery(extras)) {
                autoPlayPending = false;
                try {
                    new KuwoVoiceReceiver().onReceive(this, intent);
                } catch (Exception ignored) {
                }
            } else {
                autoPlayPending = true;
                if (MusicPlayerRemote.musicService != null) {
                    autoPlayPending = false;
                    playMusicAndExpand();
                }
            }
        }
    }

    private boolean hasConcreteSongQuery(@Nullable Bundle extras) {
        if (extras == null) return false;
        String[] keys = {"name", "songname", "songName", "song", "keyword", "query", "title", "value", "content", "search"};
        for (String k : keys) {
            Object v = extras.get(k);
            if (v != null) {
                String s = v.toString().trim();
                if (!s.isEmpty()) return true;
            }
        }
        return false;
    }
}
