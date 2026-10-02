package de.danoeh.antennapod.wearos.download

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.readText
import androidx.core.util.writeText
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
    fun hasSpaceFor(context: Context, itemId: Long, size: Long): Boolean {
        load(context)
        val used = _episodes.value.filter { it.itemId != itemId }.sumOf { it.size.coerceAtLeast(0) }
        return used + size.coerceAtLeast(0) <= STORAGE_LIMIT_BYTES
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
