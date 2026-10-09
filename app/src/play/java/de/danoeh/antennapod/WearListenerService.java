package de.danoeh.antennapod;

import android.net.Uri;
import android.util.Log;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.google.android.gms.wearable.WearableListenerService;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import android.content.Intent;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths;
import de.danoeh.antennapod.net.sync.wearinterface.WearPosition;
import de.danoeh.antennapod.net.sync.wearinterface.WearSerializer;
import de.danoeh.antennapod.net.sync.wearinterface.WearSettings;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class WearListenerService extends WearableListenerService {
    private static final String TAG = "WearListenerService";
    private static final int MAX_ITEMS = 100;

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onPlayerStatusEvent(PlayerStatusEvent event) {
        FeedMedia media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        if (media == null || media.getItem() == null) {
            return;
        }
        boolean isPlaying = PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING;
        byte[] payload = WearSerializer.nowPlayingToBytes(media.getItem(), isPlaying);
        Wearable.getNodeClient(this).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    for (Node node : nodes) {
                        Wearable.getMessageClient(this).sendMessage(node.getId(), WearDataPaths.NOW_PLAYING, payload);
                    }
                });
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        String path = event.getPath();
        String sourceNodeId = event.getSourceNodeId();
        byte[] data = event.getData();
        Log.d(TAG, "Message received: " + path + " from " + sourceNodeId);
        Completable.fromAction(() -> handleMessage(path, sourceNodeId, data))
                .subscribeOn(Schedulers.computation())
                .subscribe(
                        () -> { },
                        throwable -> Log.e(TAG, "Failed to handle wearable message: " + path, throwable));
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            Uri uri = event.getDataItem().getUri();
            if (event.getType() != DataEvent.TYPE_CHANGED || uri.getPath() == null
                    || !uri.getPath().startsWith(WearDataPaths.WATCH_POSITION_PREFIX)) {
                continue;
            }
            WearPosition position = WearSerializer.positionFromBytes(event.getDataItem().getData());
            Log.d(TAG, "Position received: " + uri.getPath());
            Completable.fromAction(() -> {
                if (position != null) {
                    WearPositionApplier.apply(this, position);
                }
                Wearable.getDataClient(this).deleteDataItems(uri);
            })
                    .subscribeOn(Schedulers.computation())
                    .subscribe(
                            () -> { },
                            throwable -> Log.e(TAG, "Failed to apply position: " + uri.getPath(), throwable));
        }
    }

    private void handleMessage(String path, String sourceNodeId, byte[] data) {
        switch (path) {
            case WearDataPaths.NOW_PLAYING:
                FeedMedia media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
                if (media == null) {
                    return;
                }
                if (!PlaybackService.isRunning) {
                    reply(sourceNodeId, WearDataPaths.NOW_PLAYING,
                            WearSerializer.nowPlayingToBytes(media.getItem(), false));
                    return;
                }
                PlaybackController.bindToMedia3Service(this, controller -> {
                    media.setPosition((int) controller.getCurrentPosition());
                    if (controller.getDuration() > 0) {
                        media.setDuration((int) controller.getDuration());
                    }
                    reply(sourceNodeId, WearDataPaths.NOW_PLAYING,
                            WearSerializer.nowPlayingToBytes(media.getItem(), true));
                });
                break;
            case WearDataPaths.PAUSE:
                PlaybackController.bindToMedia3Service(this, controller -> controller.pause());
                break;
            case WearDataPaths.SKIP_FORWARD:
                PlaybackController.bindToMedia3Service(this, controller -> controller.seekForward());
                break;
            case WearDataPaths.SKIP_BACKWARD:
                PlaybackController.bindToMedia3Service(this, controller -> controller.seekBack());
                break;
            case WearDataPaths.QUEUE:
                reply(sourceNodeId, path, WearSerializer.episodesToBytes(DBReader.getQueue()));
                break;
            case WearDataPaths.DOWNLOADS:
                reply(sourceNodeId, path, WearSerializer.episodesToBytes(DBReader.getEpisodes(0, MAX_ITEMS,
                        new FeedItemFilter(FeedItemFilter.DOWNLOADED), SortOrder.DATE_NEW_OLD)));
                break;
            case WearDataPaths.EPISODES:
                reply(sourceNodeId, path, WearSerializer.episodesToBytes(DBReader.getEpisodes(0, MAX_ITEMS,
                        FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD)));
                break;
            case WearDataPaths.SUBSCRIPTIONS:
                reply(sourceNodeId, path, WearSerializer.feedsToBytes(DBReader.getFeedList()));
                break;
            case WearDataPaths.SETTINGS:
                reply(sourceNodeId, path, WearSerializer.settingsToBytes(new WearSettings(
                        UserPreferences.getRewindSecs(), UserPreferences.getFastForwardSecs(),
                        UserPreferences.getSmartMarkAsPlayedSecs(), UserPreferences.getPlaybackSpeed())));
                break;
            case WearDataPaths.POSITIONS:
                List<FeedItem> positionItems = new ArrayList<>();
                for (long itemId : WearSerializer.idsFromBytes(data)) {
                    FeedItem positionItem = DBReader.getFeedItem(itemId);
                    if (positionItem != null) {
                        positionItems.add(positionItem);
                    }
                }
                reply(sourceNodeId, path, WearSerializer.playbackStatesToBytes(positionItems));
                break;
            default:
                if (path.startsWith(WearDataPaths.PLAY_PREFIX)) {
                    try {
                        long itemId = Long.parseLong(path.substring(WearDataPaths.PLAY_PREFIX.length()));
                        playItem(itemId);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Ignoring malformed play path: " + path, e);
                    }
                } else if (path.startsWith(WearDataPaths.FEED_EPISODES_PREFIX)) {
                    try {
                        long feedId = Long.parseLong(path.substring(WearDataPaths.FEED_EPISODES_PREFIX.length()));
                        Feed feed = DBReader.getFeed(feedId, false, 0, MAX_ITEMS);
                        List<FeedItem> feedItems = feed != null ? feed.getItems() : java.util.Collections.emptyList();
                        reply(sourceNodeId, path, WearSerializer.episodesToBytes(feedItems));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Ignoring malformed feed episodes path: " + path, e);
                    }
                } else if (path.startsWith(WearDataPaths.EPISODE_DETAILS_PREFIX)) {
                    try {
                        long itemId = Long.parseLong(path.substring(WearDataPaths.EPISODE_DETAILS_PREFIX.length()));
                        FeedItem item = DBReader.getFeedItem(itemId);
                        if (item == null) {
                            Log.w(TAG, "Item not found for id " + itemId);
                            return;
                        }
                        reply(sourceNodeId, path, WearSerializer.episodeDetailsToBytes(item));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Ignoring malformed episode details path: " + path, e);
                    }
                } else if (path.startsWith(WearDataPaths.OPEN_ON_PHONE_PREFIX)) {
                    try {
                        long itemId = Long.parseLong(path.substring(WearDataPaths.OPEN_ON_PHONE_PREFIX.length()));
                        Intent intent = new MainActivityStarter(this).withOpenEpisode(itemId).getIntent();
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Ignoring malformed open on phone path: " + path, e);
                    }
                } else {
                    Log.d(TAG, "Ignoring unknown path: " + path);
                }
                break;
        }
    }

    private void playItem(long itemId) {
        FeedItem item = DBReader.getFeedItem(itemId);
        if (item == null || item.getMedia() == null) {
            Log.w(TAG, "Item or media not found for id " + itemId);
            return;
        }
        Log.d(TAG, "Starting playback for: " + item.getTitle());
        new PlaybackServiceStarter(this, item.getMedia())
                .callEvenIfRunning(true)
                .start();
    }

    private void reply(String nodeId, String path, byte[] payload) {
        Log.d(TAG, "Sending reply to " + nodeId + " at " + path + " (" + payload.length + " bytes)");
        Wearable.getMessageClient(this).sendMessage(nodeId, path, payload)
                .addOnSuccessListener(id -> Log.d(TAG, "Reply sent: " + path))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send reply to " + path, e));
    }
}
