package de.danoeh.antennapod.wearos.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.danoeh.antennapod.ui.common.R as CommonR
import de.danoeh.antennapod.ui.notifications.R as NotificationsR
import de.danoeh.antennapod.wearos.BuildConfig
import de.danoeh.antennapod.wearos.settings.DownloadMode
import de.danoeh.antennapod.wearos.settings.WatchPreferences
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.util.concurrent.TimeUnit
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
 * Depending on [WatchPreferences], the file is received from the phone instead or mobile data is used.
 */
class EpisodeDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val itemId = inputData.getLong(KEY_ITEM_ID, -1)
        val episode = WatchEpisodeStore.get(applicationContext, itemId) ?: return Result.failure()
        startForeground(episode)
        val downloadMode = WatchPreferences.get(applicationContext).downloadMode
        val transports = transports(downloadMode, WatchPreferences.canUseMobileData(applicationContext))
        var failed = false
        var notOnPhone = false
        try {
            for (transport in transports) {
                try {
                    val size = when (transport) {
                        Transport.BLUETOOTH -> receiveFromPhone(episode)
                        Transport.WIFI -> withNetwork(NetworkCapabilities.TRANSPORT_WIFI) { download(episode, it) }
                            ?: downloadWithoutWifi(episode)
                        Transport.MOBILE -> withNetwork(NetworkCapabilities.TRANSPORT_CELLULAR) {
                            download(episode, it)
                        }
                    } ?: continue
                    WatchEpisodeStore.update(applicationContext, itemId) {
                        it.copy(status = DownloadStatus.DOWNLOADED, size = size, failedDownloads = 0)
                    }
                    return Result.success()
                } catch (e: NotOnPhoneException) {
                    Log.d(TAG, "Episode $itemId is not downloaded on the phone")
                    notOnPhone = true
                } catch (e: InsufficientSpaceException) {
                    throw e
                } catch (e: IOException) {
                    if (isStopped) {
                        throw e
                    }
                    Log.e(TAG, "Download of episode $itemId using $transport failed", e)
                    failed = true
                }
            }
            if (failed) {
                return retryOrFail(itemId)
            }
            if (notOnPhone && downloadMode == DownloadMode.BLUETOOTH_ONLY) {
                WatchEpisodeStore.update(applicationContext, itemId) { it.copy(status = DownloadStatus.NOT_ON_PHONE) }
                return Result.failure()
            }
            val waitingStatus = when (downloadMode) {
                DownloadMode.WIFI_ONLY -> DownloadStatus.WAITING_FOR_WIFI
                DownloadMode.BLUETOOTH_ONLY -> DownloadStatus.WAITING_FOR_PHONE
                else -> DownloadStatus.WAITING_FOR_CONNECTION
            }
            WatchEpisodeStore.update(applicationContext, itemId) { it.copy(status = waitingStatus) }
            return Result.retry()
        } catch (e: InsufficientSpaceException) {
            Log.e(TAG, "Not enough space for episode $itemId", e)
            WatchEpisodeStore.update(applicationContext, itemId) { it.copy(status = DownloadStatus.FAILED) }
            return Result.failure()
        } catch (e: IOException) {
            WatchEpisodeStore.update(applicationContext, itemId) { it.copy(status = DownloadStatus.QUEUED) }
            return Result.retry()
        } finally {
            if (WatchEpisodeStore.get(applicationContext, itemId) == null) {
                WatchEpisodeStore.getFile(applicationContext, episode).delete()
            }
        }
    }

    private suspend fun <T> withNetwork(transportType: Int, block: (Network) -> T): T? {
        val connectivityManager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val available = CompletableDeferred<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                available.complete(network)
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(transportType)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.requestNetwork(request, callback)
        try {
            val network = withTimeoutOrNull(NETWORK_TIMEOUT) { available.await() } ?: return null
            Log.d(TAG, "Downloading using transport $transportType")
            WatchEpisodeStore.update(applicationContext, inputData.getLong(KEY_ITEM_ID, -1)) {
                it.copy(status = DownloadStatus.DOWNLOADING)
            }
            return withContext(Dispatchers.IO) { block(network) }
        } finally {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    private suspend fun downloadWithoutWifi(episode: WatchEpisode): Long? {
        val connectivityManager = applicationContext.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager.isActiveNetworkMetered && !BuildConfig.DEBUG) {
            Log.d(TAG, "No Wi-Fi and the active network is metered")
            return null
        }
        Log.d(TAG, "Downloading episode ${episode.itemId} using the active network")
        WatchEpisodeStore.update(applicationContext, episode.itemId) { it.copy(status = DownloadStatus.DOWNLOADING) }
        return withContext(Dispatchers.IO) { download(episode, null) }
    }

    private suspend fun receiveFromPhone(episode: WatchEpisode): Long? {
        WatchEpisodeStore.update(applicationContext, episode.itemId) { it.copy(status = DownloadStatus.DOWNLOADING) }
        return PhoneFileReceiver.receive(
            applicationContext,
            episode.itemId,
            WatchEpisodeStore.getFile(applicationContext, episode),
            isStopped = { isStopped },
            checkSpace = { total, missing ->
                if (!WatchEpisodeStore.hasSpaceFor(applicationContext, episode.itemId, total, missing)) {
                    throw InsufficientSpaceException()
                }
            },
            onProgress = { WatchEpisodeStore.setProgress(episode.itemId, it) }
        )
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
            if (total > 0 && !WatchEpisodeStore.hasSpaceFor(applicationContext, episode.itemId, total,
                    total - downloaded)) {
                throw InsufficientSpaceException()
            }
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

    private suspend fun startForeground(episode: WatchEpisode) {
        val context = applicationContext
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(CommonR.string.notification_channel_downloading))
                .setDescription(context.getString(CommonR.string.notification_channel_downloading_description))
                .build()
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(NotificationsR.drawable.ic_notification_sync)
            .setContentTitle(episode.title)
            .setContentText(context.getString(CommonR.string.download_running))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val notificationId = NOTIFICATION_ID_BASE + (episode.itemId % NOTIFICATION_ID_RANGE).toInt()
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
        try {
            setForeground(foregroundInfo)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Downloading episode ${episode.itemId} without foreground service", e)
        }
    }

    private fun retryOrFail(itemId: Long): Result {
        val failedDownloads = (WatchEpisodeStore.get(applicationContext, itemId)?.failedDownloads ?: 0) + 1
        val retry = failedDownloads <= MAX_RETRIES
        WatchEpisodeStore.update(applicationContext, itemId) {
            it.copy(
                status = if (retry) DownloadStatus.QUEUED else DownloadStatus.FAILED,
                failedDownloads = failedDownloads
            )
        }
        return if (retry) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "EpisodeDownloadWorker"
        private const val KEY_ITEM_ID = "item_id"
        private const val MAX_RETRIES = 3
        private const val BUFFER_SIZE = 8192
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val NOTIFICATION_CHANNEL_ID = "watch_downloads"
        private const val NOTIFICATION_ID_BASE = 1000
        private const val NOTIFICATION_ID_RANGE = 100000
        private const val BACKOFF_MINUTES = 1L
        private val NETWORK_TIMEOUT = 30.seconds

        /**
         * Mobile data is only used if no Wi-Fi is available, so it always directly follows Wi-Fi.
         */
        fun transports(mode: DownloadMode, useMobileData: Boolean): List<Transport> {
            val wifi = if (useMobileData) listOf(Transport.WIFI, Transport.MOBILE) else listOf(Transport.WIFI)
            return when (mode) {
                DownloadMode.WIFI_ONLY -> wifi
                DownloadMode.BLUETOOTH_ONLY -> listOf(Transport.BLUETOOTH)
                DownloadMode.PREFER_WIFI -> wifi + Transport.BLUETOOTH
                DownloadMode.PREFER_BLUETOOTH -> listOf(Transport.BLUETOOTH) + wifi
            }
        }

        private fun workName(itemId: Long) = "download_$itemId"

        fun enqueue(context: Context, itemId: Long) {
            val request = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
                .setInputData(workDataOf(KEY_ITEM_ID to itemId))
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(itemId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, itemId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(itemId))
        }
    }
}

enum class Transport { WIFI, MOBILE, BLUETOOTH }

private class InsufficientSpaceException : IOException("Not enough space on the watch")
