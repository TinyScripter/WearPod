package de.danoeh.antennapod.wearos.download

import android.content.Context
import android.os.Build
import de.danoeh.antennapod.model.feed.Chapter
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
import org.robolectric.annotation.Config

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
        val episode = episode(1).copy(phoneState = PhoneState.PLAYED, failedDownloads = 2)

        assertEquals(episode, WatchEpisode.fromJson(episode.toJson()))
    }

    @Test
    fun testJsonRoundTripWithSpeedAndChapters() {
        val episode = episode(1).copy(
            status = DownloadStatus.NOT_ON_PHONE,
            feedPlaybackSpeed = 1.3f,
            chapters = listOf(Chapter(0, "Intro", null, null), Chapter(90000, "Main", null, null))
        )

        val result = WatchEpisode.fromJson(episode.toJson())

        assertEquals(episode.copy(chapters = null), result.copy(chapters = null))
        assertEquals(listOf(0L, 90000L), result.chapters?.map { it.start })
        assertEquals(listOf("Intro", "Main"), result.chapters?.map { it.title })
    }

    @Test
    fun testJsonRoundTripWithoutChapters() {
        assertNull(WatchEpisode.fromJson(episode(1).toJson()).chapters)
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
    fun testRemoveDeletesImage() {
        WatchEpisodeStore.put(context, episode(1))
        val image = File(File(context.filesDir, "images"), "image_item_1.jpg")
        image.parentFile?.mkdirs()
        image.writeText("image")

        WatchEpisodeStore.remove(context, 1)

        assertFalse(image.exists())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.N_MR1])
    fun testHasSpaceFor() {
        WatchEpisodeStore.put(context, episode(1, size = 900L * 1024 * 1024))

        assertTrue(WatchEpisodeStore.hasSpaceFor(context, 2, 100L * 1024 * 1024))
        assertFalse(WatchEpisodeStore.hasSpaceFor(context, 2, 200L * 1024 * 1024))
        assertTrue(WatchEpisodeStore.hasSpaceFor(context, 1, 200L * 1024 * 1024))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.N_MR1])
    fun testHasSpaceForChecksFreeSpace() {
        val freeBytes = WatchEpisodeStore.freeBytes(context)

        assertFalse(WatchEpisodeStore.hasSpaceFor(context, 1, 100L * 1024 * 1024, freeBytes))
        assertTrue(WatchEpisodeStore.hasSpaceFor(context, 1, 100L * 1024 * 1024, 0))
    }

    @Test
    fun testApplyPhoneStates() {
        (1L..3L).forEach { WatchEpisodeStore.put(context, episode(it)) }
        val played = FeedItem()
        played.id = 1
        played.setPlayed(true)

        val file = WatchEpisodeStore.getFile(context, episode(1))
        file.parentFile?.mkdirs()
        file.writeText("audio")

        val removed = WatchEpisodeStore.applyPhoneStates(context, listOf(1L, 2L), listOf(played))

        assertEquals(listOf(1L, 2L), removed)
        assertNull(WatchEpisodeStore.get(context, 1))
        assertNull(WatchEpisodeStore.get(context, 2))
        assertFalse(file.exists())
        assertEquals(PhoneState.UNPLAYED, WatchEpisodeStore.get(context, 3)?.phoneState)
    }

    @Test
    fun testApplyPhoneStatesKeepsItems() {
        (1L..2L).forEach { WatchEpisodeStore.put(context, episode(it)) }
        val played = FeedItem()
        played.id = 1
        played.setPlayed(true)

        val removed = WatchEpisodeStore.applyPhoneStates(context, listOf(1L, 2L), listOf(played), setOf(1L, 2L))

        assertTrue(removed.isEmpty())
        assertEquals(PhoneState.UNPLAYED, WatchEpisodeStore.get(context, 1)?.phoneState)
        assertEquals(PhoneState.UNPLAYED, WatchEpisodeStore.get(context, 2)?.phoneState)

        assertEquals(listOf(1L, 2L), WatchEpisodeStore.applyPhoneStates(context, listOf(1L, 2L), listOf(played)))
    }

    @Test
    fun testApplyPhoneStatesKeepsEpisodesPlayedBeforeDownload() {
        WatchEpisodeStore.put(context, episode(1).copy(phoneState = PhoneState.PLAYED))
        val played = FeedItem()
        played.id = 1
        played.setPlayed(true)

        val removed = WatchEpisodeStore.applyPhoneStates(context, listOf(1L), listOf(played))

        assertTrue(removed.isEmpty())
        assertEquals(PhoneState.PLAYED, WatchEpisodeStore.get(context, 1)?.phoneState)
    }
}
