package de.danoeh.antennapod.wearos

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import de.danoeh.antennapod.wearos.download.DownloadStatus
import de.danoeh.antennapod.wearos.download.PhoneState
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore
import de.danoeh.antennapod.wearos.playback.WatchPlaybackService
import de.danoeh.antennapod.wearos.sync.PositionSync
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class WatchPlayerUiState(
    val title: String = "",
    val feedTitle: String = "",
    val position: Int = 0,
    val duration: Int = 0,
    val isPlaying: Boolean = false,
    val hasNext: Boolean = false,
    val isLoading: Boolean = false
)

class WatchPlayerViewModel(application: Application, private val itemId: Long) : AndroidViewModel(application) {
    private var isStarting = itemId != NO_ITEM
    private val _uiState = MutableStateFlow(WatchPlayerUiState(isLoading = isStarting))
    val uiState: StateFlow<WatchPlayerUiState> = _uiState

    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, WatchPlaybackService::class.java))
    ).buildAsync()
    private var controller: MediaController? = null

    init {
        controllerFuture.addListener({
            if (controllerFuture.isCancelled) {
                return@addListener
            }
            try {
                val controller = controllerFuture.get()
                this.controller = controller
                controller.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        updateState()
                    }
                })
                start(controller)
                updateState()
            } catch (e: ExecutionException) {
                Log.e(TAG, "Failed to connect to playback service", e)
                _uiState.value = WatchPlayerUiState()
            }
        }, ContextCompat.getMainExecutor(application))

        viewModelScope.launch {
            while (true) {
                updateState()
                delay(1.seconds)
            }
        }
    }

    private fun start(controller: MediaController) {
        if (!isStarting || (controller.isPlaying && controller.currentMediaItem?.mediaId == itemId.toString())) {
            isStarting = false
            return
        }
        viewModelScope.launch {
            PositionSync.syncFromPhone(getApplication())
            if (controller.currentMediaItem?.mediaId == itemId.toString()) {
                controller.play()
            } else {
                val episodes = WatchEpisodeStore.episodes.value.filter {
                    it.status == DownloadStatus.DOWNLOADED &&
                        (it.phoneState == PhoneState.UNPLAYED || it.itemId == itemId)
                }
                val index = episodes.indexOfFirst { it.itemId == itemId }
                if (index >= 0) {
                    val mediaItems = episodes.map { MediaItem.Builder().setMediaId(it.itemId.toString()).build() }
                    controller.setMediaItems(mediaItems, index, episodes[index].position.toLong())
                    controller.prepare()
                    controller.play()
                }
            }
            isStarting = false
            updateState()
        }
    }

    private fun updateState() {
        val controller = controller ?: return
        _uiState.value = WatchPlayerUiState(
            title = controller.mediaMetadata.title?.toString() ?: "",
            feedTitle = controller.mediaMetadata.artist?.toString() ?: "",
            position = controller.currentPosition.toInt(),
            duration = controller.duration.coerceAtLeast(0).toInt(),
            isPlaying = controller.isPlaying,
            hasNext = controller.hasNextMediaItem(),
            isLoading = isStarting
        )
    }

    fun playPause() {
        val controller = controller ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipBackward() {
        controller?.seekBack()
    }

    fun skipForward() {
        controller?.seekForward()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
    }

    companion object {
        private const val TAG = "WatchPlayerViewModel"
        const val NO_ITEM = -1L

        fun factory(itemId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WatchPlayerViewModel(
                    checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY)),
                    itemId
                )
            }
        }
    }
}
