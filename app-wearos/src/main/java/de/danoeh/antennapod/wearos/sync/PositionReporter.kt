package de.danoeh.antennapod.wearos.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.net.sync.wearinterface.WearPosition
import de.danoeh.antennapod.net.sync.wearinterface.WearSerializer
import kotlinx.coroutines.tasks.await

/**
 * Reports playback positions of the watch to the phone.
 * Uses data items instead of messages, so the last position of each episode
 * is delivered as soon as the phone is reachable again.
 */
object PositionReporter {
    private const val TAG = "PositionReporter"

    suspend fun report(context: Context, position: WearPosition) {
        try {
            val request = PutDataRequest.create(WearDataPaths.watchPositionPath(position.itemId))
                .setData(WearSerializer.positionToBytes(position))
                .setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report position to phone", e)
        }
    }
}
