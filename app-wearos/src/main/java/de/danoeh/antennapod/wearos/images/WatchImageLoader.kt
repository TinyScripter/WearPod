package de.danoeh.antennapod.wearos.images

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.wearable.Wearable
import de.danoeh.antennapod.wearos.settings.WatchPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Loads small podcast and episode images from the phone.
 * Images are cached in memory and on disk, so they are also shown without the phone.
 */
object WatchImageLoader {
    private const val TAG = "WatchImageLoader"
    private const val IMAGES_DIR = "images"
    private const val MEMORY_CACHE_SIZE = 100
    private const val PARALLEL_REQUESTS = 3
    private val MAX_AGE = 7.days
    private val TIMEOUT = 10.seconds

    private val memoryCache = LruCache<String, ImageBitmap>(MEMORY_CACHE_SIZE)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val semaphore = Semaphore(PARALLEL_REQUESTS)

    fun cached(path: String): ImageBitmap? = memoryCache.get(path)

    suspend fun load(context: Context, path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memoryCache.get(path)?.let { return@withContext it }
        val file = getFile(context, path)
        val stored = if (file.exists()) decode(file.readBytes()) else null
        if (stored != null && System.currentTimeMillis() - file.lastModified() < MAX_AGE.inWholeMilliseconds) {
            memoryCache.put(path, stored)
            return@withContext stored
        }
        if (path in failed) {
            return@withContext stored
        }
        val data = semaphore.withPermit { request(context, path) }
        val image = data?.let { decode(it) }
        if (image == null) {
            if (data != null) {
                failed.add(path)
            }
            stored?.let { memoryCache.put(path, it) }
            return@withContext stored
        }
        file.parentFile?.mkdirs()
        file.writeBytes(data)
        memoryCache.put(path, image)
        image
    }

    fun remove(context: Context, path: String) {
        memoryCache.remove(path)
        failed.remove(path)
        getFile(context, path).delete()
    }

    private fun getFile(context: Context, path: String): File =
        File(File(context.filesDir, IMAGES_DIR), path.trim('/').replace('/', '_') + ".jpg")

    fun onImageReceived(path: String, data: ByteArray) {
        pending.remove(path)?.complete(data)
    }

    private suspend fun request(context: Context, path: String): ByteArray? {
        try {
            val node = Wearable.getNodeClient(context).connectedNodes.await().firstOrNull() ?: return null
            val deferred = pending.getOrPut(path) { CompletableDeferred() }
            Wearable.getMessageClient(context).sendMessage(node.id, path, null).await()
            return withTimeoutOrNull(TIMEOUT) { deferred.await() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request image $path", e)
            return null
        }
    }

    private fun decode(data: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
}

@Composable
fun rememberWatchImage(path: String): ImageBitmap? {
    val context = LocalContext.current
    val preferences by WatchPreferences.observe(context).collectAsState()
    if (!preferences.showImages) {
        return null
    }
    val image by produceState(WatchImageLoader.cached(path), path) {
        value = WatchImageLoader.load(context, path)
    }
    return image
}
