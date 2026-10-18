package de.danoeh.antennapod.net.sync.wearinterface;

import de.danoeh.antennapod.model.feed.FeedItem;

import java.util.Objects;

public class WearNowPlaying {
    public final FeedItem item;
    public final boolean isPlaying;

    public WearNowPlaying(FeedItem item, boolean isPlaying) {
        this.item = item;
        this.isPlaying = isPlaying;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WearNowPlaying that = (WearNowPlaying) o;
        return isPlaying == that.isPlaying && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, isPlaying);
    }
}
