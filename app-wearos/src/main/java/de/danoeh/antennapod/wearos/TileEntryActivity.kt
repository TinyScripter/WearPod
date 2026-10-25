package de.danoeh.antennapod.wearos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import de.danoeh.antennapod.wearos.download.DownloadStatus
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore

/**
 * Exported entry point for the tile. Only forwards episodes that are downloaded on the watch,
 * so that [WatchPlayerActivity] does not need to be exported.
 */
class TileEntryActivity : Activity() {
    @SuppressLint("WearRecents")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, WatchPlayerViewModel.NO_ITEM)
        val episode = WatchEpisodeStore.get(this, itemId)?.takeIf { it.status == DownloadStatus.DOWNLOADED }
        val playerIntent = Intent(this, WatchPlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (episode != null) {
            playerIntent.putExtra(WatchPlayerActivity.EXTRA_ITEM_ID, episode.itemId)
            playerIntent.putExtra(
                WatchPlayerActivity.EXTRA_START_PLAYBACK,
                intent.getBooleanExtra(EXTRA_START_PLAYBACK, false)
            )
        }
        startActivity(playerIntent)
        finish()
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_START_PLAYBACK = "start_playback"
    }
}
