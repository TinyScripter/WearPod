package de.danoeh.antennapod.wearos.download

import de.danoeh.antennapod.model.feed.FeedItem
import org.json.JSONObject

enum class DownloadStatus { QUEUED, WAITING_FOR_WIFI, DOWNLOADING, DOWNLOADED, FAILED }

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
    val phoneState: PhoneState = PhoneState.UNPLAYED
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
            phoneState = PhoneState.entries.find { it.name == obj.optString(KEY_PHONE_STATE) } ?: PhoneState.UNPLAYED
        )
    }
}
