package de.danoeh.antennapod.wearos.download

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import de.danoeh.antennapod.net.sync.wearinterface.R as WearInterfaceR
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class NotOnPhoneException : IOException("Episode is not downloaded on the phone")

/**
 * Receives the media file of an episode that is downloaded on the phone over the Wearable channel API.
 */
object PhoneFileReceiver {
    private val CHANNEL_TIMEOUT = 30.seconds
    private const val BUFFER_SIZE = 8192

    /**
     * Returns the size of the received file, or null if the phone is not reachable.
     */
    suspend fun receive(
        context: Context,
        itemId: Long,
        file: File,
        isStopped: () -> Boolean,
        checkSpace: (total: Long, missing: Long) -> Unit,
        onProgress: (Int) -> Unit
    ): Long? {
        try {
            val node = Wearable.getCapabilityClient(context)
                .getCapability(
                    context.getString(WearInterfaceR.string.wear_capability_phone_standalone),
                    CapabilityClient.FILTER_REACHABLE
                ).await().nodes.firstOrNull() ?: return null
            val path = WearDataPaths.episodeFilePath(itemId)
            val channelClient = Wearable.getChannelClient(context)
            val opened = CompletableDeferred<ChannelClient.Channel>()
            val callback = object : ChannelClient.ChannelCallback() {
                override fun onChannelOpened(channel: ChannelClient.Channel) {
                    if (channel.path == path) {
                        opened.complete(channel)
                    }
                }
            }
            channelClient.registerChannelCallback(callback).await()
            var channel: ChannelClient.Channel? = null
            try {
                file.parentFile?.mkdirs()
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, path, file.length().toString().toByteArray()).await()
                channel = withTimeoutOrNull(CHANNEL_TIMEOUT) { opened.await() } ?: return null
                val stream = channelClient.getInputStream(channel).await()
                return withContext(Dispatchers.IO) {
                    DataInputStream(BufferedInputStream(stream)).use { copy(it, file, isStopped, checkSpace, onProgress) }
                }
            } finally {
                channel?.let { channelClient.close(it) }
                channelClient.unregisterChannelCallback(callback)
            }
        } catch (e: ApiException) {
            throw IOException(e)
        }
    }

    private fun copy(
        input: DataInputStream,
        file: File,
        isStopped: () -> Boolean,
        checkSpace: (total: Long, missing: Long) -> Unit,
        onProgress: (Int) -> Unit
    ): Long {
        val total = input.readLong()
        val start = input.readLong()
        if (total < 0) {
            throw NotOnPhoneException()
        }
        checkSpace(total, total - start)
        var received = start
        var lastPercent = -1
        FileOutputStream(file, start > 0).use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read = input.read(buffer)
            while (read != -1 && !isStopped()) {
                output.write(buffer, 0, read)
                received += read
                val percent = if (total > 0) (received * 100 / total).toInt() else 0
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
                read = input.read(buffer)
            }
        }
        if (received != total) {
            throw IOException("Received $received of $total bytes")
        }
        return total
    }
}
