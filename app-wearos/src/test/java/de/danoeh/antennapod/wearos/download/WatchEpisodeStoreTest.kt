package de.danoeh.antennapod.wearos.download

import android.content.Context
import de.danoeh.antennapod.model.feed.FeedItem
import java.io.File
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WatchEpisodeStoreTest {
    private lateinit var context: Context

    private fun episode(itemId: Long, size: Long = 0) = WatchEpisode(
        itemId = itemId,
        guid = "guid-$itemId",
        downloadUrl = "https://example.com/$itemId.mp3",
        title = "Title $itemId",
        feedTitle = "Feed",
        duration = 3600000,
        position = 60000,
        lastPlayed = 5000,
        size = size,
        fileName = "$itemId.mp3",
        status = DownloadStatus.DOWNLOADED
    )

    private fun itemIds() = WatchEpisodeStore.episodes.value.map { it.itemId }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WatchEpisodeStore.load(context)
        itemIds().forEach { WatchEpisodeStore.remove(context, it) }
    }

    @Test
    fun testJsonRoundTrip() {
        val episode = episode(1).copy(phoneState = PhoneState.PLAYED)

        assertEquals(episode, WatchEpisode.fromJson(episode.toJson()))
    }

    @Test
    fun testJsonRoundTripWithoutGuid() {
        val episode = episode(1).copy(guid = null, status = DownloadStatus.WAITING_FOR_WIFI)

        assertEquals(episode, WatchEpisode.fromJson(episode.toJson()))
    }

    @Test
    fun testPutAddsAndReplaces() {
        WatchEpisodeStore.put(context, episode(1))
        WatchEpisodeStore.put(context, episode(2))
        WatchEpisodeStore.put(context, episode(1).copy(title = "Replaced"))

        assertEquals(listOf(1L, 2L), itemIds())
        assertEquals("Replaced", WatchEpisodeStore.get(context, 1)?.title)
    }

    @Test
    fun testEpisodesArePersisted() {
        WatchEpisodeStore.put(context, episode(1))
        WatchEpisodeStore.put(context, episode(2))

        val stored = JSONArray(File(context.filesDir, "watch_episodes.json").readText())
        assertEquals(2, stored.length())
        assertEquals(episode(2), WatchEpisode.fromJson(stored.getJSONObject(1)))
    }

    @Test
    fun testMove() {
        (1L..3L).forEach { WatchEpisodeStore.put(context, episode(it)) }

        WatchEpisodeStore.move(context, 3, 0)
        assertEquals(listOf(3L, 1L, 2L), itemIds())

        WatchEpisodeStore.move(context, 3, 1)
        assertEquals(listOf(1L, 3L, 2L), itemIds())

        WatchEpisodeStore.move(context, 1, 10)
        assertEquals(listOf(3L, 2L, 1L), itemIds())
    }

    @Test
    fun testRemoveDeletesFile() {
        WatchEpisodeStore.put(context, episode(1))
        val file = WatchEpisodeStore.getFile(context, episode(1))
        file.parentFile?.mkdirs()
        file.writeText("audio")

        WatchEpisodeStore.remove(context, 1)

        assertNull(WatchEpisodeStore.get(context, 1))
        assertFalse(file.exists())
    }

    @Test
    fun testHasSpaceFor() {
        WatchEpisodeStore.put(context, episode(1, size = 900L * 1024 * 1024))

        assertTrue(WatchEpisodeStore.hasSpaceFor(context, 2, 100L * 1024 * 1024))
        assertFalse(WatchEpisodeStore.hasSpaceFor(context, 2, 200L * 1024 * 1024))
        assertTrue(WatchEpisodeStore.hasSpaceFor(context, 1, 200L * 1024 * 1024))
    }

    @Test
    fun testApplyPhoneStates() {
        (1L..3L).forEach { WatchEpisodeStore.put(context, episode(it)) }
        val played = FeedItem()
        played.id = 1
        played.setPlayed(true)

        WatchEpisodeStore.applyPhoneStates(context, listOf(1L, 2L), listOf(played))

        assertEquals(PhoneState.PLAYED, WatchEpisodeStore.get(context, 1)?.phoneState)
        assertEquals(PhoneState.DELETED, WatchEpisodeStore.get(context, 2)?.phoneState)
        assertEquals(PhoneState.UNPLAYED, WatchEpisodeStore.get(context, 3)?.phoneState)
    }
}
