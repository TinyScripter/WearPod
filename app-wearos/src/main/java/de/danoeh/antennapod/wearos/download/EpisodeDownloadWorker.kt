package de.danoeh.antennapod.wearos.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.danoeh.antennapod.wearos.BuildConfig
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads the media file of a single episode from [WatchEpisodeStore] to the watch.
 * Explicitly requests a Wi-Fi network because Wear OS keeps Wi-Fi turned off while the phone is connected.
 */
class EpisodeDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val itemId = inputData.getLong(KEY_ITEM_ID, -1)
        val episode = WatchEpisodeStore.get(applicationContext, itemId) ?: return Result.failure()
        val connectivityManager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val wifi = CompletableDeferred<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifi.complete(network)
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.requestNetwork(request, callback)
        try {
            val network = withTimeoutOrNull(WIFI_TIMEOUT) { wifi.await() }
            if (network == null && connectivityManager.isActiveNetworkMetered && !BuildConfig.DEBUG) {
                Log.d(TAG, "No Wi-Fi and the active network is metered")
                return retryOrFail(itemId, DownloadStatus.WAITING_FOR_WIFI)
            }
            Log.d(TAG, "Downloading episode $itemId using " + if (network != null) "Wi-Fi" else "the active network")
            WatchEpisodeStore.update(applicationContext, itemId) { it.copy(status = DownloadStatus.DOWNLOADING) }
            val size = withContext(Dispatchers.IO) { download(episode, network) }
            WatchEpisodeStore.update(applicationContext, itemId) {
                it.copy(status = DownloadStatus.DOWNLOADED, size = size)
            }
            return Result.success()
        } catch (e: IOException) {
            Log.e(TAG, "Download of episode $itemId failed", e)
            return retryOrFail(itemId, DownloadStatus.QUEUED)
        } finally {
            connectivityManager.unregisterNetworkCallback(callback)
            if (WatchEpisodeStore.get(applicationContext, itemId) == null) {
                WatchEpisodeStore.getFile(applicationContext, episode).delete()
            }
        }
    }

    private fun download(episode: WatchEpisode, network: Network?): Long {
        val url = episode.downloadUrl.toHttpUrlOrNull() ?: throw IOException("Invalid url")
        val file = WatchEpisodeStore.getFile(applicationContext, episode)
        file.parentFile?.mkdirs()
        val existing = file.length()
        val clientBuilder = OkHttpClient.Builder()
        if (network != null) {
            clientBuilder.socketFactory(network.socketFactory)
            clientBuilder.dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> = network.getAllByName(hostname).toList()
            })
        }
        val requestBuilder = Request.Builder().url(url).header("User-Agent", "AntennaPod/" + BuildConfig.VERSION_NAME)
        if (existing > 0) {
            requestBuilder.header("Range", "bytes=$existing-")
        }
        clientBuilder.build().newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == HTTP_RANGE_NOT_SATISFIABLE) {
                val total = response.header("Content-Range")?.substringAfter("*/", "")?.toLongOrNull()
                if (total == existing) {
                    return existing
                }
                file.delete()
            }
            val body = response.body
            if (!response.isSuccessful || body == null) {
                throw IOException("Unexpected response " + response.code)
            }
            val append = response.code == HttpURLConnection.HTTP_PARTIAL
            var downloaded = if (append) existing else 0L
            val total = if (body.contentLength() >= 0) body.contentLength() + downloaded else -1L
            var lastPercent = -1
            FileOutputStream(file, append).use { output ->
                val input = body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var read = input.read(buffer)
                while (read != -1 && !isStopped) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
                    if (percent != lastPercent) {
                        lastPercent = percent
                        WatchEpisodeStore.setProgress(episode.itemId, percent)
                    }
                    read = input.read(buffer)
                }
            }
            if (isStopped) {
                throw IOException("Download of episode ${episode.itemId} was stopped")
            }
            return downloaded
        }
    }

    private fun retryOrFail(itemId: Long, retryStatus: DownloadStatus): Result {
        val retry = runAttemptCount < MAX_RETRIES
        WatchEpisodeStore.update(applicationContext, itemId) {
            it.copy(status = if (retry) retryStatus else DownloadStatus.FAILED)
        }
        return if (retry) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "EpisodeDownloadWorker"
        private const val KEY_ITEM_ID = "item_id"
        private const val MAX_RETRIES = 3
        private const val BUFFER_SIZE = 8192
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private val WIFI_TIMEOUT = 30.seconds

        private fun workName(itemId: Long) = "download_$itemId"

        fun enqueue(context: Context, itemId: Long) {
            val request = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
                .setInputData(workDataOf(KEY_ITEM_ID to itemId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(itemId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, itemId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(itemId))
        }
    }
}
