package de.danoeh.antennapod;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.wearinterface.WearPosition;
import de.danoeh.antennapod.playback.service.Media3PlaybackService;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.internal.PlayableUtils;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;

import java.util.Date;

/**
 * Applies a playback position that was reported by the watch to the database of the phone.
 * The most recent timestamp wins, so positions that are older than the last playback on the phone are ignored.
 */
public final class WearPositionApplier {
    private static final String TAG = "WearPositionApplier";

    private WearPositionApplier() {
    }

    public static void apply(@NonNull Context context, @NonNull WearPosition position) {
        FeedItem item = findItem(position);
        if (item == null || item.getMedia() == null) {
            Log.d(TAG, "Ignoring position of unknown episode " + position.itemId);
            return;
        }
        FeedMedia media = item.getMedia();
        boolean isCurrentMedia = media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        if (isCurrentMedia
                && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING) {
            Log.d(TAG, "Ignoring position because the phone is playing episode " + item.getId());
            return;
        }
        Date lastPlayed = media.getLastPlayedTimeHistory();
        if (lastPlayed != null && lastPlayed.getTime() >= position.timestamp) {
            Log.d(TAG, "Ignoring outdated position of episode " + item.getId());
            return;
        }

        int listened = position.position - Math.max(position.startPosition, media.getPosition());
        if (listened > 0) {
            media.setPlayedDuration(media.getPlayedDuration() + listened);
        }
        PlayableUtils.saveCurrentPosition(media, position.position, position.timestamp);

        Feed feed = item.getFeed();
        if (feed != null && !feed.isLocalFeed() && feed.getState() != Feed.STATE_NOT_SUBSCRIBED
                && (position.completed || position.position > position.startPosition)) {
            SynchronizationQueue.getInstance().enqueueEpisodeAction(
                    new EpisodeAction.Builder(item, EpisodeAction.PLAY)
                            .timestamp(new Date(position.timestamp))
                            .started(position.startPosition / 1000)
                            .position((position.completed ? media.getDuration() : position.position) / 1000)
                            .total(media.getDuration() / 1000)
                            .build());
        }

        if (isCurrentMedia && !position.completed) {
            String mediaId = String.valueOf(media.getId());
            PlaybackController.bindToMedia3Service(context, controller -> {
                MediaItem currentItem = controller.getCurrentMediaItem();
                if (currentItem != null && mediaId.equals(currentItem.mediaId)) {
                    controller.seekTo(position.position);
                }
            });
        }
        Media3PlaybackService.updateDatabaseAfterPlayback(context, media, -1, null, position.completed, false, false);
    }

    @Nullable
    private static FeedItem findItem(@NonNull WearPosition position) {
        FeedItem item = DBReader.getFeedItem(position.itemId);
        if (item != null && item.getMedia() != null
                && ((position.guid != null && TextUtils.equals(item.getItemIdentifier(), position.guid))
                || TextUtils.equals(item.getMedia().getDownloadUrl(), position.episodeUrl))) {
            return item;
        }
        if (position.episodeUrl == null) {
            return null;
        }
        item = DBReader.getFeedItemByGuidOrEpisodeUrl(position.guid, position.episodeUrl);
        return item == null ? null : DBReader.getFeedItem(item.getId());
    }
}
