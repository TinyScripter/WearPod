package de.danoeh.antennapod.wearos.sync

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.net.sync.wearinterface.WearNowPlaying
import de.danoeh.antennapod.net.sync.wearinterface.WearSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton holding the last data received from the phone for each data path.
 * Written by WearDataListenerService (even when no activity is running) and
 * read by the screen composables.
 */
object WearDataRepository {
    private val _episodesByPath = MutableStateFlow<Map<String, List<FeedItem>>>(emptyMap())
    val episodesByPath: StateFlow<Map<String, List<FeedItem>>> = _episodesByPath

    private val _feedsByPath = MutableStateFlow<Map<String, List<Feed>>>(emptyMap())
    val feedsByPath: StateFlow<Map<String, List<Feed>>> = _feedsByPath

    private val _nowPlaying = MutableStateFlow<WearNowPlaying?>(null)
    val nowPlaying: StateFlow<WearNowPlaying?> = _nowPlaying

    private val _episodeDetails = MutableStateFlow<Map<Long, FeedItem>>(emptyMap())
    val episodeDetails: StateFlow<Map<Long, FeedItem>> = _episodeDetails

    private val _settings = MutableStateFlow<WearSettings?>(null)
    val settings: StateFlow<WearSettings?> = _settings

    fun updateNowPlaying(nowPlaying: WearNowPlaying?) {
        _nowPlaying.value = nowPlaying
    }

    fun updateEpisodes(path: String, items: List<FeedItem>) {
        _episodesByPath.update { it + (path to items) }
    }

    fun clearEpisodes(path: String) {
        _episodesByPath.update { it - path }
    }

    fun updateFeeds(path: String, feeds: List<Feed>) {
        _feedsByPath.update { it + (path to feeds) }
    }

    fun updateEpisodeDetails(item: FeedItem) {
        _episodeDetails.update { it + (item.id to item) }
    }

    fun updateSettings(settings: WearSettings?) {
        _settings.value = settings
    }
}
