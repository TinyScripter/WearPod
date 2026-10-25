package de.danoeh.antennapod.wearos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModelProvider
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.ui.common.R as CommonR
import de.danoeh.antennapod.wearos.composable.ListItem
import de.danoeh.antennapod.wearos.composable.ListScaffold
import de.danoeh.antennapod.wearos.download.DownloadStatus
import de.danoeh.antennapod.wearos.download.PhoneState
import de.danoeh.antennapod.wearos.download.WatchEpisode
import de.danoeh.antennapod.wearos.images.rememberWatchImage

class WatchListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[WatchListViewModel::class.java]

        setContent {
            MaterialTheme(colorScheme = dynamicColorScheme(this) ?: MaterialTheme.colorScheme) {
                AppScaffold {
                    val uiState by viewModel.uiState.collectAsState()
                    WatchListScreen(
                        uiState = uiState,
                        onPlay = { itemId ->
                            val intent = Intent(this@WatchListActivity, WatchPlayerActivity::class.java).apply {
                                putExtra(WatchPlayerActivity.EXTRA_ITEM_ID, itemId)
                            }
                            startActivity(intent)
                        },
                        onRetry = { viewModel.retry(it) },
                        onMove = { itemId, newIndex -> viewModel.move(itemId, newIndex) },
                        onRemove = { viewModel.remove(it) },
                        onRemovePlayed = { viewModel.removePlayed() }
                    )
                }
            }
        }
    }
}

@Composable
fun downloadStatusText(episode: WatchEpisode, progress: Int): String? = when (episode.status) {
    DownloadStatus.QUEUED -> stringResource(CommonR.string.download_pending)
    DownloadStatus.WAITING_FOR_WIFI -> stringResource(CommonR.string.wearos_waiting_for_wifi)
    DownloadStatus.WAITING_FOR_PHONE -> stringResource(CommonR.string.wearos_waiting_for_phone)
    DownloadStatus.WAITING_FOR_CONNECTION -> stringResource(CommonR.string.wearos_waiting_for_connection)
    DownloadStatus.DOWNLOADING -> stringResource(CommonR.string.wearos_downloading, progress)
    DownloadStatus.FAILED -> stringResource(CommonR.string.wearos_download_failed)
    DownloadStatus.NOT_ON_PHONE -> stringResource(CommonR.string.wearos_not_downloaded_on_phone)
    DownloadStatus.DOWNLOADED -> null
}

@Composable
fun WatchListScreen(
    uiState: WatchListUiState,
    onPlay: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onRemovePlayed: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()
    var menuEpisode by remember { mutableStateOf<WatchEpisode?>(null) }
    var menuVisible by remember { mutableStateOf(false) }

    ListScaffold(
        titleRes = CommonR.string.wearos_on_watch,
        scrollState = scrollState,
        isPhoneSupported = true,
        isTimedOut = false,
        isLoading = false,
        isEmpty = uiState.episodes.isEmpty()
    ) {
        items(uiState.episodes) { episode ->
            val openMenu = {
                menuEpisode = episode
                menuVisible = true
            }
            val phoneStateText = when (episode.phoneState) {
                PhoneState.PLAYED -> stringResource(CommonR.string.is_played)
                PhoneState.DELETED -> stringResource(CommonR.string.wearos_deleted_on_phone)
                PhoneState.UNPLAYED -> null
            }
            ListItem(
                text = episode.title,
                secondaryText = downloadStatusText(episode, uiState.progress[episode.itemId] ?: 0)
                    ?: phoneStateText
                    ?: episode.feedTitle.ifEmpty { null },
                onLongClick = openMenu,
                image = rememberWatchImage(WearDataPaths.itemImagePath(episode.itemId)),
                onClick = {
                    if (episode.status == DownloadStatus.DOWNLOADED) {
                        onPlay(episode.itemId)
                    } else {
                        openMenu()
                    }
                }
            )
        }
        if (uiState.episodes.any { it.phoneState != PhoneState.UNPLAYED }) {
            item {
                ListItem(
                    text = stringResource(CommonR.string.delete_downloads_played),
                    onClick = onRemovePlayed
                )
            }
        }
    }

    AlertDialog(
        visible = menuVisible,
        onDismissRequest = { menuVisible = false },
        title = { Text(text = menuEpisode?.title ?: "", maxLines = 3, overflow = TextOverflow.Ellipsis) }
    ) {
        val episode = menuEpisode ?: return@AlertDialog
        if (episode.status.isFailed) {
            item {
                ListItem(
                    text = stringResource(CommonR.string.retry_label),
                    onClick = {
                        onRetry(episode.itemId)
                        menuVisible = false
                    }
                )
            }
        }
        val index = uiState.episodes.indexOfFirst { it.itemId == episode.itemId }
        if (index > 0) {
            item {
                ListItem(
                    text = stringResource(CommonR.string.wearos_move_up),
                    onClick = {
                        onMove(episode.itemId, index - 1)
                        menuVisible = false
                    }
                )
            }
        }
        if (index in 0 until uiState.episodes.lastIndex) {
            item {
                ListItem(
                    text = stringResource(CommonR.string.wearos_move_down),
                    onClick = {
                        onMove(episode.itemId, index + 1)
                        menuVisible = false
                    }
                )
            }
        }
        if (index > 0) {
            item {
                ListItem(
                    text = stringResource(CommonR.string.move_to_top_label),
                    onClick = {
                        onMove(episode.itemId, 0)
                        menuVisible = false
                    }
                )
            }
        }
        item {
            ListItem(
                text = stringResource(CommonR.string.wearos_remove_from_watch),
                onClick = {
                    onRemove(episode.itemId)
                    menuVisible = false
                }
            )
        }
    }
}
