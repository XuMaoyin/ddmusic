package top.juluo.ddmusic.loader;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import top.juluo.ddmusic.model.Album;
import top.juluo.ddmusic.model.Artist;
import top.juluo.ddmusic.model.Song;
import top.juluo.ddmusic.util.PlayCountManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地曲库加载：
 * - getAllSongs：整库歌曲（可按名称/添加时间/播放次数排序）
 * - getSongs(query)：歌名 OR 歌手 LIKE
 * - getSongsByArtistAndTitle：歌手 AND 歌名精确匹配（语音"播放梁静茹的情歌"）
 * - getSongByData：按文件路径取歌（恢复队列用）
 */
public class SongLoader {

    public static final int SORT_NAME = 0;
    public static final int SORT_DATE_ADDED = 1;
    public static final int SORT_PLAY_COUNT = 2;

    private static List<Song> cachedAll = null;
    /** 曲库版本号：设置变更或缓存失效时自增，界面据此判断是否需要重新加载列表 */
    private static volatile int revision = 0;

    public static synchronized void invalidate() {
        cachedAll = null;
        revision++;
    }

    public static int getRevision() {
        return revision;
    }

    /** 一个歌曲目录及其歌曲数量，供设置页展示 */
    public static class DirEntry {
        public final String path;
        public final int songCount;

        public DirEntry(String path, int songCount) {
            this.path = path;
            this.songCount = songCount;
        }
    }

    /**
     * 统计所有歌曲所在目录（不过滤排除项，否则被排除的目录会从设置页消失、无法再打开）。
     * 按歌曲数量降序，方便用户先看到占地最大的目录。
     */
    public static List<DirEntry> getDirectoryStats(Context context) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.DATA},
                    null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String data = cursor.getString(0);
                    if (data == null) continue;
                    int slash = data.lastIndexOf('/');
                    if (slash <= 0) continue;
                    String dir = data.substring(0, slash);
                    Integer old = counts.get(dir);
                    counts.put(dir, old == null ? 1 : old + 1);
                }
            }
        } catch (SecurityException e) {
        } catch (Exception e) {
        } finally {
            if (cursor != null) cursor.close();
        }
        List<DirEntry> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            out.add(new DirEntry(e.getKey(), e.getValue()));
        }
        Collections.sort(out, (a, b) -> {
            if (a.songCount != b.songCount) return b.songCount - a.songCount;
            return a.path.compareToIgnoreCase(b.path);
        });
        return out;
    }

    /** 全部歌曲（名称排序，含缓存），无音乐时返回空列表 */
    public static List<Song> getAllSongs(Context context) {
        return getAllSongs(context, SORT_NAME);
    }

    /** 全部歌曲（按指定排序模式，无音乐时返回空列表） */
    public static List<Song> getAllSongs(Context context, int sortMode) {
        List<Song> base = getBase(context);
        if (base.isEmpty()) return new ArrayList<>();
        List<Song> out = new ArrayList<>(base);
        switch (sortMode) {
            case SORT_DATE_ADDED:
                Collections.sort(out, (a, b) -> Long.compare(b.dateAdded, a.dateAdded));
                break;
            case SORT_PLAY_COUNT:
                Collections.sort(out, (a, b) -> Integer.compare(
                        PlayCountManager.getCount(context, b.id),
                        PlayCountManager.getCount(context, a.id)));
                break;
            case SORT_NAME:
            default:
                Collections.sort(out, (a, b) -> a.title.compareToIgnoreCase(b.title));
                break;
        }
        return out;
    }

    private static List<Song> getBase(Context context) {
        if (cachedAll != null) return new ArrayList<>(cachedAll);
        List<Song> songs = query(context, null, null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
        if (songs.isEmpty()) {
            cachedAll = null;
        } else {
            cachedAll = songs;
        }
        return new ArrayList<>(songs);
    }

    /** 歌名 OR 歌手 模糊搜索 */
    public static List<Song> getSongs(Context context, String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllSongs(context);
        }
        String q = "%" + query.trim() + "%";
        String selection = MediaStore.Audio.Media.TITLE + " LIKE ? OR " + MediaStore.Audio.Media.ARTIST + " LIKE ?";
        String[] args = {q, q};
        return query(context, selection, args, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
    }

    /** 歌手 AND 歌名 精确匹配（都非空时）；任一项为空则退回对应模糊搜索 */
    public static List<Song> getSongsByArtistAndTitle(Context context, String artist, String title) {
        if (artist != null && !artist.trim().isEmpty() && title != null && !title.trim().isEmpty()) {
            String selection = MediaStore.Audio.Media.ARTIST + " = ? AND " + MediaStore.Audio.Media.TITLE + " = ?";
            String[] args = {artist.trim(), title.trim()};
            return query(context, selection, args, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
        }
        if (title != null && !title.trim().isEmpty()) {
            return getSongs(context, title.trim());
        }
        if (artist != null && !artist.trim().isEmpty()) {
            String q = "%" + artist.trim() + "%";
            return query(context, MediaStore.Audio.Media.ARTIST + " LIKE ?", new String[]{q},
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
        }
        return getAllSongs(context);
    }

    /** 按文件路径取歌（队列恢复用） */
    public static Song getSongByData(Context context, String data) {
        if (data == null) return null;
        List<Song> songs = query(context, MediaStore.Audio.Media.DATA + " = ?", new String[]{data}, null);
        return songs.isEmpty() ? null : songs.get(0);
    }

    /** 专辑列表（来自全部歌曲分组，按专辑名排序） */
    public static List<Album> getAlbums(Context context) {
        Map<Long, Album> map = new LinkedHashMap<>();
        for (Song song : getAllSongs(context)) {
            Album album = map.get(song.albumId);
            if (album == null) {
                album = new Album(song.albumId, song.albumName, song.artistName, song.year, song.artistId);
                map.put(song.albumId, album);
            }
            album.addSong(song);
        }
        List<Album> albums = new ArrayList<>(map.values());
        Collections.sort(albums, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return albums;
    }

    /** 歌手列表（来自全部歌曲分组，按歌手名排序） */
    public static List<Artist> getArtists(Context context) {
        Map<Long, Artist> map = new LinkedHashMap<>();
        for (Song song : getAllSongs(context)) {
            Artist artist = map.get(song.artistId);
            if (artist == null) {
                artist = new Artist(song.artistId, song.artistName);
                map.put(song.artistId, artist);
            }
            artist.addSong(song);
        }
        List<Artist> artists = new ArrayList<>(map.values());
        Collections.sort(artists, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return artists;
    }

    private static List<Song> query(Context context, String selection, String[] args, String orderBy) {
        List<Song> songs = new ArrayList<>();
        // 一次性取出被排除目录，避免每首歌都读一次 SharedPreferences
        java.util.Set<String> excluded = top.juluo.ddmusic.util.ScanSettings.getExcludedDirs(context);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    Song.getProjection(),
                    selection,
                    args,
                    orderBy == null ? MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC" : orderBy);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Song song = fromCursor(cursor);
                    if (song.duration > 0
                            && !top.juluo.ddmusic.util.ScanSettings.isExcluded(excluded, song.data)) {
                        songs.add(song);
                    }
                }
            }
        } catch (SecurityException e) {
        } catch (Exception e) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return songs;
    }

    private static Song fromCursor(Cursor c) {
        return new Song(
                c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)),
                c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)),
                c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)),
                safeString(c, MediaStore.Audio.Media.TITLE),
                safeString(c, MediaStore.Audio.Media.ARTIST),
                safeString(c, MediaStore.Audio.Media.ALBUM),
                c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
                c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)),
                safeString(c, MediaStore.Audio.Media.DATA),
                c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)),
                c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)),
                c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
        );
    }

    private static String safeString(Cursor c, String column) {
        try {
            String s = c.getString(c.getColumnIndexOrThrow(column));
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    private SongLoader() {
    }
}
