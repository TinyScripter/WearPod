package de.danoeh.antennapod.wearos.download

import de.danoeh.antennapod.model.feed.Chapter
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class DownloadStatus {
    QUEUED, WAITING_FOR_WIFI, WAITING_FOR_PHONE, WAITING_FOR_CONNECTION, DOWNLOADING, DOWNLOADED, FAILED, NOT_ON_PHONE;

    val isFailed: Boolean
        get() = this == FAILED || this == NOT_ON_PHONE
}

enum class PhoneState { UNPLAYED, PLAYED, DELETED }

data class WatchEpisode(
    val itemId: Long,
    val guid: String?,
    val downloadUrl: String,
    val title: String,
    val feedTitle: String,
    val duration: Int,
    val position: Int,
    val lastPlayed: Long,
    val size: Long,
    val fileName: String,
    val status: DownloadStatus,
    val phoneState: PhoneState = PhoneState.UNPLAYED,
    val failedDownloads: Int = 0,
    val feedPlaybackSpeed: Float = FeedPreferences.SPEED_USE_GLOBAL,
    val chapters: List<Chapter>? = null
) {
    fun withPhoneState(state: FeedItem?): WatchEpisode {
        if (state == null) {
            return copy(phoneState = PhoneState.DELETED)
        }
        val newPhoneState = if (state.isPlayed) PhoneState.PLAYED else PhoneState.UNPLAYED
        val phoneLastPlayed = state.media?.lastPlayedTimeHistory?.time ?: 0
        if (phoneLastPlayed <= lastPlayed) {
            return copy(phoneState = newPhoneState)
        }
        return copy(
            position = state.media?.position ?: position,
            lastPlayed = phoneLastPlayed,
            phoneState = newPhoneState
        )
    }

    fun isAlmostEnded(position: Long, smartMarkAsPlayedSecs: Int): Boolean =
        duration > 0 && position >= duration - smartMarkAsPlayedSecs * 1000L

    fun toJson(): JSONObject = JSONObject()
        .put(KEY_ITEM_ID, itemId)
        .putOpt(KEY_GUID, guid)
        .put(KEY_DOWNLOAD_URL, downloadUrl)
        .put(KEY_TITLE, title)
        .put(KEY_FEED_TITLE, feedTitle)
        .put(KEY_DURATION, duration)
        .put(KEY_POSITION, position)
        .put(KEY_LAST_PLAYED, lastPlayed)
        .put(KEY_SIZE, size)
        .put(KEY_FILE_NAME, fileName)
        .put(KEY_STATUS, status.name)
        .put(KEY_PHONE_STATE, phoneState.name)
        .put(KEY_FAILED_DOWNLOADS, failedDownloads)
        .put(KEY_FEED_PLAYBACK_SPEED, feedPlaybackSpeed.toDouble())
        .putOpt(
            KEY_CHAPTERS,
            chapters?.let { list ->
                JSONArray().apply {
                    list.forEach { put(JSONObject().put(KEY_START, it.start).put(KEY_TITLE, it.title ?: "")) }
                }
            }
        )

    companion object {
        private const val KEY_ITEM_ID = "item_id"
        private const val KEY_GUID = "guid"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_TITLE = "title"
        private const val KEY_FEED_TITLE = "feed_title"
        private const val KEY_DURATION = "duration"
        private const val KEY_POSITION = "position"
        private const val KEY_LAST_PLAYED = "last_played"
        private const val KEY_SIZE = "size"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_STATUS = "status"
        private const val KEY_PHONE_STATE = "phone_state"
        private const val KEY_FAILED_DOWNLOADS = "failed_downloads"
        private const val KEY_FEED_PLAYBACK_SPEED = "feed_playback_speed"
        private const val KEY_CHAPTERS = "chapters"
        private const val KEY_START = "start"

        fun fromJson(obj: JSONObject) = WatchEpisode(
            itemId = obj.getLong(KEY_ITEM_ID),
            guid = if (obj.has(KEY_GUID)) obj.getString(KEY_GUID) else null,
            downloadUrl = obj.getString(KEY_DOWNLOAD_URL),
            title = obj.optString(KEY_TITLE),
            feedTitle = obj.optString(KEY_FEED_TITLE),
            duration = obj.optInt(KEY_DURATION),
            position = obj.optInt(KEY_POSITION),
            lastPlayed = obj.optLong(KEY_LAST_PLAYED),
            size = obj.optLong(KEY_SIZE),
            fileName = obj.getString(KEY_FILE_NAME),
            status = DownloadStatus.entries.find { it.name == obj.optString(KEY_STATUS) } ?: DownloadStatus.FAILED,
            phoneState = PhoneState.entries.find { it.name == obj.optString(KEY_PHONE_STATE) } ?: PhoneState.UNPLAYED,
            failedDownloads = obj.optInt(KEY_FAILED_DOWNLOADS),
            feedPlaybackSpeed = obj.optDouble(KEY_FEED_PLAYBACK_SPEED, FeedPreferences.SPEED_USE_GLOBAL.toDouble())
                .toFloat(),
            chapters = obj.optJSONArray(KEY_CHAPTERS)?.let { array ->
                (0 until array.length()).map {
                    val chapter = array.getJSONObject(it)
                    Chapter(chapter.optLong(KEY_START), chapter.optString(KEY_TITLE), null, null)
                }
            }
        )
    }
}
