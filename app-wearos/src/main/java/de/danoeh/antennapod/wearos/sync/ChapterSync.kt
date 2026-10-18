package de.danoeh.antennapod.wearos.sync

import android.content.Context
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fetches the chapters of an episode on the watch from the phone and stores them with the episode,
 * so they are available without the phone.
 */
object ChapterSync {
    private val TIMEOUT = 15.seconds

    suspend fun syncFromPhone(context: Context, itemId: Long) = withContext(Dispatchers.IO) {
        WearMessageSender.send(context, WearDataPaths.chaptersPath(itemId))
        val chapters = withTimeoutOrNull(TIMEOUT) {
            WearDataRepository.chapters.first { it.containsKey(itemId) }
        }?.get(itemId) ?: return@withContext
        WatchEpisodeStore.update(context, itemId) { it.copy(chapters = chapters) }
    }
}
