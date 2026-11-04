package top.juluo.ddmusic.model;

import android.provider.MediaStore;

/**
 * 本地歌曲模型。字段与 MediaStore.Audio.Media 一一对应。
 */
public class Song {
    public final long id;
    public final long albumId;
    public final long artistId;
    public final String title;
    public final String artistName;
    public final String albumName;
    public final int duration;
    public final int year;
    public final String data;          // 文件绝对路径
    public final long dateAdded;
    public final long dateModified;
    public final int trackNumber;

    public Song(long id, long albumId, long artistId, String title, String artistName,
                String albumName, int duration, int year, String data,
                long dateAdded, long dateModified, int trackNumber) {
        this.id = id;
        this.albumId = albumId;
        this.artistId = artistId;
        this.title = title;
        this.artistName = artistName;
        this.albumName = albumName;
        this.duration = duration;
        this.year = year;
        this.data = data;
        this.dateAdded = dateAdded;
        this.dateModified = dateModified;
        this.trackNumber = trackNumber;
    }

    /** 构造查询投影 */
    public static String[] getProjection() {
        return new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.TRACK
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song)) return false;
        return ((Song) o).id == id;
    }

    @Override
    public int hashCode() {
        return (int) id;
    }
}
