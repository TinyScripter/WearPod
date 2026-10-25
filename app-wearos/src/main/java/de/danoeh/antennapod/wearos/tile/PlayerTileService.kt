package de.danoeh.antennapod.wearos.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.CardDefaults.filledCardColors
import androidx.wear.protolayout.material3.CardDefaults.imageBackgroundCardColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.backgroundImage
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconEdgeButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.ui.common.R as CommonR
import de.danoeh.antennapod.wearos.TileEntryActivity
import de.danoeh.antennapod.wearos.download.DownloadStatus
import de.danoeh.antennapod.wearos.download.PhoneState
import de.danoeh.antennapod.wearos.download.WatchEpisode
import de.danoeh.antennapod.wearos.download.WatchEpisodeStore
import de.danoeh.antennapod.wearos.images.WatchImageLoader
import de.danoeh.antennapod.wearos.playback.WatchPlaybackService
import de.danoeh.antennapod.wearos.settings.WatchPreferences
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tile showing the episode that was played last on the watch.
 */
class PlayerTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            val episode = lastEpisode(this@PlayerTileService)
            val image = episode?.let { loadImage(it) }
            val layout = materialScope(this@PlayerTileService, requestParams.deviceConfiguration) {
                tileLayout(episode, image != null)
            }
            TileBuilders.Tile.Builder()
                .setResourcesVersion("${episode?.itemId}-${image != null}")
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = serviceScope.future {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
            .addIdToImageMapping(
                PLAY_ICON_ID,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(CommonR.drawable.ic_play_48dp_black)
                            .build()
                    )
                    .build()
            )
        val image = lastEpisode(this@PlayerTileService)?.let { loadImage(it) }
        if (image != null) {
            resources.addIdToImageMapping(IMAGE_ID, toImageResource(image))
        }
        resources.build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun MaterialScope.tileLayout(episode: WatchEpisode?, hasImage: Boolean): LayoutElement {
        if (episode == null) {
            return primaryLayout(mainSlot = { text(getString(CommonR.string.wearos_no_items).layoutString) })
        }
        val background: (MaterialScope.() -> LayoutElement)? = if (hasImage) {
            { backgroundImage(IMAGE_ID) }
        } else {
            null
        }
        return primaryLayout(
            titleSlot = { text(episode.feedTitle.layoutString) },
            mainSlot = {
                titleCard(
                    onClick = entryClickable(episode, false),
                    title = { text(episode.title.layoutString, maxLines = 2) },
                    height = expand(),
                    colors = if (hasImage) imageBackgroundCardColors() else filledCardColors(),
                    backgroundContent = background
                )
            },
            bottomSlot = {
                iconEdgeButton(
                    onClick = entryClickable(episode, true),
                    modifier = LayoutModifier.contentDescription(getString(CommonR.string.play_label))
                ) {
                    icon(PLAY_ICON_ID)
                }
            }
        )
    }

    private fun entryClickable(episode: WatchEpisode, startPlayback: Boolean): Clickable = clickable(
        ActionBuilders.launchAction(
            ComponentName(this, TileEntryActivity::class.java),
            mapOf(
                TileEntryActivity.EXTRA_ITEM_ID to ActionBuilders.longExtra(episode.itemId),
                TileEntryActivity.EXTRA_START_PLAYBACK to ActionBuilders.booleanExtra(startPlayback)
            )
        ),
        if (startPlayback) CLICK_ID_PLAY else CLICK_ID_OPEN
    )

    private suspend fun loadImage(episode: WatchEpisode): Bitmap? {
        if (!WatchPreferences.get(this).showImages) {
            return null
        }
        return withTimeoutOrNull(IMAGE_TIMEOUT) {
            WatchImageLoader.load(this@PlayerTileService, WearDataPaths.itemImagePath(episode.itemId))
        }?.asAndroidBitmap()
    }

    private fun toImageResource(image: Bitmap): ResourceBuilders.ImageResource {
        val bitmap = image.copy(Bitmap.Config.RGB_565, false)
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        return ResourceBuilders.ImageResource.Builder()
            .setInlineResource(
                ResourceBuilders.InlineImageResource.Builder()
                    .setData(buffer.array())
                    .setWidthPx(bitmap.width)
                    .setHeightPx(bitmap.height)
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
                    .build()
            )
            .build()
    }

    companion object {
        private const val PLAY_ICON_ID = "play_icon"
        private const val IMAGE_ID = "episode_image"
        private const val CLICK_ID_PLAY = "play"
        private const val CLICK_ID_OPEN = "open"
        private val IMAGE_TIMEOUT = 3.seconds

        @Volatile
        private var lastItemId: Long? = null

        private fun lastEpisode(context: Context): WatchEpisode? {
            WatchEpisodeStore.load(context)
            return lastEpisode(WatchEpisodeStore.episodes.value)
        }

        private fun lastEpisode(episodes: List<WatchEpisode>): WatchEpisode? = episodes.filter {
            it.status == DownloadStatus.DOWNLOADED &&
                (it.phoneState == PhoneState.UNPLAYED || it.itemId == WatchPlaybackService.currentItemId)
        }.maxByOrNull { it.lastPlayed }

        fun onEpisodesChanged(context: Context, episodes: List<WatchEpisode>) {
            val itemId = lastEpisode(episodes)?.itemId
            if (itemId != lastItemId) {
                lastItemId = itemId
                getUpdater(context).requestUpdate(PlayerTileService::class.java)
            }
        }
    }
}
