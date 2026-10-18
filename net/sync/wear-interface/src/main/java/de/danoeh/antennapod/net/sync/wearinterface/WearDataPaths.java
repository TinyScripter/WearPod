package de.danoeh.antennapod.net.sync.wearinterface;

public final class WearDataPaths {
    public static final String QUEUE = "/queue";
    public static final String DOWNLOADS = "/downloads";
    public static final String EPISODES = "/episodes";
    public static final String SUBSCRIPTIONS = "/subscriptions";
    public static final String FEED_EPISODES_PREFIX = "/feed_episodes/";
    public static final String PLAY_PREFIX = "/play/";
    public static final String NOW_PLAYING = "/now_playing";
    public static final String PAUSE = "/pause";
    public static final String SKIP_FORWARD = "/skip_forward";
    public static final String SKIP_BACKWARD = "/skip_backward";
    public static final String OPEN_ON_PHONE_PREFIX = "/open_on_phone/";
    public static final String EPISODE_DETAILS_PREFIX = "/episode_details/";
    public static final String POSITIONS = "/positions";
    public static final String SETTINGS = "/settings";
    public static final String WATCH_POSITION_PREFIX = "/watch_position/";
    public static final String EPISODE_FILE_PREFIX = "/episode_file/";
    public static final String WATCH_PLAYBACK_SPEED = "/watch_playback_speed";
    public static final String CHAPTERS_PREFIX = "/chapters/";
    public static final String FEED_IMAGE_PREFIX = "/image/feed/";
    public static final String ITEM_IMAGE_PREFIX = "/image/item/";

    public static String playPath(long itemId) {
        return PLAY_PREFIX + itemId;
    }

    public static String openOnPhonePath(long itemId) {
        return OPEN_ON_PHONE_PREFIX + itemId;
    }

    public static String feedEpisodesPath(long feedId) {
        return FEED_EPISODES_PREFIX + feedId;
    }

    public static String episodeDetailsPath(long itemId) {
        return EPISODE_DETAILS_PREFIX + itemId;
    }

    public static String watchPositionPath(long itemId) {
        return WATCH_POSITION_PREFIX + itemId;
    }

    public static String episodeFilePath(long itemId) {
        return EPISODE_FILE_PREFIX + itemId;
    }

    public static String chaptersPath(long itemId) {
        return CHAPTERS_PREFIX + itemId;
    }

    public static String feedImagePath(long feedId) {
        return FEED_IMAGE_PREFIX + feedId;
    }

    public static String itemImagePath(long itemId) {
        return ITEM_IMAGE_PREFIX + itemId;
    }

    private WearDataPaths() {
    }
}
