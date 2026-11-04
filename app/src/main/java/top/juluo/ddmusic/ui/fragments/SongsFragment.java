package top.juluo.ddmusic.ui.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import top.juluo.ddmusic.R;
import top.juluo.ddmusic.loader.SongLoader;
import top.juluo.ddmusic.model.Song;
import top.juluo.ddmusic.ui.adapter.SongAdapter;

import java.util.List;

public class SongsFragment extends Fragment {

    private SongAdapter adapter;
    private EditText searchInput;
    private RecyclerView recyclerView;
    private View emptyView;
    private List<Song> allSongs;

    private final SongAdapter.OnSongClickListener songClickListener =
            (song, position) -> {
                if (getActivity() instanceof OnSongActionListener) {
                    ((OnSongActionListener) getActivity()).onPlaySongFromLibrary(song);
                }
            };

    public interface OnSongActionListener {
        void onPlaySongFromLibrary(Song song);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_songs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        searchInput = view.findViewById(R.id.search_input);
        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new SongAdapter(requireContext(), songClickListener);
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        reload();
    }

    private void filter(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.setSongs(allSongs);
        } else {
            adapter.setSongs(SongLoader.getSongs(requireContext(), query.trim()));
        }
        updateEmpty();
    }

    public void reload() {
        if (getActivity() == null) return;
        allSongs = SongLoader.getAllSongs(getActivity());
        adapter.setSongs(allSongs);
        updateEmpty();
    }

    private void updateEmpty() {
        if (adapter.getItemCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
}
