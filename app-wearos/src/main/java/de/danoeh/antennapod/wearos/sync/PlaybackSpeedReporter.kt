package de.danoeh.antennapod.wearos.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.net.sync.wearinterface.WearSerializer
import kotlinx.coroutines.tasks.await

/**
 * Reports a playback speed changed on the watch to the phone.
 * Uses a data item, so the speed is delivered as soon as the phone is reachable again.
 */
object PlaybackSpeedReporter {
    private const val TAG = "PlaybackSpeedReporter"

    suspend fun report(context: Context, speed: Float) {
        try {
            val request = PutDataRequest.create(WearDataPaths.WATCH_PLAYBACK_SPEED)
                .setData(WearSerializer.playbackSpeedToBytes(speed))
                .setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report playback speed to phone", e)
        }
    }
}
