package de.danoeh.antennapod.net.sync.wearinterface;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class WearSerializerTest {
    @Test
    public void testEpisodesRoundTrip() {
        FeedItem item = new FeedItem();
        item.setId(42L);
        item.setTitle("Test Episode");
        item.setPubDate(new Date(1000000L));
        FeedMedia media = new FeedMedia(0, item, 3600000, 90000, 0, null, null, null, 0, null, 0, 0L);
        item.setMedia(media);

        byte[] bytes = WearSerializer.episodesToBytes(Collections.singletonList(item));
        List<FeedItem> result = WearSerializer.episodesFromBytes(bytes);

        assertEquals(1, result.size());
        assertEquals(42L, result.get(0).getId());
        assertEquals("Test Episode", result.get(0).getTitle());
        assertEquals(1000000L, result.get(0).getPubDate().getTime());
        assertEquals(3600000, result.get(0).getMedia().getDuration());
        assertEquals(90000, result.get(0).getMedia().getPosition());
    }

    @Test
    public void testEpisodesRoundTripEmpty() {
        byte[] bytes = WearSerializer.episodesToBytes(Collections.emptyList());
        List<FeedItem> result = WearSerializer.episodesFromBytes(bytes);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testEpisodesRoundTripNullTitle() {
        FeedItem item = new FeedItem();
        item.setId(1L);
        item.setTitle(null);

        byte[] bytes = WearSerializer.episodesToBytes(Collections.singletonList(item));
        List<FeedItem> result = WearSerializer.episodesFromBytes(bytes);

        assertEquals(1, result.size());
        assertEquals("", result.get(0).getTitle());
    }

    @Test
    public void testFeedsRoundTrip() {
        Feed feed = new Feed(null, null);
        feed.setId(7L);
        feed.setTitle("Test Feed");

        byte[] bytes = WearSerializer.feedsToBytes(Collections.singletonList(feed));
        List<Feed> result = WearSerializer.feedsFromBytes(bytes);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getId());
        assertEquals("Test Feed", result.get(0).getTitle());
    }

    @Test
    public void testFeedsRoundTripEmpty() {
        byte[] bytes = WearSerializer.feedsToBytes(Collections.emptyList());
        List<Feed> result = WearSerializer.feedsFromBytes(bytes);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFeedsRoundTripMultiple() {
        Feed feed1 = new Feed(null, null);
        feed1.setId(1L);
        feed1.setTitle("Feed One");
        Feed feed2 = new Feed(null, null);
        feed2.setId(2L);
        feed2.setTitle("Feed Two");

        byte[] bytes = WearSerializer.feedsToBytes(Arrays.asList(feed1, feed2));
        List<Feed> result = WearSerializer.feedsFromBytes(bytes);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Feed One", result.get(0).getTitle());
        assertEquals(2L, result.get(1).getId());
        assertEquals("Feed Two", result.get(1).getTitle());
    }

    @Test
    public void testNowPlayingRoundTrip() {
        FeedItem item = new FeedItem();
        item.setId(99L);
        item.setTitle("Now Playing");
        item.setPubDate(new Date(2000000L));
        FeedMedia media = new FeedMedia(0, item, 7200000, 120000, 0, null, null, null, 0, null, 0, 0L);
        item.setMedia(media);

        byte[] bytes = WearSerializer.nowPlayingToBytes(item, true);
        WearNowPlaying result = WearSerializer.nowPlayingFromBytes(bytes);

        assertTrue(result != null);
        assertEquals(99L, result.item.getId());
        assertEquals("Now Playing", result.item.getTitle());
        assertEquals(7200000, result.item.getMedia().getDuration());
        assertEquals(120000, result.item.getMedia().getPosition());
        assertTrue(result.isPlaying);
    }

    @Test
    public void testNowPlayingFromBytesEmpty() {
        WearNowPlaying result = WearSerializer.nowPlayingFromBytes(new byte[0]);
        assertNull(result);
    }

    @Test
    public void testEpisodeDetailsRoundTrip() {
        Feed feed = new Feed(null, null);
        feed.setTitle("Test Feed");
        FeedItem item = new FeedItem();
        item.setId(5L);
        item.setTitle("Details");
        item.setItemIdentifier("guid-5");
        item.setFeed(feed);
        item.setPlayed(true);
        FeedMedia media = new FeedMedia(0, item, 3600000, 60000, 1234567L, "audio/mpeg", null,
                "https://example.com/episode.mp3", 0, new Date(5000000L), 0, 0L);
        item.setMedia(media);

        FeedItem result = WearSerializer.episodeDetailsFromBytes(WearSerializer.episodeDetailsToBytes(item));

        assertTrue(result != null);
        assertEquals(5L, result.getId());
        assertEquals("Details", result.getTitle());
        assertEquals("guid-5", result.getItemIdentifier());
        assertEquals("Test Feed", result.getFeed().getTitle());
        assertTrue(result.isPlayed());
        assertEquals("https://example.com/episode.mp3", result.getMedia().getDownloadUrl());
        assertEquals("audio/mpeg", result.getMedia().getMimeType());
        assertEquals(1234567L, result.getMedia().getSize());
        assertEquals(60000, result.getMedia().getPosition());
        assertEquals(5000000L, result.getMedia().getLastPlayedTimeHistory().getTime());
    }

    @Test
    public void testEpisodeDetailsFromBytesEmpty() {
        assertNull(WearSerializer.episodeDetailsFromBytes(new byte[0]));
    }

    @Test
    public void testEpisodesWithoutDetails() {
        FeedItem item = new FeedItem();
        item.setId(3L);
        item.setTitle("Basic");

        List<FeedItem> result = WearSerializer.episodesFromBytes(
                WearSerializer.episodesToBytes(Collections.singletonList(item)));

        assertEquals(1, result.size());
        assertNull(result.get(0).getItemIdentifier());
        assertNull(result.get(0).getFeed());
        assertNull(result.get(0).getMedia().getDownloadUrl());
        assertNull(result.get(0).getMedia().getLastPlayedTimeHistory());
    }

    @Test
    public void testIdsRoundTrip() {
        List<Long> ids = Arrays.asList(1L, 42L, 123456789012L);
        assertEquals(ids, WearSerializer.idsFromBytes(WearSerializer.idsToBytes(ids)));
        assertTrue(WearSerializer.idsFromBytes(null).isEmpty());
        assertTrue(WearSerializer.idsFromBytes(new byte[0]).isEmpty());
    }

    @Test
    public void testPlaybackStatesRoundTrip() {
        FeedItem item = new FeedItem();
        item.setId(8L);
        FeedMedia media = new FeedMedia(0, item, 1800000, 300000, 0, null, null, null, 0,
                new Date(7000000L), 0, 0L);
        item.setMedia(media);
        FeedItem playedItem = new FeedItem();
        playedItem.setId(9L);
        playedItem.setPlayed(true);

        List<FeedItem> result = WearSerializer.episodesFromBytes(
                WearSerializer.playbackStatesToBytes(Arrays.asList(item, playedItem)));

        assertEquals(2, result.size());
        assertEquals(8L, result.get(0).getId());
        assertEquals(300000, result.get(0).getMedia().getPosition());
        assertEquals(7000000L, result.get(0).getMedia().getLastPlayedTimeHistory().getTime());
        assertFalse(result.get(0).isPlayed());
        assertEquals(9L, result.get(1).getId());
        assertTrue(result.get(1).isPlayed());
        assertNull(result.get(1).getMedia().getLastPlayedTimeHistory());
    }

    @Test
    public void testSettingsRoundTrip() {
        WearSettings settings = new WearSettings(20, 30, 45, 1.5f);

        WearSettings result = WearSerializer.settingsFromBytes(WearSerializer.settingsToBytes(settings));

        assertTrue(result != null);
        assertEquals(20, result.rewindSecs);
        assertEquals(30, result.fastForwardSecs);
        assertEquals(45, result.smartMarkAsPlayedSecs);
        assertEquals(1.5f, result.playbackSpeed, 0.001f);
    }

    @Test
    public void testSettingsFromBytesEmpty() {
        assertNull(WearSerializer.settingsFromBytes(new byte[0]));
    }
}
