package de.danoeh.antennapod.wearos.playback

import android.content.Context
import androidx.core.content.edit
import de.danoeh.antennapod.net.sync.wearinterface.WearSettings

/**
 * Persists the playback settings last received from the phone, so playback on the watch
 * behaves the same when the phone is not reachable.
 */
object WatchSettingsStore {
    private const val PREFS_NAME = "watch_settings"
    private const val KEY_REWIND_SECS = "rewind_secs"
    private const val KEY_FAST_FORWARD_SECS = "fast_forward_secs"
    private const val KEY_SMART_MARK_AS_PLAYED_SECS = "smart_mark_as_played_secs"
    private const val KEY_PLAYBACK_SPEED = "playback_speed"

    fun save(context: Context, settings: WearSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(KEY_REWIND_SECS, settings.rewindSecs)
            putInt(KEY_FAST_FORWARD_SECS, settings.fastForwardSecs)
            putInt(KEY_SMART_MARK_AS_PLAYED_SECS, settings.smartMarkAsPlayedSecs)
            putFloat(KEY_PLAYBACK_SPEED, settings.playbackSpeed)
        }
    }

    fun load(context: Context): WearSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WearSettings(
            prefs.getInt(KEY_REWIND_SECS, WearSettings.DEFAULT_REWIND_SECS),
            prefs.getInt(KEY_FAST_FORWARD_SECS, WearSettings.DEFAULT_FAST_FORWARD_SECS),
            prefs.getInt(KEY_SMART_MARK_AS_PLAYED_SECS, WearSettings.DEFAULT_SMART_MARK_AS_PLAYED_SECS),
            prefs.getFloat(KEY_PLAYBACK_SPEED, WearSettings.DEFAULT_PLAYBACK_SPEED)
        )
    }
}
