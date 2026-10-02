package de.danoeh.antennapod.wearos.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import de.danoeh.antennapod.ui.notifications.R as NotificationsR
import de.danoeh.antennapod.wearos.WatchPlayerActivity
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays the episodes from [WatchEpisodeStore] on the watch itself.
 * The media id of each playlist item is the episode id.
 */
@OptIn(UnstableApi::class)
class WatchPlaybackService : MediaSessionService() {
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

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
                val isSameItem = oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId
                savePosition(oldItemId, if (isSameItem) newPosition.positionMs else oldPosition.positionMs)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                return
            }
            val itemId = mediaItem?.mediaId?.toLongOrNull() ?: return
            val position = WatchEpisodeStore.get(this@WatchPlaybackService, itemId)?.position ?: 0
            if (position > 0) {
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
                item.buildUpon()
                    .setUri(WatchEpisodeStore.getFile(this@WatchPlaybackService, episode).toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(episode.title)
                            .setArtist(episode.feedTitle)
                            .build()
                    )
                    .build()
            }.toMutableList()
        )
    }

    override fun onCreate() {
        super.onCreate()
        val settings = WatchSettingsStore.load(this)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(settings.rewindSecs.coerceAtLeast(1) * 1000L)
            .setSeekForwardIncrementMs(settings.fastForwardSecs.coerceAtLeast(1) * 1000L)
            .setSuppressPlaybackOnUnsuitableOutput(true)
            .build()
        player.setPlaybackSpeed(settings.playbackSpeed)
        player.addListener(WearUnsuitableOutputPlaybackSuppressionResolverListener(this))
        player.addListener(playerListener)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WatchPlayerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(NotificationsR.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

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
        mainScope.cancel()
        saveCurrentPosition()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun saveCurrentPosition() {
        val itemId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        savePosition(itemId, player.currentPosition)
    }

    private fun savePosition(itemId: Long, position: Long) {
        val timestamp = System.currentTimeMillis()
        ioScope.launch {
            WatchEpisodeStore.update(applicationContext, itemId) {
                it.copy(position = position.toInt(), lastPlayed = timestamp)
            }
        }
    }

    private fun complete(itemId: Long) {
        val index = (0 until player.mediaItemCount).firstOrNull {
            player.getMediaItemAt(it).mediaId == itemId.toString()
        }
        if (index != null) {
            player.removeMediaItem(index)
        }
        ioScope.launch {
            WatchEpisodeStore.remove(applicationContext, itemId)
        }
    }

    companion object {
        private const val TAG = "WatchPlaybackService"
        private val SAVE_INTERVAL = 60.seconds
    }
}
