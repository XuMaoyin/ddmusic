package top.juluo.ddmusic.model;

import java.util.ArrayList;
import java.util.List;

public class Artist {
    public final long id;
    public final String name;
    private final List<Song> songs = new ArrayList<>();

    public Artist(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public void addSong(Song song) {
        songs.add(song);
    }

    public List<Song> getSongs() {
        return songs;
    }
}
