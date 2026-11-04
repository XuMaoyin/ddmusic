package top.juluo.ddmusic.ui.adapter;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import top.juluo.ddmusic.R;
import top.juluo.ddmusic.model.Album;

import java.util.ArrayList;
import java.util.List;

public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.VH> {

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album);
    }

    private final Context context;
    private final List<Album> albums = new ArrayList<>();
    private final OnAlbumClickListener listener;

    public AlbumAdapter(Context context, OnAlbumClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setAlbums(List<Album> newAlbums) {
        albums.clear();
        if (newAlbums != null) albums.addAll(newAlbums);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Album album = albums.get(position);
        holder.title.setText(album.name);
        holder.subtitle.setText(album.artistName + " · " + album.getSongs().size() + "首");
        Uri art = Uri.parse("content://media/external/audio/albumart/" + album.id);
        Glide.with(context).load(art).placeholder(R.color.primaryDark).into(holder.cover);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAlbumClick(album);
        });
    }

    @Override
    public int getItemCount() {
        return albums.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;
        TextView subtitle;

        VH(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.cover);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
        }
    }
}
