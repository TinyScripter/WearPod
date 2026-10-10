package de.danoeh.antennapod.wearos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import de.danoeh.antennapod.ui.common.R as CommonR
import de.danoeh.antennapod.wearos.settings.DownloadMode
import de.danoeh.antennapod.wearos.settings.WatchPreferenceValues
import de.danoeh.antennapod.wearos.settings.WatchPreferences

class WatchSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hasMobileData = WatchPreferences.hasMobileData(this)

        setContent {
            MaterialTheme(colorScheme = dynamicColorScheme(this) ?: MaterialTheme.colorScheme) {
                val values by WatchPreferences.observe(this).collectAsState()
                WatchSettingsScreen(
                    values = values,
                    hasMobileData = hasMobileData,
                    onChange = { transform -> WatchPreferences.update(this, transform) }
                )
            }
        }
    }
}

private fun downloadModeTitleRes(mode: DownloadMode): Int = when (mode) {
    DownloadMode.WIFI_ONLY -> CommonR.string.wearos_download_wifi_only
    DownloadMode.BLUETOOTH_ONLY -> CommonR.string.wearos_download_bluetooth_only
    DownloadMode.PREFER_WIFI -> CommonR.string.wearos_download_prefer_wifi
    DownloadMode.PREFER_BLUETOOTH -> CommonR.string.wearos_download_prefer_bluetooth
}

@Composable
private fun SectionTitle(textRes: Int) {
    Text(
        text = stringResource(textRes),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun SettingSwitch(textRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(textRes)) }
    )
}

@Composable
fun WatchSettingsScreen(
    values: WatchPreferenceValues,
    hasMobileData: Boolean,
    onChange: ((WatchPreferenceValues) -> WatchPreferenceValues) -> Unit
) {
    val scrollState = rememberScalingLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = scrollState
        ) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(CommonR.string.settings_label))
                }
            }

            item { SectionTitle(CommonR.string.downloads_label) }

            items(DownloadMode.entries) { mode ->
                RadioButton(
                    selected = values.downloadMode == mode,
                    onSelect = { onChange { it.copy(downloadMode = mode) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(downloadModeTitleRes(mode))) }
                )
            }

            if (hasMobileData) {
                item {
                    SettingSwitch(CommonR.string.wearos_download_mobile_data, values.useMobileData) { checked ->
                        onChange { it.copy(useMobileData = checked) }
                    }
                }
            }

            item { SectionTitle(CommonR.string.wearos_menu_display) }

            item {
                SettingSwitch(CommonR.string.wearos_show_images, values.showImages) { checked ->
                    onChange { it.copy(showImages = checked) }
                }
            }

            item { SectionTitle(CommonR.string.playback_pref) }

            item {
                SettingSwitch(CommonR.string.playback_speed, values.showPlaybackSpeed) { checked ->
                    onChange { it.copy(showPlaybackSpeed = checked) }
                }
            }

            item {
                SettingSwitch(CommonR.string.wearos_chapter_selection, values.showChapters) { checked ->
                    onChange { it.copy(showChapters = checked) }
                }
            }
        }
        ScrollIndicator(state = scrollState, modifier = Modifier.align(Alignment.CenterEnd))
    }
}
