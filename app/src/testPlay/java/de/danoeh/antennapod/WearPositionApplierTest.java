package de.danoeh.antennapod;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueue;
import de.danoeh.antennapod.net.sync.serviceinterface.SynchronizationQueueStub;
import de.danoeh.antennapod.net.sync.wearinterface.WearPosition;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class WearPositionApplierTest {
    private static final long TIMEOUT = 5L;
    private static final int DURATION = 3600000;
    private static final String GUID = "guid-1";
    private static final String URL = "https://example.com/episode.mp3";

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UserPreferences.init(context);
        PlaybackPreferences.init(context);
        SynchronizationQueue.setInstance(new SynchronizationQueueStub());
        AutoDownloadManager.setInstance(new AutoDownloadManager() {
            @Override
            public Future<?> autodownloadUndownloadedItems(Context context) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void performAutoCleanup(Context context) {
            }
        });

        PodDBAdapter.init(context);
        PodDBAdapter.deleteDatabase();
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.close();
    }

    @After
    public void tearDown() {
        PodDBAdapter.tearDownTests();
        DBWriter.tearDownTests();
    }

    private FeedItem createItem(int position, long lastPlayed) throws Exception {
        Feed feed = new Feed("https://example.com/feed", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        FeedItem item = new FeedItem(0, "Item", GUID, "https://example.com/item", new Date(), FeedItem.UNPLAYED, feed);
        items.add(item);
        FeedMedia media = new FeedMedia(0, item, DURATION, position, 1, "audio/mpeg",
                null, URL, 0, new Date(lastPlayed), 0, 0);
        item.setMedia(media);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.close();
        return item;
    }

    private void waitForDatabase() throws Exception {
        DBWriter.removeQueueItem(context, false).get(TIMEOUT, TimeUnit.SECONDS);
    }

    @Test
    public void testAppliesNewerPosition() throws Exception {
        FeedItem item = createItem(1000, 1000);

        WearPositionApplier.apply(context,
                new WearPosition(item.getId(), GUID, URL, 60000, DURATION, 1000, 5000, false));
        waitForDatabase();

        FeedItem itemFromDb = DBReader.getFeedItem(item.getId());
        assertEquals(60000, itemFromDb.getMedia().getPosition());
        assertEquals(5000, itemFromDb.getMedia().getLastPlayedTimeHistory().getTime());
        assertEquals(59000, itemFromDb.getMedia().getPlayedDuration());
        assertFalse(itemFromDb.isPlayed());
    }

    @Test
    public void testIgnoresOlderPosition() throws Exception {
        FeedItem item = createItem(2000, 10000);

        WearPositionApplier.apply(context,
                new WearPosition(item.getId(), GUID, URL, 60000, DURATION, 0, 5000, false));
        waitForDatabase();

        FeedItem itemFromDb = DBReader.getFeedItem(item.getId());
        assertEquals(2000, itemFromDb.getMedia().getPosition());
        assertEquals(10000, itemFromDb.getMedia().getLastPlayedTimeHistory().getTime());
    }

    @Test
    public void testCompletedMarksPlayedAndRemovesFromQueue() throws Exception {
        FeedItem item = createItem(1000, 1000);
        DBWriter.addQueueItem(context, item).get(TIMEOUT, TimeUnit.SECONDS);
        assertEquals(1, DBReader.getQueue().size());

        WearPositionApplier.apply(context,
                new WearPosition(item.getId(), GUID, URL, DURATION, DURATION, 1000, 5000, true));
        waitForDatabase();

        FeedItem itemFromDb = DBReader.getFeedItem(item.getId());
        assertTrue(itemFromDb.isPlayed());
        assertEquals(0, itemFromDb.getMedia().getPosition());
        assertEquals(Collections.emptyList(), DBReader.getQueue());
    }

    @Test
    public void testFindsEpisodeByGuidIfIdIsUnknown() throws Exception {
        FeedItem item = createItem(1000, 1000);

        WearPositionApplier.apply(context,
                new WearPosition(item.getId() + 100, GUID, URL, 60000, DURATION, 1000, 5000, false));
        waitForDatabase();

        assertEquals(60000, DBReader.getFeedItem(item.getId()).getMedia().getPosition());
    }

    @Test
    public void testIgnoresUnknownEpisode() throws Exception {
        FeedItem item = createItem(1000, 1000);

        WearPositionApplier.apply(context,
                new WearPosition(item.getId() + 100, "other-guid", "https://example.com/other.mp3",
                        60000, DURATION, 1000, 5000, false));
        waitForDatabase();

        assertEquals(1000, DBReader.getFeedItem(item.getId()).getMedia().getPosition());
    }
}
