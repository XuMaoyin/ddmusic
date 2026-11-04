package top.juluo.ddmusic.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import top.juluo.ddmusic.R;
import top.juluo.ddmusic.loader.SongLoader;
import top.juluo.ddmusic.ui.adapter.DirectoryAdapter;
import top.juluo.ddmusic.util.ScanSettings;

import java.util.List;

/**
 * 曲库扫描目录（二级页）：列出曲库中出现过的目录，按开关决定是否参与扫描。
 * 排除按路径前缀生效，关掉父目录会连带排除其子目录。
 */
public class ScanDirsActivity extends AppCompatActivity implements DirectoryAdapter.OnToggleListener {

    private RecyclerView dirList;
    private TextView emptyView;
    private DirectoryAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_dirs);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        dirList = findViewById(R.id.dir_list);
        emptyView = findViewById(R.id.empty_view);
        dirList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DirectoryAdapter(this, this);
        dirList.setAdapter(adapter);

        loadDirectories();
    }

    private void loadDirectories() {
        List<SongLoader.DirEntry> dirs = SongLoader.getDirectoryStats(this);
        adapter.setItems(dirs);
        boolean isEmpty = dirs.isEmpty();
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        dirList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onToggle(String dir, boolean included) {
        if (included) {
            ScanSettings.setIncluded(this, dir);
        } else {
            ScanSettings.setExcluded(this, dir, true);
        }
        // 曲库缓存必须失效，否则回到主界面还是旧列表
        SongLoader.invalidate();
        // 排除父目录会影响子目录的显示状态，整表重刷保证开关与实际一致
        adapter.notifyDataSetChanged();
    }
}
