package top.juluo.ddmusic.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import top.juluo.ddmusic.R;
import top.juluo.ddmusic.loader.SongLoader;
import top.juluo.ddmusic.util.ScanSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 设置页的"扫描目录"列表：每行一个目录，开关表示该目录是否参与曲库扫描。
 */
public class DirectoryAdapter extends RecyclerView.Adapter<DirectoryAdapter.VH> {

    public interface OnToggleListener {
        /** @param included true 表示重新纳入扫描，false 表示排除 */
        void onToggle(String dir, boolean included);
    }

    private final Context context;
    private final OnToggleListener listener;
    private final List<SongLoader.DirEntry> items = new ArrayList<>();

    public DirectoryAdapter(Context context, OnToggleListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<SongLoader.DirEntry> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_setting_dir, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        SongLoader.DirEntry entry = items.get(position);
        Set<String> excluded = ScanSettings.getExcludedDirs(context);
        boolean isExcluded = ScanSettings.isDirExcluded(excluded, entry.path);

        // 去掉 /storage/emulated/0/ 前缀再显示：否则中间省略号会把
        // Download/Sync/music 和它的子目录截成同一个样子，用户无法区分
        holder.path.setText(displayPath(entry.path));
        holder.path.setContentDescription(entry.path);
        holder.count.setText(isExcluded
                ? context.getString(R.string.settings_dir_excluded) + " · "
                    + context.getString(R.string.settings_dir_songs, entry.songCount)
                : context.getString(R.string.settings_dir_songs, entry.songCount));

        // 复用行时必须先解绑，否则 setChecked 会触发上一行的回调，造成误改
        holder.toggle.setOnCheckedChangeListener(null);
        holder.toggle.setChecked(!isExcluded);
        holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) listener.onToggle(entry.path, isChecked);
        });
    }

    /** 内部存储根目录下的路径去掉冗长前缀，保留能区分不同目录的尾部 */
    private static String displayPath(String path) {
        if (path == null) return "";
        String prefix = "/storage/emulated/0/";
        if (path.startsWith(prefix)) return path.substring(prefix.length());
        return path;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView path;
        final TextView count;
        final SwitchCompat toggle;

        VH(@NonNull View itemView) {
            super(itemView);
            path = itemView.findViewById(R.id.dir_path);
            count = itemView.findViewById(R.id.dir_count);
            toggle = itemView.findViewById(R.id.dir_switch);
        }
    }
}
