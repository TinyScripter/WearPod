package de.danoeh.antennapod.wearos.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.net.sync.wearinterface.WearSerializer
import de.danoeh.antennapod.wearos.playback.WatchSettingsStore

class WearDataListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        when (path) {
            WearDataPaths.SUBSCRIPTIONS -> {
                val feeds = WearSerializer.feedsFromBytes(event.data)
                Log.d(TAG, "Received $path: ${feeds.size} feeds")
                WearDataRepository.updateFeeds(path, feeds)
            }
            WearDataPaths.NOW_PLAYING ->
                WearDataRepository.updateNowPlaying(WearSerializer.nowPlayingFromBytes(event.data))
            WearDataPaths.SETTINGS -> {
                val settings = WearSerializer.settingsFromBytes(event.data)
                WearDataRepository.updateSettings(settings)
                settings?.let { WatchSettingsStore.save(this, it) }
            }
            WearDataPaths.QUEUE, WearDataPaths.DOWNLOADS, WearDataPaths.EPISODES, WearDataPaths.POSITIONS -> {
                val items = WearSerializer.episodesFromBytes(event.data)
                Log.d(TAG, "Received $path: ${items.size} episodes")
                WearDataRepository.updateEpisodes(path, items)
            }
            else -> if (path.startsWith(WearDataPaths.FEED_EPISODES_PREFIX)) {
                val items = WearSerializer.episodesFromBytes(event.data)
                Log.d(TAG, "Received $path: ${items.size} episodes")
                WearDataRepository.updateEpisodes(path, items)
            } else if (path.startsWith(WearDataPaths.EPISODE_DETAILS_PREFIX)) {
                WearSerializer.episodeDetailsFromBytes(event.data)?.let { WearDataRepository.updateEpisodeDetails(it) }
            }
        }
    }

    companion object {
        private const val TAG = "WearDataListenerService"
    }
}
