package de.danoeh.antennapod.wearos.download

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.readText
import androidx.core.util.writeText
import de.danoeh.antennapod.model.feed.FeedItem
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException

/**
 * Singleton holding the episodes that were selected for playback on the watch, in the order chosen on the watch.
 * Persisted as a JSON file, so the list survives restarts and is available without the phone.
 */
object WatchEpisodeStore {
    private const val TAG = "WatchEpisodeStore"
    private const val FILE_NAME = "watch_episodes.json"
    private const val EPISODES_DIR = "episodes"
    private const val STORAGE_LIMIT_BYTES = 1024L * 1024 * 1024
    private const val MIN_FREE_BYTES = 100L * 1024 * 1024

    private val _episodes = MutableStateFlow<List<WatchEpisode>>(emptyList())
    val episodes: StateFlow<List<WatchEpisode>> = _episodes

    private val _progress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val progress: StateFlow<Map<Long, Int>> = _progress

    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) {
            return
        }
        loaded = true
        try {
            val array = JSONArray(AtomicFile(File(context.filesDir, FILE_NAME)).readText())
            _episodes.value = (0 until array.length()).map { WatchEpisode.fromJson(array.getJSONObject(it)) }
        } catch (e: IOException) {
            Log.d(TAG, "No stored watch episodes", e)
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to parse watch episodes", e)
        }
    }

    fun getFile(context: Context, episode: WatchEpisode): File =
        File(File(context.filesDir, EPISODES_DIR), episode.fileName)

    @Synchronized
    fun get(context: Context, itemId: Long): WatchEpisode? {
        load(context)
        return _episodes.value.find { it.itemId == itemId }
    }

    @Synchronized
    fun hasSpaceFor(context: Context, itemId: Long, size: Long, missingBytes: Long = size): Boolean {
        load(context)
        val used = _episodes.value.filter { it.itemId != itemId }.sumOf { it.size.coerceAtLeast(0) }
        return used + size.coerceAtLeast(0) <= STORAGE_LIMIT_BYTES &&
            missingBytes.coerceAtLeast(0) + MIN_FREE_BYTES <= freeBytes(context)
    }

    fun freeBytes(context: Context): Long {
        val dir = context.filesDir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val storageManager = context.getSystemService(StorageManager::class.java)
                return storageManager.getAllocatableBytes(storageManager.getUuidForPath(dir))
            } catch (e: IOException) {
                Log.w(TAG, "Failed to get allocatable bytes", e)
            }
        }
        return dir.usableSpace
    }

    @Synchronized
    fun put(context: Context, episode: WatchEpisode) {
        load(context)
        if (_episodes.value.any { it.itemId == episode.itemId }) {
            _episodes.value = _episodes.value.map { if (it.itemId == episode.itemId) episode else it }
        } else {
            _episodes.value = _episodes.value + episode
        }
        save(context)
    }

    @Synchronized
    fun update(context: Context, itemId: Long, transform: (WatchEpisode) -> WatchEpisode) {
        load(context)
        _episodes.value = _episodes.value.map { if (it.itemId == itemId) transform(it) else it }
        save(context)
    }

    @Synchronized
    fun move(context: Context, itemId: Long, newIndex: Int) {
        load(context)
        val episodes = _episodes.value.toMutableList()
        val index = episodes.indexOfFirst { it.itemId == itemId }
        if (index < 0) {
            return
        }
        val episode = episodes.removeAt(index)
        episodes.add(newIndex.coerceIn(0, episodes.size), episode)
        _episodes.value = episodes
        save(context)
    }

    @Synchronized
    fun applyPhoneStates(
        context: Context,
        requestedIds: List<Long>,
        states: List<FeedItem>,
        keepItemIds: Set<Long> = emptySet()
    ): List<Long> {
        load(context)
        val statesById = states.associateBy { it.id }
        val episodes = _episodes.value.map {
            if (it.itemId !in requestedIds) {
                it
            } else if (it.itemId in keepItemIds) {
                it.withPhoneState(statesById[it.itemId]).copy(phoneState = it.phoneState)
            } else {
                it.withPhoneState(statesById[it.itemId])
            }
        }
        val finishedOnPhone = episodes.filter { episode ->
            episode.phoneState != PhoneState.UNPLAYED &&
                _episodes.value.any { it.itemId == episode.itemId && it.phoneState == PhoneState.UNPLAYED }
        }
        val finishedIds = finishedOnPhone.map { it.itemId }
        _episodes.value = episodes.filter { it.itemId !in finishedIds }
        _progress.update { it - finishedIds.toSet() }
        save(context)
        finishedOnPhone.forEach { getFile(context, it).delete() }
        return finishedIds
    }

    @Synchronized
    fun remove(context: Context, itemId: Long) {
        val episode = get(context, itemId) ?: return
        _episodes.value = _episodes.value - episode
        _progress.update { it - itemId }
        save(context)
        getFile(context, episode).delete()
    }

    fun setProgress(itemId: Long, percent: Int) {
        _progress.update { it + (itemId to percent) }
    }

    private fun save(context: Context) {
        val array = JSONArray()
        _episodes.value.forEach { array.put(it.toJson()) }
        try {
            AtomicFile(File(context.filesDir, FILE_NAME)).writeText(array.toString())
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save watch episodes", e)
        }
    }
}
