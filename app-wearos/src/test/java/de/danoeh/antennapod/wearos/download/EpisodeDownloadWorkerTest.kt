package de.danoeh.antennapod.wearos.download

import de.danoeh.antennapod.wearos.settings.DownloadMode
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeDownloadWorkerTest {
    @Test
    fun testTransportsWithoutMobileData() {
        assertEquals(listOf(Transport.WIFI), EpisodeDownloadWorker.transports(DownloadMode.WIFI_ONLY, false))
        assertEquals(
            listOf(Transport.BLUETOOTH),
            EpisodeDownloadWorker.transports(DownloadMode.BLUETOOTH_ONLY, false)
        )
        assertEquals(
            listOf(Transport.WIFI, Transport.BLUETOOTH),
            EpisodeDownloadWorker.transports(DownloadMode.PREFER_WIFI, false)
        )
        assertEquals(
            listOf(Transport.BLUETOOTH, Transport.WIFI),
            EpisodeDownloadWorker.transports(DownloadMode.PREFER_BLUETOOTH, false)
        )
    }

    @Test
    fun testMobileDataDirectlyFollowsWifi() {
        assertEquals(
            listOf(Transport.WIFI, Transport.MOBILE),
            EpisodeDownloadWorker.transports(DownloadMode.WIFI_ONLY, true)
        )
        assertEquals(
            listOf(Transport.BLUETOOTH),
            EpisodeDownloadWorker.transports(DownloadMode.BLUETOOTH_ONLY, true)
        )
        assertEquals(
            listOf(Transport.WIFI, Transport.MOBILE, Transport.BLUETOOTH),
            EpisodeDownloadWorker.transports(DownloadMode.PREFER_WIFI, true)
        )
        assertEquals(
            listOf(Transport.BLUETOOTH, Transport.WIFI, Transport.MOBILE),
            EpisodeDownloadWorker.transports(DownloadMode.PREFER_BLUETOOTH, true)
        )
    }
}
