package de.danoeh.antennapod.net.sync.wearinterface;

public class WearSettings {
    public static final int DEFAULT_REWIND_SECS = 10;
    public static final int DEFAULT_FAST_FORWARD_SECS = 30;
    public static final int DEFAULT_SMART_MARK_AS_PLAYED_SECS = 30;
    public static final float DEFAULT_PLAYBACK_SPEED = 1.0f;

    public final int rewindSecs;
    public final int fastForwardSecs;
    public final int smartMarkAsPlayedSecs;
    public final float playbackSpeed;

    public WearSettings(int rewindSecs, int fastForwardSecs, int smartMarkAsPlayedSecs, float playbackSpeed) {
        this.rewindSecs = rewindSecs;
        this.fastForwardSecs = fastForwardSecs;
        this.smartMarkAsPlayedSecs = smartMarkAsPlayedSecs;
        this.playbackSpeed = playbackSpeed;
    }
}
