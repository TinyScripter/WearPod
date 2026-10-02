package de.danoeh.antennapod.wearos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.danoeh.antennapod.wearos.download.DownloadStatus
import de.danoeh.antennapod.wearos.download.EpisodeDownloadWorker
import de.danoeh.antennapod.wearos.download.WatchEpisode
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchListUiState(val episodes: List<WatchEpisode> = emptyList(), val progress: Map<Long, Int> = emptyMap())

class WatchListViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(WatchListUiState())
    val uiState: StateFlow<WatchListUiState> = _uiState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            WatchEpisodeStore.load(getApplication())
        }

        viewModelScope.launch {
            WatchEpisodeStore.episodes.collect { episodes ->
                _uiState.update { it.copy(episodes = episodes) }
            }
        }

        viewModelScope.launch {
            WatchEpisodeStore.progress.collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
    }

    fun retry(itemId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            WatchEpisodeStore.update(getApplication(), itemId) { it.copy(status = DownloadStatus.QUEUED) }
            EpisodeDownloadWorker.enqueue(getApplication(), itemId)
        }
    }

    fun remove(itemId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            EpisodeDownloadWorker.cancel(getApplication(), itemId)
            WatchEpisodeStore.remove(getApplication(), itemId)
        }
    }
}
