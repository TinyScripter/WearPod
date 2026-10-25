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
import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.wearos.download.DownloadStatus
import de.danoeh.antennapod.wearos.download.PhoneState
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore
import de.danoeh.antennapod.wearos.playback.WatchPlaybackService
import de.danoeh.antennapod.wearos.playback.WatchSettingsStore
import de.danoeh.antennapod.wearos.settings.WatchPreferences
import de.danoeh.antennapod.wearos.sync.ChapterSync
import de.danoeh.antennapod.wearos.sync.PositionSync
import de.danoeh.antennapod.wearos.sync.WearDataRepository
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class WatchPlayerUiState(
    val title: String = "",
    val feedTitle: String = "",
    val position: Int = 0,
    val duration: Int = 0,
    val isPlaying: Boolean = false,
    val hasNext: Boolean = false,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val showPlaybackSpeed: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val playbackSpeeds: List<Float> = emptyList(),
    val showChapters: Boolean = false,
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = -1
)

class WatchPlayerViewModel(
    application: Application,
    private var itemId: Long,
    private var startPlayback: Boolean
) : AndroidViewModel(application) {
    private var isStarting = itemId != NO_ITEM
    private val _uiState = MutableStateFlow(WatchPlayerUiState(isLoading = isStarting))
    val uiState: StateFlow<WatchPlayerUiState> = _uiState

    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, WatchPlaybackService::class.java))
    ).buildAsync()
    private var controller: MediaController? = null
    private var playbackSpeeds = WatchSettingsStore.load(application).playbackSpeeds
    private val chapterRequests = mutableSetOf<Long>()

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
            WearDataRepository.settings.filterNotNull().collect {
                playbackSpeeds = it.playbackSpeeds
                updateState()
            }
        }

        viewModelScope.launch {
            WatchPreferences.observe(application).collect { updateState() }
        }

        viewModelScope.launch {
            _uiState.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { isVisible ->
                while (isVisible) {
                    updateState()
                    delay(1.seconds)
                }
            }
        }
    }

    private fun start(controller: MediaController) {
        if (!isStarting || (controller.isPlaying &&
                (controller.currentMediaItem?.mediaId == itemId.toString() || !startPlayback))
        ) {
            isStarting = false
            return
        }
        viewModelScope.launch {
            PositionSync.syncFromPhone(getApplication(), itemId)
            if (controller.currentMediaItem?.mediaId == itemId.toString()) {
                if (startPlayback) {
                    controller.play()
                }
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
                    if (startPlayback) {
                        controller.play()
                    }
                }
            }
            isStarting = false
            updateState()
        }
    }

    private fun updateState() {
        val controller = controller ?: return
        val preferences = WatchPreferences.get(getApplication())
        val currentItemId = controller.currentMediaItem?.mediaId?.toLongOrNull()
        val chapters = currentItemId?.let { WatchEpisodeStore.get(getApplication(), it) }?.chapters
        if (preferences.showChapters && currentItemId != null && chapters == null &&
            chapterRequests.add(currentItemId)
        ) {
            viewModelScope.launch { ChapterSync.syncFromPhone(getApplication(), currentItemId) }
        }
        val position = controller.currentPosition.toInt()
        _uiState.value = WatchPlayerUiState(
            title = controller.mediaMetadata.title?.toString() ?: "",
            feedTitle = controller.mediaMetadata.artist?.toString() ?: "",
            position = position,
            duration = controller.duration.coerceAtLeast(0).toInt(),
            isPlaying = controller.isPlaying,
            hasNext = controller.hasNextMediaItem(),
            isLoading = isStarting,
            hasError = controller.playerError != null,
            showPlaybackSpeed = preferences.showPlaybackSpeed,
            playbackSpeed = controller.playbackParameters.speed,
            playbackSpeeds = playbackSpeeds,
            showChapters = preferences.showChapters,
            chapters = chapters.orEmpty(),
            currentChapterIndex = Chapter.getAfterPosition(chapters, position)
        )
    }

    fun open(itemId: Long, startPlayback: Boolean) {
        this.itemId = itemId
        this.startPlayback = startPlayback
        isStarting = true
        _uiState.value = _uiState.value.copy(isLoading = true)
        controller?.let { start(it) }
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

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun seekToChapter(chapter: Chapter) {
        controller?.seekTo(chapter.start)
    }

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
    }

    companion object {
        private const val TAG = "WatchPlayerViewModel"
        const val NO_ITEM = -1L

        fun factory(itemId: Long, startPlayback: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WatchPlayerViewModel(
                    checkNotNull(get(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY)),
                    itemId,
                    startPlayback
                )
            }
        }
    }
}
