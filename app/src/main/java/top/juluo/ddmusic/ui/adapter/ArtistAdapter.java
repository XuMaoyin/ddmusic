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
import top.juluo.ddmusic.model.Artist;
import top.juluo.ddmusic.model.Song;

import java.util.ArrayList;
import java.util.List;

public class ArtistAdapter extends RecyclerView.Adapter<ArtistAdapter.VH> {

    public interface OnArtistClickListener {
        void onArtistClick(Artist artist);
    }

    private final Context context;
    private final List<Artist> artists = new ArrayList<>();
    private final OnArtistClickListener listener;

    public ArtistAdapter(Context context, OnArtistClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setArtists(List<Artist> newArtists) {
        artists.clear();
        if (newArtists != null) artists.addAll(newArtists);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_artist, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Artist artist = artists.get(position);
        holder.title.setText(artist.name);
        if (!artist.getSongs().isEmpty()) {
            Song first = artist.getSongs().get(0);
            Glide.with(context)
                    .load(Uri.parse("content://media/external/audio/albumart/" + first.albumId))
                    .placeholder(R.color.primaryDark)
                    .into(holder.cover);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onArtistClick(artist);
        });
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;

        VH(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.cover);
            title = itemView.findViewById(R.id.title);
        }
    }
}
