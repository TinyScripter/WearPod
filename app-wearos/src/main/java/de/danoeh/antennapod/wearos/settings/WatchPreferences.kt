package de.danoeh.antennapod.wearos.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DownloadMode { WIFI_ONLY, BLUETOOTH_ONLY, PREFER_WIFI, PREFER_BLUETOOTH }

data class WatchPreferenceValues(
    val downloadMode: DownloadMode = DownloadMode.WIFI_ONLY,
    val useMobileData: Boolean = false,
    val showImages: Boolean = true,
    val showPlaybackSpeed: Boolean = true,
    val showChapters: Boolean = true
)

/**
 * Settings that are only made on the watch.
 */
object WatchPreferences {
    private const val PREFS_NAME = "watch_preferences"
    private const val KEY_DOWNLOAD_MODE = "download_mode"
    private const val KEY_USE_MOBILE_DATA = "use_mobile_data"
    private const val KEY_SHOW_IMAGES = "show_images"
    private const val KEY_SHOW_PLAYBACK_SPEED = "show_playback_speed"
    private const val KEY_SHOW_CHAPTERS = "show_chapters"

    private val _values = MutableStateFlow(WatchPreferenceValues())
    private var loaded = false

    @Synchronized
    fun observe(context: Context): StateFlow<WatchPreferenceValues> {
        if (!loaded) {
            loaded = true
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val defaults = WatchPreferenceValues()
            _values.value = WatchPreferenceValues(
                downloadMode = DownloadMode.entries.find { it.name == prefs.getString(KEY_DOWNLOAD_MODE, null) }
                    ?: defaults.downloadMode,
                useMobileData = prefs.getBoolean(KEY_USE_MOBILE_DATA, defaults.useMobileData),
                showImages = prefs.getBoolean(KEY_SHOW_IMAGES, defaults.showImages),
                showPlaybackSpeed = prefs.getBoolean(KEY_SHOW_PLAYBACK_SPEED, defaults.showPlaybackSpeed),
                showChapters = prefs.getBoolean(KEY_SHOW_CHAPTERS, defaults.showChapters)
            )
        }
        return _values
    }

    fun get(context: Context): WatchPreferenceValues = observe(context).value

    @Synchronized
    fun update(context: Context, transform: (WatchPreferenceValues) -> WatchPreferenceValues) {
        val values = transform(get(context))
        _values.value = values
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_DOWNLOAD_MODE, values.downloadMode.name)
            putBoolean(KEY_USE_MOBILE_DATA, values.useMobileData)
            putBoolean(KEY_SHOW_IMAGES, values.showImages)
            putBoolean(KEY_SHOW_PLAYBACK_SPEED, values.showPlaybackSpeed)
            putBoolean(KEY_SHOW_CHAPTERS, values.showChapters)
        }
    }

    fun hasMobileData(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    fun canUseMobileData(context: Context): Boolean = hasMobileData(context) && get(context).useMobileData
}
