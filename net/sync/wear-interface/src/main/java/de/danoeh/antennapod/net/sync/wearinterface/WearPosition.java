package de.danoeh.antennapod.net.sync.wearinterface;

import androidx.annotation.Nullable;

public class WearPosition {
    public final long itemId;
    @Nullable
    public final String guid;
    @Nullable
    public final String episodeUrl;
    public final int position;
    public final int duration;
    public final int startPosition;
    public final long timestamp;
    public final boolean completed;

    public WearPosition(long itemId, @Nullable String guid, @Nullable String episodeUrl, int position, int duration,
                        int startPosition, long timestamp, boolean completed) {
        this.itemId = itemId;
        this.guid = guid;
        this.episodeUrl = episodeUrl;
        this.position = position;
        this.duration = duration;
        this.startPosition = startPosition;
        this.timestamp = timestamp;
        this.completed = completed;
    }
}
