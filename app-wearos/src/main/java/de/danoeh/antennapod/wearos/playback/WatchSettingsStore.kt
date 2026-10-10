package de.danoeh.antennapod.wearos.playback

import android.content.Context
import androidx.core.content.edit
import de.danoeh.antennapod.net.sync.wearinterface.WearSettings

/**
 * Persists the playback settings last received from the phone, so playback on the watch
 * behaves the same when the phone is not reachable.
 * A playback speed changed on the watch is kept as pending until the phone reports the same speed.
 */
object WatchSettingsStore {
    private const val PREFS_NAME = "watch_settings"
    private const val KEY_REWIND_SECS = "rewind_secs"
    private const val KEY_FAST_FORWARD_SECS = "fast_forward_secs"
    private const val KEY_SMART_MARK_AS_PLAYED_SECS = "smart_mark_as_played_secs"
    private const val KEY_PLAYBACK_SPEED = "playback_speed"
    private const val KEY_PLAYBACK_SPEEDS = "playback_speeds"
    private const val KEY_PENDING_PLAYBACK_SPEED = "pending_playback_speed"
    private const val KEY_TEMPORARY_SPEED_ITEM_ID = "temporary_speed_item_id"
    private const val KEY_TEMPORARY_SPEED = "temporary_speed"
    private const val NO_SPEED = -1f

    fun save(context: Context, settings: WearSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putInt(KEY_REWIND_SECS, settings.rewindSecs)
            putInt(KEY_FAST_FORWARD_SECS, settings.fastForwardSecs)
            putInt(KEY_SMART_MARK_AS_PLAYED_SECS, settings.smartMarkAsPlayedSecs)
            putFloat(KEY_PLAYBACK_SPEED, settings.playbackSpeed)
            putString(KEY_PLAYBACK_SPEEDS, settings.playbackSpeeds.joinToString(","))
            if (prefs.getFloat(KEY_PENDING_PLAYBACK_SPEED, NO_SPEED) == settings.playbackSpeed) {
                remove(KEY_PENDING_PLAYBACK_SPEED)
            }
        }
    }

    fun load(context: Context): WearSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pendingSpeed = prefs.getFloat(KEY_PENDING_PLAYBACK_SPEED, NO_SPEED)
        val speeds = prefs.getString(KEY_PLAYBACK_SPEEDS, null)
            ?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.isNotEmpty() }
            ?: WearSettings.DEFAULT_PLAYBACK_SPEEDS
        return WearSettings(
            prefs.getInt(KEY_REWIND_SECS, WearSettings.DEFAULT_REWIND_SECS),
            prefs.getInt(KEY_FAST_FORWARD_SECS, WearSettings.DEFAULT_FAST_FORWARD_SECS),
            prefs.getInt(KEY_SMART_MARK_AS_PLAYED_SECS, WearSettings.DEFAULT_SMART_MARK_AS_PLAYED_SECS),
            if (pendingSpeed > 0) pendingSpeed else prefs.getFloat(KEY_PLAYBACK_SPEED, WearSettings.DEFAULT_PLAYBACK_SPEED),
            speeds
        )
    }

    /**
     * Like on the phone, a speed changed during playback becomes the global speed
     * and is used for the current episode even if its podcast has its own speed.
     */
    fun setPlaybackSpeed(context: Context, itemId: Long, speed: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putFloat(KEY_PENDING_PLAYBACK_SPEED, speed)
            putLong(KEY_TEMPORARY_SPEED_ITEM_ID, itemId)
            putFloat(KEY_TEMPORARY_SPEED, speed)
        }
    }

    fun getTemporarySpeed(context: Context, itemId: Long): Float? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_TEMPORARY_SPEED_ITEM_ID, -1) != itemId) {
            return null
        }
        return prefs.getFloat(KEY_TEMPORARY_SPEED, NO_SPEED).takeIf { it > 0 }
    }
}
