package top.juluo.ddmusic.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import top.juluo.ddmusic.R;
import top.juluo.ddmusic.loader.SongLoader;
import top.juluo.ddmusic.model.Album;
import top.juluo.ddmusic.ui.adapter.AlbumAdapter;

public class AlbumsFragment extends Fragment {

    private AlbumAdapter adapter;
    private RecyclerView recyclerView;
    private View emptyView;

    private final AlbumAdapter.OnAlbumClickListener albumClickListener = album -> {
        if (getActivity() instanceof SongsFragment.OnSongActionListener) {
            ((SongsFragment.OnSongActionListener) getActivity()).onPlaySongFromLibrary(album.getSongs().get(0));
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_albums, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new AlbumAdapter(requireContext(), albumClickListener);
        recyclerView.setAdapter(adapter);
        reload();
    }

    public void reload() {
        if (getActivity() == null) return;
        adapter.setAlbums(SongLoader.getAlbums(getActivity()));
        if (adapter.getItemCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
}
