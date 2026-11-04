package top.juluo.ddmusic.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import top.juluo.ddmusic.R;
import top.juluo.ddmusic.loader.SongLoader;
import top.juluo.ddmusic.util.LibraryScanner;
import top.juluo.ddmusic.util.UiSettings;

/**
 * 设置页（一级）：
 * <ul>
 *   <li>播放行为——启动时是否自动播放并跳转播放页</li>
 *   <li>播放页——横屏时歌词放在左侧还是右侧</li>
 *   <li>歌曲列表——默认排序方式</li>
 *   <li>曲库维护——主动扫描；曲库扫描目录、关于为二级页入口</li>
 * </ul>
 */
public class SettingsActivity extends AppCompatActivity {

    private SwitchCompat switchAutoPlay;
    private SwitchCompat switchLyricsLeft;
    private TextView autoPlayDesc;
    private TextView lyricsSideDesc;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindSwitches();
        setupSortMode();
        setupScan();
        setupNavigation();
    }

    // ---------------- 列表排序 ----------------

    private TextView sortModeDesc;

    private void setupSortMode() {
        sortModeDesc = findViewById(R.id.sort_mode_desc);
        updateSortDesc();
        findViewById(R.id.row_sort_mode).setOnClickListener(v -> showSortDialog());
    }

    private void updateSortDesc() {
        if (sortModeDesc == null) return;
        int mode = UiSettings.getSortMode(this);
        sortModeDesc.setText(getString(R.string.settings_sort_current,
                getString(UiSettings.getSortLabelRes(mode))));
    }

    private void showSortDialog() {
        final int[] modes = {
                SongLoader.SORT_NAME,
                SongLoader.SORT_DATE_ADDED,
                SongLoader.SORT_PLAY_COUNT
        };
        final CharSequence[] labels = {
                getString(R.string.settings_sort_name),
                getString(R.string.settings_sort_date),
                getString(R.string.settings_sort_play_count)
        };
        int checked = 0;
        int current = UiSettings.getSortMode(this);
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_sort_mode)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    UiSettings.setSortMode(this, modes[which]);
                    updateSortDesc();
                    // 让主界面返回时自动按新排序重排列表
                    SongLoader.invalidate();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---------------- 开关类设置 ----------------

    private void bindSwitches() {
        switchAutoPlay = findViewById(R.id.switch_auto_play);
        autoPlayDesc = findViewById(R.id.auto_play_desc);
        switchLyricsLeft = findViewById(R.id.switch_lyrics_left);
        lyricsSideDesc = findViewById(R.id.lyrics_side_desc);

        boolean autoPlay = UiSettings.isAutoPlayOnLaunch(this);
        switchAutoPlay.setChecked(autoPlay);
        updateAutoPlayDesc(autoPlay);
        switchAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UiSettings.setAutoPlayOnLaunch(this, isChecked);
            updateAutoPlayDesc(isChecked);
        });
        // 整行可点：开关本身可点时，点开关走它自己的回调，不会重复触发
        findViewById(R.id.row_auto_play).setOnClickListener(v -> switchAutoPlay.toggle());

        boolean lyricsLeft = UiSettings.isLyricsOnLeft(this);
        switchLyricsLeft.setChecked(lyricsLeft);
        updateLyricsSideDesc(lyricsLeft);
        switchLyricsLeft.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UiSettings.setLyricsOnLeft(this, isChecked);
            updateLyricsSideDesc(isChecked);
        });
        findViewById(R.id.row_lyrics_left).setOnClickListener(v -> switchLyricsLeft.toggle());
    }

    private void updateAutoPlayDesc(boolean enabled) {
        autoPlayDesc.setText(enabled
                ? R.string.settings_auto_play_on : R.string.settings_auto_play_off);
    }

    private void updateLyricsSideDesc(boolean lyricsLeft) {
        lyricsSideDesc.setText(lyricsLeft
                ? R.string.settings_lyrics_side_left : R.string.settings_lyrics_side_right);
    }

    // ---------------- 扫描歌曲 ----------------

    private View rowScanSongs;
    private TextView scanStatus;
    private TextView scanAction;
    private boolean scanning;

    private void setupScan() {
        rowScanSongs = findViewById(R.id.row_scan_songs);
        scanStatus = findViewById(R.id.scan_status);
        scanAction = findViewById(R.id.scan_action);
        rowScanSongs.setOnClickListener(v -> startScan());
    }

    private void startScan() {
        if (scanning) return;
        scanning = true;
        rowScanSongs.setEnabled(false);
        scanAction.setText(R.string.settings_scanning);
        scanStatus.setText(R.string.settings_scan_hint);

        LibraryScanner.start(this, new LibraryScanner.Listener() {
            @Override
            public void onProgress(int found, int scanned) {
                if (isFinishing() || isDestroyed()) return;
                scanStatus.setText(getString(R.string.settings_scan_progress, scanned, found));
            }

            @Override
            public void onDone(int found, int scanned, int libraryCount) {
                scanning = false;
                if (isFinishing() || isDestroyed()) return;
                rowScanSongs.setEnabled(true);
                scanAction.setText(R.string.settings_scan_action);
                scanStatus.setText(found == 0
                        ? getString(R.string.settings_scan_empty)
                        : getString(R.string.settings_scan_done, found, libraryCount));
            }
        });
    }

    // ---------------- 二级页入口 ----------------

    private void setupNavigation() {
        findViewById(R.id.row_scan_dirs).setOnClickListener(v ->
                startActivity(new Intent(this, ScanDirsActivity.class)));
        findViewById(R.id.row_about).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
    }
}
