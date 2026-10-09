package de.danoeh.antennapod.wearos.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.WearUnsuitableOutputPlaybackSuppressionResolverListener
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.danoeh.antennapod.net.sync.wearinterface.WearPosition
import de.danoeh.antennapod.net.sync.wearinterface.WearSettings
import de.danoeh.antennapod.ui.notifications.R as NotificationsR
import de.danoeh.antennapod.wearos.WatchPlayerActivity
import de.danoeh.antennapod.wearos.download.DownloadStatus
import de.danoeh.antennapod.wearos.download.PhoneState
import de.danoeh.antennapod.wearos.download.WatchEpisode
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore
import de.danoeh.antennapod.wearos.sync.PositionReporter
import de.danoeh.antennapod.wearos.sync.WearDataRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Plays the episodes from [WatchEpisodeStore] on the watch itself.
 * The media id of each playlist item is the episode id.
 */
@OptIn(UnstableApi::class)
class WatchPlaybackService : MediaSessionService() {
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var startPosition = 0
    private val lastSavedTimestamps = mutableMapOf<Long, Long>()
    private val completedItemIds = mutableSetOf<Long>()
    private var smartMarkAsPlayedSecs = WearSettings.DEFAULT_SMART_MARK_AS_PLAYED_SECS
    private var seekBackIncrementMs = WearSettings.DEFAULT_REWIND_SECS * 1000L
    private var seekForwardIncrementMs = WearSettings.DEFAULT_FAST_FORWARD_SECS * 1000L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                saveCurrentPosition()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val oldItemId = oldPosition.mediaItem?.mediaId?.toLongOrNull() ?: return
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                complete(oldItemId)
            } else if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_REMOVE) {
                if (oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId) {
                    savePosition(oldItemId, newPosition.positionMs)
                } else if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                    WatchEpisodeStore.get(this@WatchPlaybackService, oldItemId)
                        ?.isAlmostEnded(oldPosition.positionMs, smartMarkAsPlayedSecs) == true
                ) {
                    complete(oldItemId)
                } else if (player.playWhenReady) {
                    savePosition(oldItemId, oldPosition.positionMs)
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val itemId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
            val episode = WatchEpisodeStore.get(this@WatchPlaybackService, itemId) ?: return
            val lastSaved = lastSavedTimestamps[itemId] ?: return
            if (playWhenReady && episode.lastPlayed > lastSaved) {
                startPosition = episode.position
                player.seekTo(episode.position.toLong())
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentItemId = mediaItem?.mediaId?.toLongOrNull()
            val itemId = currentItemId ?: return
            val position = WatchEpisodeStore.get(this@WatchPlaybackService, itemId)?.position ?: 0
            startPosition = position
            if (position > 0 && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                player.seekTo(position.toLong())
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                player.currentMediaItem?.mediaId?.toLongOrNull()?.let { complete(it) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback failed", error)
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.mapNotNull { item ->
                val itemId = item.mediaId.toLongOrNull() ?: return@mapNotNull null
                val episode = WatchEpisodeStore.get(this@WatchPlaybackService, itemId) ?: return@mapNotNull null
                buildMediaItem(episode)
            }.toMutableList()
        )
    }

    private fun buildMediaItem(episode: WatchEpisode): MediaItem = MediaItem.Builder()
        .setMediaId(episode.itemId.toString())
        .setUri(WatchEpisodeStore.getFile(this, episode).toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(episode.feedTitle)
                .build()
        )
        .build()

    private fun updatePlaylist(episodes: List<WatchEpisode>) {
        completedItemIds.retainAll(episodes.map { it.itemId }.toSet())
        val currentItemId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val playlist = episodes.filter {
            it.status == DownloadStatus.DOWNLOADED && it.itemId !in completedItemIds &&
                (it.phoneState == PhoneState.UNPLAYED || it.itemId == currentItemId)
        }
        val currentIndex = playlist.indexOfFirst { it.itemId == currentItemId }
        if (currentIndex < 0) {
            if (currentItemId !in completedItemIds) {
                player.removeMediaItem(player.currentMediaItemIndex)
            }
            return
        }
        val playerItemIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (playerItemIds == playlist.map { it.itemId.toString() }) {
            return
        }
        player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
        player.removeMediaItems(0, player.currentMediaItemIndex)
        player.addMediaItems(0, playlist.subList(0, currentIndex).map { buildMediaItem(it) })
        player.addMediaItems(playlist.subList(currentIndex + 1, playlist.size).map { buildMediaItem(it) })
    }

    private fun applySettings(settings: WearSettings) {
        smartMarkAsPlayedSecs = settings.smartMarkAsPlayedSecs
        seekBackIncrementMs = settings.rewindSecs.coerceAtLeast(1) * 1000L
        seekForwardIncrementMs = settings.fastForwardSecs.coerceAtLeast(1) * 1000L
        player.setPlaybackSpeed(settings.playbackSpeed)
    }

    private fun seekBy(offsetMs: Long) {
        var positionMs = player.currentPosition + offsetMs
        if (player.duration != C.TIME_UNSET) {
            positionMs = positionMs.coerceAtMost(player.duration)
        }
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSuppressPlaybackOnUnsuitableOutput(true)
            .build()
        applySettings(WatchSettingsStore.load(this))
        player.addListener(WearUnsuitableOutputPlaybackSuppressionResolverListener(this))
        player.addListener(playerListener)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WatchPlayerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val sessionPlayer = object : ForwardingPlayer(player) {
            override fun getSeekBackIncrement() = seekBackIncrementMs

            override fun getSeekForwardIncrement() = seekForwardIncrementMs

            override fun seekBack() = seekBy(-seekBackIncrementMs)

            override fun seekForward() = seekBy(seekForwardIncrementMs)
        }
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(NotificationsR.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

        mainScope.launch {
            WatchEpisodeStore.episodes.collect { updatePlaylist(it) }
        }

        mainScope.launch {
            WearDataRepository.settings.filterNotNull().collect { applySettings(it) }
        }

        mainScope.launch {
            while (true) {
                delay(SAVE_INTERVAL)
                if (player.isPlaying) {
                    saveCurrentPosition()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        currentItemId = null
        mainScope.cancel()
        if (player.isPlaying) {
            saveCurrentPosition()
        }
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun saveCurrentPosition() {
        val itemId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        savePosition(itemId, player.currentPosition)
    }

    private fun savePosition(itemId: Long, position: Long, completed: Boolean = false) {
        val timestamp = System.currentTimeMillis()
        val startPosition = startPosition
        lastSavedTimestamps[itemId] = timestamp
        ioScope.launch {
            WatchEpisodeStore.update(applicationContext, itemId) {
                it.copy(position = position.toInt(), lastPlayed = timestamp)
            }
            val episode = WatchEpisodeStore.get(applicationContext, itemId) ?: return@launch
            PositionReporter.report(
                applicationContext,
                WearPosition(
                    itemId,
                    episode.guid,
                    episode.downloadUrl,
                    position.toInt(),
                    episode.duration,
                    startPosition,
                    timestamp,
                    completed
                )
            )
            if (completed) {
                WatchEpisodeStore.remove(applicationContext, itemId)
            }
        }
    }

    private fun complete(itemId: Long) {
        completedItemIds.add(itemId)
        val duration = WatchEpisodeStore.get(this, itemId)?.duration ?: 0
        savePosition(itemId, duration.toLong(), true)
        val index = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId == itemId.toString()
        }
        if (index != null) {
            player.removeMediaItem(index)
        }
    }

    companion object {
        private const val TAG = "WatchPlaybackService"
        private val SAVE_INTERVAL = 60.seconds

        @Volatile
        var currentItemId: Long? = null
            private set
    }
}
