package de.danoeh.antennapod.net.sync.wearinterface;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WearSettings {
    public static final int DEFAULT_REWIND_SECS = 10;
    public static final int DEFAULT_FAST_FORWARD_SECS = 30;
    public static final int DEFAULT_SMART_MARK_AS_PLAYED_SECS = 30;
    public static final float DEFAULT_PLAYBACK_SPEED = 1.0f;
    public static final List<Float> DEFAULT_PLAYBACK_SPEEDS =
            Collections.unmodifiableList(Arrays.asList(1.0f, 1.25f, 1.5f));

    public final int rewindSecs;
    public final int fastForwardSecs;
    public final int smartMarkAsPlayedSecs;
    public final float playbackSpeed;
    public final List<Float> playbackSpeeds;

    public WearSettings(int rewindSecs, int fastForwardSecs, int smartMarkAsPlayedSecs, float playbackSpeed) {
        this(rewindSecs, fastForwardSecs, smartMarkAsPlayedSecs, playbackSpeed, DEFAULT_PLAYBACK_SPEEDS);
    }

    public WearSettings(int rewindSecs, int fastForwardSecs, int smartMarkAsPlayedSecs, float playbackSpeed,
                        List<Float> playbackSpeeds) {
        this.rewindSecs = rewindSecs;
        this.fastForwardSecs = fastForwardSecs;
        this.smartMarkAsPlayedSecs = smartMarkAsPlayedSecs;
        this.playbackSpeed = playbackSpeed;
        this.playbackSpeeds = playbackSpeeds;
    }
}
