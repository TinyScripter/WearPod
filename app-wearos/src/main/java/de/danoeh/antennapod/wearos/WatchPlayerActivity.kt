package de.danoeh.antennapod.wearos

import android.media.AudioManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LevelIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.common.R as CommonR
import de.danoeh.antennapod.ui.notifications.R as NotificationsR
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

class WatchPlayerActivity : ComponentActivity() {
    private var rotaryScrollPixels = 0f
    private val volume = mutableStateOf<Float?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val itemId = if (savedInstanceState == null) {
            intent.getLongExtra(EXTRA_ITEM_ID, WatchPlayerViewModel.NO_ITEM)
        } else {
            WatchPlayerViewModel.NO_ITEM
        }
        val viewModel = ViewModelProvider(this, WatchPlayerViewModel.factory(itemId))[WatchPlayerViewModel::class.java]

        setContent {
            MaterialTheme(colorScheme = dynamicColorScheme(this) ?: MaterialTheme.colorScheme) {
                val uiState by viewModel.uiState.collectAsState()
                val currentVolume by volume
                LaunchedEffect(currentVolume) {
                    delay(VOLUME_INDICATOR_DURATION)
                    volume.value = null
                }
                WatchPlayerScreen(
                    uiState = uiState,
                    volume = currentVolume,
                    onPlayPause = { viewModel.playPause() },
                    onSkipForward = { viewModel.skipForward() },
                    onSkipBackward = { viewModel.skipBackward() },
                    onNext = { viewModel.next() }
                )
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_SCROLL ||
            !event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
        ) {
            return super.onGenericMotionEvent(event)
        }
        rotaryScrollPixels -= event.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
            ViewConfigurationCompat.getScaledVerticalScrollFactor(ViewConfiguration.get(this), this)
        val audioManager = getSystemService(AudioManager::class.java)
        while (abs(rotaryScrollPixels) >= VOLUME_STEP_PIXELS) {
            val direction = if (rotaryScrollPixels > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            rotaryScrollPixels -= if (rotaryScrollPixels > 0) VOLUME_STEP_PIXELS else -VOLUME_STEP_PIXELS
            volume.value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }
        return true
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        private const val VOLUME_STEP_PIXELS = 48f
        private val VOLUME_INDICATOR_DURATION = 2.seconds
    }
}

@Composable
fun WatchPlayerScreen(
    uiState: WatchPlayerUiState,
    volume: Float?,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onNext: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
            return@Box
        }
        if (uiState.title.isEmpty()) {
            Text(text = stringResource(CommonR.string.no_media_playing_label), textAlign = TextAlign.Center)
            return@Box
        }
        if (volume != null) {
            LevelIndicator(value = { volume }, modifier = Modifier.align(Alignment.CenterStart))
        } else if (uiState.duration > 0) {
            CircularProgressIndicator(
                progress = { uiState.position.toFloat() / uiState.duration.toFloat() },
                modifier = Modifier.fillMaxSize().padding(2.dp),
                strokeWidth = 4.dp
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = uiState.title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (uiState.hasError) {
                Text(
                    text = stringResource(CommonR.string.wearos_playback_failed),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = Converter.getDurationStringLong(uiState.position) + " / " +
                        Converter.getDurationStringLong(uiState.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = onSkipBackward, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painter = painterResource(NotificationsR.drawable.ic_notification_fast_rewind),
                        contentDescription = stringResource(CommonR.string.rewind_label),
                        modifier = Modifier.size(24.dp)
                    )
                }
                val playIcon = if (uiState.isPlaying) {
                    CommonR.drawable.ic_pause_black
                } else {
                    CommonR.drawable.ic_play_48dp_black
                }
                val playLabel = if (uiState.isPlaying) CommonR.string.pause_label else CommonR.string.play_label
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painter = painterResource(playIcon),
                        contentDescription = stringResource(playLabel),
                        modifier = Modifier.size(24.dp)
                    )
                }
                FilledTonalIconButton(onClick = onSkipForward, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painter = painterResource(NotificationsR.drawable.ic_notification_fast_forward),
                        contentDescription = stringResource(CommonR.string.fast_forward_label),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(36.dp), enabled = uiState.hasNext) {
                Icon(
                    painter = painterResource(NotificationsR.drawable.ic_notification_skip),
                    contentDescription = stringResource(CommonR.string.skip_episode_label),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
