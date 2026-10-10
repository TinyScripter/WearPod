package de.danoeh.antennapod.net.sync.wearinterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.danoeh.antennapod.model.feed.Chapter;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.danoeh.antennapod.model.feed.FeedMedia;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class WearSerializer {
    private static final String KEY_EPISODE_ID = "episode_id";
    private static final String KEY_FEED_ID = "feed_id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_PUB_DATE = "pub_date";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_POSITION = "position";
    private static final String KEY_IS_PLAYING = "is_playing";
    private static final String KEY_LAST_PLAYED = "last_played";
    private static final String KEY_PLAYED = "played";
    private static final String KEY_GUID = "guid";
    private static final String KEY_FEED_TITLE = "feed_title";
    private static final String KEY_DOWNLOAD_URL = "download_url";
    private static final String KEY_MIME_TYPE = "mime_type";
    private static final String KEY_SIZE = "size";
    private static final String KEY_REWIND_SECS = "rewind_secs";
    private static final String KEY_FAST_FORWARD_SECS = "fast_forward_secs";
    private static final String KEY_SMART_MARK_AS_PLAYED_SECS = "smart_mark_as_played_secs";
    private static final String KEY_PLAYBACK_SPEED = "playback_speed";
    private static final String KEY_START_POSITION = "start_position";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_COMPLETED = "completed";
    private static final String KEY_PLAYBACK_SPEEDS = "playback_speeds";
    private static final String KEY_FEED_PLAYBACK_SPEED = "feed_playback_speed";
    private static final String KEY_START = "start";
    private static final int MAX_CHAPTERS = 500;

    private WearSerializer() {
    }

    @NonNull
    private static JSONObject episodeToJson(@NonNull FeedItem item) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_EPISODE_ID, item.getId());
        obj.put(KEY_TITLE, item.getTitle() != null ? item.getTitle() : "");
        obj.put(KEY_PUB_DATE, item.getPubDate() != null ? item.getPubDate().getTime() : 0);
        obj.put(KEY_DURATION, item.getMedia() != null ? item.getMedia().getDuration() : 0);
        obj.put(KEY_POSITION, item.getMedia() != null ? item.getMedia().getPosition() : 0);
        return obj;
    }

    @NonNull
    private static FeedItem episodeFromJson(@NonNull JSONObject obj) throws JSONException {
        FeedItem item = new FeedItem();
        item.setId(obj.optLong(KEY_EPISODE_ID, -1));
        item.setTitle(obj.optString(KEY_TITLE, ""));
        long pubDate = obj.optLong(KEY_PUB_DATE, 0);
        if (pubDate != 0) {
            item.setPubDate(new Date(pubDate));
        }
        int duration = obj.optInt(KEY_DURATION, 0);
        int position = obj.optInt(KEY_POSITION, 0);
        long size = obj.optLong(KEY_SIZE, 0);
        String mimeType = obj.optString(KEY_MIME_TYPE, null);
        String downloadUrl = obj.optString(KEY_DOWNLOAD_URL, null);
        long lastPlayed = obj.optLong(KEY_LAST_PLAYED, 0);
        Date lastPlayedDate = lastPlayed != 0 ? new Date(lastPlayed) : null;
        FeedMedia media = new FeedMedia(0, item, duration, position, size, mimeType, null, downloadUrl,
                0, lastPlayedDate, 0, 0L);
        item.setMedia(media);
        if (obj.has(KEY_PLAYED)) {
            item.setPlayed(obj.optBoolean(KEY_PLAYED, false));
        }
        item.setItemIdentifier(obj.optString(KEY_GUID, null));
        String feedTitle = obj.optString(KEY_FEED_TITLE, null);
        if (feedTitle != null) {
            Feed feed = new Feed(null, null);
            feed.setTitle(feedTitle);
            if (obj.has(KEY_FEED_PLAYBACK_SPEED)) {
                FeedPreferences preferences = new FeedPreferences(0, FeedPreferences.AutoDownloadSetting.GLOBAL,
                        FeedPreferences.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF,
                        FeedPreferences.NewEpisodesAction.GLOBAL, null, null);
                preferences.setFeedPlaybackSpeed((float) obj.optDouble(KEY_FEED_PLAYBACK_SPEED,
                        FeedPreferences.SPEED_USE_GLOBAL));
                feed.setPreferences(preferences);
            }
            item.setFeed(feed);
        }
        return item;
    }

    @NonNull
    private static JSONObject playbackStateToJson(@NonNull FeedItem item) throws JSONException {
        JSONObject obj = episodeToJson(item);
        obj.put(KEY_PLAYED, item.isPlayed());
        if (item.getMedia() != null && item.getMedia().getLastPlayedTimeHistory() != null) {
            obj.put(KEY_LAST_PLAYED, item.getMedia().getLastPlayedTimeHistory().getTime());
        }
        return obj;
    }

    @NonNull
    public static byte[] episodesToBytes(@NonNull List<FeedItem> items) {
        JSONArray array = new JSONArray();
        for (FeedItem item : items) {
            try {
                array.put(episodeToJson(item));
            } catch (JSONException e) {
                // skip malformed item
            }
        }
        return array.toString().getBytes(StandardCharsets.UTF_8);
    }

    @NonNull
    public static List<FeedItem> episodesFromBytes(@NonNull byte[] data) {
        List<FeedItem> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                items.add(episodeFromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // return whatever we managed to parse
        }
        return items;
    }

    @NonNull
    public static byte[] episodeDetailsToBytes(@NonNull FeedItem item) {
        try {
            JSONObject obj = playbackStateToJson(item);
            obj.put(KEY_GUID, item.getItemIdentifier());
            if (item.getFeed() != null) {
                obj.put(KEY_FEED_TITLE, item.getFeed().getTitle());
                if (item.getFeed().getPreferences() != null) {
                    obj.put(KEY_FEED_PLAYBACK_SPEED, item.getFeed().getPreferences().getFeedPlaybackSpeed());
                }
            }
            FeedMedia media = item.getMedia();
            if (media != null) {
                obj.put(KEY_DOWNLOAD_URL, media.getDownloadUrl());
                obj.put(KEY_MIME_TYPE, media.getMimeType());
                obj.put(KEY_SIZE, media.getSize());
            }
            return obj.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            return new byte[0];
        }
    }

    @Nullable
    public static FeedItem episodeDetailsFromBytes(@NonNull byte[] data) {
        if (data.length == 0) {
            return null;
        }
        try {
            return episodeFromJson(new JSONObject(new String(data, StandardCharsets.UTF_8)));
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    public static byte[] idsToBytes(@NonNull List<Long> ids) {
        JSONArray array = new JSONArray();
        for (long id : ids) {
            array.put(id);
        }
        return array.toString().getBytes(StandardCharsets.UTF_8);
    }

    @NonNull
    public static List<Long> idsFromBytes(@Nullable byte[] data) {
        List<Long> ids = new ArrayList<>();
        if (data == null || data.length == 0) {
            return ids;
        }
        try {
            JSONArray array = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                ids.add(array.getLong(i));
            }
        } catch (JSONException e) {
            // return whatever we managed to parse
        }
        return ids;
    }

    @NonNull
    public static byte[] playbackStatesToBytes(@NonNull List<FeedItem> items) {
        JSONArray array = new JSONArray();
        for (FeedItem item : items) {
            try {
                array.put(playbackStateToJson(item));
            } catch (JSONException e) {
                // skip malformed item
            }
        }
        return array.toString().getBytes(StandardCharsets.UTF_8);
    }

    @NonNull
    public static byte[] settingsToBytes(@NonNull WearSettings settings) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(KEY_REWIND_SECS, settings.rewindSecs);
            obj.put(KEY_FAST_FORWARD_SECS, settings.fastForwardSecs);
            obj.put(KEY_SMART_MARK_AS_PLAYED_SECS, settings.smartMarkAsPlayedSecs);
            obj.put(KEY_PLAYBACK_SPEED, settings.playbackSpeed);
            JSONArray speeds = new JSONArray();
            for (float speed : settings.playbackSpeeds) {
                speeds.put(speed);
            }
            obj.put(KEY_PLAYBACK_SPEEDS, speeds);
            return obj.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            return new byte[0];
        }
    }

    @Nullable
    public static WearSettings settingsFromBytes(@NonNull byte[] data) {
        if (data.length == 0) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(new String(data, StandardCharsets.UTF_8));
            List<Float> speeds = WearSettings.DEFAULT_PLAYBACK_SPEEDS;
            JSONArray speedsArray = obj.optJSONArray(KEY_PLAYBACK_SPEEDS);
            if (speedsArray != null && speedsArray.length() > 0) {
                speeds = new ArrayList<>();
                for (int i = 0; i < speedsArray.length(); i++) {
                    speeds.add((float) speedsArray.getDouble(i));
                }
            }
            return new WearSettings(
                    obj.optInt(KEY_REWIND_SECS, WearSettings.DEFAULT_REWIND_SECS),
                    obj.optInt(KEY_FAST_FORWARD_SECS, WearSettings.DEFAULT_FAST_FORWARD_SECS),
                    obj.optInt(KEY_SMART_MARK_AS_PLAYED_SECS, WearSettings.DEFAULT_SMART_MARK_AS_PLAYED_SECS),
                    (float) obj.optDouble(KEY_PLAYBACK_SPEED, WearSettings.DEFAULT_PLAYBACK_SPEED),
                    speeds);
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    public static byte[] positionToBytes(@NonNull WearPosition position) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(KEY_EPISODE_ID, position.itemId);
            obj.put(KEY_GUID, position.guid);
            obj.put(KEY_DOWNLOAD_URL, position.episodeUrl);
            obj.put(KEY_POSITION, position.position);
            obj.put(KEY_DURATION, position.duration);
            obj.put(KEY_START_POSITION, position.startPosition);
            obj.put(KEY_TIMESTAMP, position.timestamp);
            obj.put(KEY_COMPLETED, position.completed);
            return obj.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            return new byte[0];
        }
    }

    @Nullable
    public static WearPosition positionFromBytes(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(new String(data, StandardCharsets.UTF_8));
            return new WearPosition(
                    obj.optLong(KEY_EPISODE_ID, -1),
                    obj.optString(KEY_GUID, null),
                    obj.optString(KEY_DOWNLOAD_URL, null),
                    obj.optInt(KEY_POSITION, 0),
                    obj.optInt(KEY_DURATION, 0),
                    obj.optInt(KEY_START_POSITION, 0),
                    obj.optLong(KEY_TIMESTAMP, 0),
                    obj.optBoolean(KEY_COMPLETED, false));
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    public static byte[] playbackSpeedToBytes(float speed) {
        try {
            JSONObject obj = new JSONObject();
            obj.put(KEY_PLAYBACK_SPEED, speed);
            obj.put(KEY_TIMESTAMP, System.currentTimeMillis());
            return obj.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            return new byte[0];
        }
    }

    public static float playbackSpeedFromBytes(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return FeedPreferences.SPEED_USE_GLOBAL;
        }
        try {
            JSONObject obj = new JSONObject(new String(data, StandardCharsets.UTF_8));
            return (float) obj.optDouble(KEY_PLAYBACK_SPEED, FeedPreferences.SPEED_USE_GLOBAL);
        } catch (JSONException e) {
            return FeedPreferences.SPEED_USE_GLOBAL;
        }
    }

    @NonNull
    public static byte[] chaptersToBytes(@Nullable List<Chapter> chapters) {
        JSONArray array = new JSONArray();
        if (chapters != null) {
            for (Chapter chapter : chapters.subList(0, Math.min(chapters.size(), MAX_CHAPTERS))) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put(KEY_START, chapter.getStart());
                    obj.put(KEY_TITLE, chapter.getTitle() != null ? chapter.getTitle() : "");
                    array.put(obj);
                } catch (JSONException e) {
                    // skip malformed chapter
                }
            }
        }
        return array.toString().getBytes(StandardCharsets.UTF_8);
    }

    @NonNull
    public static List<Chapter> chaptersFromBytes(@Nullable byte[] data) {
        List<Chapter> chapters = new ArrayList<>();
        if (data == null || data.length == 0) {
            return chapters;
        }
        try {
            JSONArray array = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                chapters.add(new Chapter(obj.optLong(KEY_START, 0), obj.optString(KEY_TITLE, ""), null, null));
            }
        } catch (JSONException e) {
            // return whatever we managed to parse
        }
        return chapters;
    }

    @NonNull
    public static byte[] feedsToBytes(@NonNull List<Feed> feeds) {
        JSONArray array = new JSONArray();
        for (Feed feed : feeds) {
            try {
                JSONObject obj = new JSONObject();
                obj.put(KEY_FEED_ID, feed.getId());
                obj.put(KEY_TITLE, feed.getTitle() != null ? feed.getTitle() : "");
                array.put(obj);
            } catch (JSONException e) {
                // skip malformed feed
            }
        }
        return array.toString().getBytes(StandardCharsets.UTF_8);
    }

    @NonNull
    public static List<Feed> feedsFromBytes(@NonNull byte[] data) {
        List<Feed> feeds = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Feed feed = new Feed(null, null);
                feed.setId(obj.optLong(KEY_FEED_ID, -1));
                feed.setTitle(obj.optString(KEY_TITLE, ""));
                feeds.add(feed);
            }
        } catch (JSONException e) {
            // return whatever we managed to parse
        }
        return feeds;
    }

    @NonNull
    public static byte[] nowPlayingToBytes(@NonNull FeedItem item, boolean isPlaying) {
        try {
            JSONObject obj = episodeToJson(item);
            obj.put(KEY_IS_PLAYING, isPlaying);
            return obj.toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            return new byte[0];
        }
    }

    @Nullable
    public static WearNowPlaying nowPlayingFromBytes(@NonNull byte[] data) {
        if (data.length == 0) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(new String(data, StandardCharsets.UTF_8));
            FeedItem item = episodeFromJson(obj);
            boolean isPlaying = obj.optBoolean(KEY_IS_PLAYING, false);
            return new WearNowPlaying(item, isPlaying);
        } catch (JSONException e) {
            return null;
        }
    }

}
