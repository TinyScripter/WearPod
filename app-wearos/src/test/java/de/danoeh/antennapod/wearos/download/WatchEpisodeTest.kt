package de.danoeh.antennapod.wearos.download

import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedMedia
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchEpisodeTest {
    private val episode = WatchEpisode(
        itemId = 1,
        guid = "guid",
        downloadUrl = "https://example.com/episode.mp3",
        title = "Title",
        feedTitle = "Feed",
        duration = 3600000,
        position = 60000,
        lastPlayed = 5000,
        size = 0,
        fileName = "1.mp3",
        status = DownloadStatus.DOWNLOADED
    )

    private fun phoneItem(position: Int, lastPlayed: Long, played: Boolean): FeedItem {
        val item = FeedItem()
        item.id = 1
        item.setPlayed(played)
        item.media = FeedMedia(0, item, 3600000, position, 0, null, null, null, 0, Date(lastPlayed), 0, 0L)
        return item
    }

    @Test
    fun testNewerPhonePositionIsApplied() {
        val result = episode.withPhoneState(phoneItem(120000, 9000, false))

        assertEquals(120000, result.position)
        assertEquals(9000, result.lastPlayed)
        assertEquals(PhoneState.UNPLAYED, result.phoneState)
    }

    @Test
    fun testOlderPhonePositionIsIgnored() {
        val result = episode.withPhoneState(phoneItem(120000, 5000, false))

        assertEquals(60000, result.position)
        assertEquals(5000, result.lastPlayed)
        assertEquals(PhoneState.UNPLAYED, result.phoneState)
    }

    @Test
    fun testPlayedOnPhone() {
        val result = episode.withPhoneState(phoneItem(0, 5000, true))

        assertEquals(60000, result.position)
        assertEquals(PhoneState.PLAYED, result.phoneState)
    }

    @Test
    fun testUnplayedOnPhoneResetsState() {
        val result = episode.copy(phoneState = PhoneState.PLAYED).withPhoneState(phoneItem(0, 0, false))

        assertEquals(PhoneState.UNPLAYED, result.phoneState)
    }

    @Test
    fun testDeletedOnPhone() {
        val result = episode.withPhoneState(null)

        assertEquals(60000, result.position)
        assertEquals(PhoneState.DELETED, result.phoneState)
    }

    @Test
    fun testIsAlmostEnded() {
        assertFalse(episode.isAlmostEnded(3500000, 30))
        assertTrue(episode.isAlmostEnded(3570000, 30))
        assertTrue(episode.isAlmostEnded(3600000, 0))
        assertFalse(episode.isAlmostEnded(3599000, 0))
        assertFalse(episode.copy(duration = 0).isAlmostEnded(3600000, 30))
    }
}
