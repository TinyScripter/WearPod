package de.danoeh.antennapod.wearos.sync

import android.content.Context
import de.danoeh.antennapod.net.sync.wearinterface.WearConnectionUtils
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.net.sync.wearinterface.WearSerializer
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fetches the playback state of the episodes on the watch from the phone.
 * Does nothing if the phone is not reachable, so playback on the watch still works offline.
 */
object PositionSync {
    private val TIMEOUT = 3.seconds
    private val mutex = Mutex()

    suspend fun syncFromPhone(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            WatchEpisodeStore.load(context)
            val ids = WatchEpisodeStore.episodes.value.map { it.itemId }
            if (ids.isEmpty() || !WearConnectionUtils.isPhoneStandaloneSupported(context)) {
                return@withLock
            }
            WearDataRepository.clearEpisodes(WearDataPaths.POSITIONS)
            WearMessageSender.send(context, WearDataPaths.POSITIONS, WearSerializer.idsToBytes(ids))
            val states = withTimeoutOrNull(TIMEOUT) {
                WearDataRepository.episodesByPath.first { it.containsKey(WearDataPaths.POSITIONS) }
            }?.get(WearDataPaths.POSITIONS) ?: return@withLock
            WatchEpisodeStore.applyPhoneStates(context, ids, states)
        }
    }
}
