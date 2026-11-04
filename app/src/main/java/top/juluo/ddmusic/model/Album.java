package top.juluo.ddmusic.model;

import java.util.ArrayList;
import java.util.List;

public class Album {
    public final long id;
    public final String name;
    public final String artistName;
    public final int year;
    public final long artistId;
    private final List<Song> songs = new ArrayList<>();

    public Album(long id, String name, String artistName, int year, long artistId) {
        this.id = id;
        this.name = name;
        this.artistName = artistName;
        this.year = year;
        this.artistId = artistId;
    }

    public void addSong(Song song) {
        songs.add(song);
    }

    public List<Song> getSongs() {
        return songs;
    }
}
