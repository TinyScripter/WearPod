package de.danoeh.antennapod.wearos

import android.app.Application
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.net.sync.wearinterface.WearConnectionUtils
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.ui.common.R as CommonR
import de.danoeh.antennapod.wearos.download.DownloadStatus
import de.danoeh.antennapod.wearos.download.EpisodeDownloadWorker
import de.danoeh.antennapod.wearos.download.WatchEpisode
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore
import de.danoeh.antennapod.wearos.sync.WearDataRepository
import de.danoeh.antennapod.wearos.sync.WearMessageSender
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class EpisodeDetailUiState(
    val item: FeedItem,
    val position: Int = 0,
    val duration: Int = 0,
    val isCurrentlyPlaying: Boolean = false,
    val hasStartedPlaying: Boolean = false,
    val canDownloadToWatch: Boolean = false,
    val watchEpisode: WatchEpisode? = null,
    val downloadProgress: Int = 0,
    val watchErrorRes: Int? = null,
    val isRequestingDownload: Boolean = false
)

class EpisodeDetailViewModel(application: Application, private val episode: FeedItem) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        EpisodeDetailUiState(
            item = episode,
            position = episode.media?.position ?: 0,
            duration = episode.media?.duration ?: 0,
            hasStartedPlaying = WearDataRepository.nowPlaying.value
                ?.takeIf { it.item.id == episode.id }?.isPlaying == true
        )
    )
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            while (true) {
                WearMessageSender.send(getApplication(), WearDataPaths.NOW_PLAYING)
                delay(1.seconds)
            }
        }

        viewModelScope.launch {
            WearDataRepository.nowPlaying.collect { nowPlaying ->
                val liveData = nowPlaying?.takeIf { it.item.id == episode.id }
                val position = liveData?.item?.media?.position ?: episode.media?.position ?: 0
                val duration = liveData?.item?.media?.duration?.takeIf { it > 0 } ?: episode.media?.duration ?: 0
                val isCurrentlyPlaying = liveData?.isPlaying == true
                _uiState.update {
                    it.copy(
                        position = position,
                        duration = duration,
                        isCurrentlyPlaying = isCurrentlyPlaying,
                        hasStartedPlaying = it.hasStartedPlaying || isCurrentlyPlaying
                    )
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            WatchEpisodeStore.load(getApplication())
            val isSupported = WearConnectionUtils.isPhoneStandaloneSupported(getApplication())
            _uiState.update { it.copy(canDownloadToWatch = isSupported) }
        }

        viewModelScope.launch {
            combine(WatchEpisodeStore.episodes, WatchEpisodeStore.progress) { episodes, progress ->
                episodes.find { it.itemId == episode.id } to (progress[episode.id] ?: 0)
            }.collect { (watchEpisode, progress) ->
                _uiState.update { it.copy(watchEpisode = watchEpisode, downloadProgress = progress) }
            }
        }
    }

    fun downloadToWatch() {
        if (_uiState.value.isRequestingDownload) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(watchErrorRes = null, isRequestingDownload = true) }
            WearMessageSender.send(getApplication(), WearDataPaths.episodeDetailsPath(episode.id))
            val details = withTimeoutOrNull(10.seconds) {
                WearDataRepository.episodeDetails.first { it.containsKey(episode.id) }
            }?.get(episode.id)
            if (details == null) {
                _uiState.update { it.copy(watchErrorRes = CommonR.string.wearos_phone_not_reachable) }
                return@launch
            }
            val media = details.media
            val url = media?.downloadUrl
            if (media == null || url.isNullOrEmpty()) {
                _uiState.update { it.copy(watchErrorRes = CommonR.string.wearos_download_failed) }
                return@launch
            }
            val hasSpace = withContext(Dispatchers.IO) {
                WatchEpisodeStore.hasSpaceFor(getApplication(), details.id, media.size)
            }
            if (!hasSpace) {
                _uiState.update { it.copy(watchErrorRes = CommonR.string.download_error_insufficient_space) }
                return@launch
            }
            val extension = MimeTypeMap.getFileExtensionFromUrl(url).ifEmpty {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(media.mimeType) ?: "mp3"
            }
            val watchEpisode = WatchEpisode(
                itemId = details.id,
                guid = details.itemIdentifier,
                downloadUrl = url,
                title = details.title ?: "",
                feedTitle = details.feed?.title ?: "",
                duration = media.duration,
                position = media.position,
                lastPlayed = media.lastPlayedTimeHistory?.time ?: 0,
                size = media.size,
                fileName = "${details.id}.$extension",
                status = DownloadStatus.QUEUED
            )
            withContext(Dispatchers.IO) {
                WatchEpisodeStore.put(getApplication(), watchEpisode)
                EpisodeDownloadWorker.enqueue(getApplication(), watchEpisode.itemId)
            }
        }.invokeOnCompletion {
            _uiState.update { it.copy(isRequestingDownload = false) }
        }
    }

    fun removeFromWatch() {
        viewModelScope.launch(Dispatchers.IO) {
            EpisodeDownloadWorker.cancel(getApplication(), episode.id)
            WatchEpisodeStore.remove(getApplication(), episode.id)
        }
    }

    fun play() {
        viewModelScope.launch(Dispatchers.IO) {
            WearMessageSender.send(getApplication(), WearDataPaths.playPath(episode.id))
        }
    }

    fun pause() {
        viewModelScope.launch(Dispatchers.IO) { WearMessageSender.send(getApplication(), WearDataPaths.PAUSE) }
    }

    fun skipForward() {
        viewModelScope.launch(Dispatchers.IO) { WearMessageSender.send(getApplication(), WearDataPaths.SKIP_FORWARD) }
    }

    fun skipBackward() {
        viewModelScope.launch(Dispatchers.IO) { WearMessageSender.send(getApplication(), WearDataPaths.SKIP_BACKWARD) }
    }

    fun openOnPhone() {
        viewModelScope.launch(Dispatchers.IO) {
            WearMessageSender.send(getApplication(), WearDataPaths.openOnPhonePath(episode.id))
        }
    }

    companion object {
        fun factory(episode: FeedItem): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EpisodeDetailViewModel(
                    checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY)),
                    episode
                )
            }
        }
    }
}
