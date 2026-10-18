# :app-wearos

A companion Wear OS app for AntennaPod. Can browse episodes from the phone and remote-control the phone to start playback.
Communicates with the phone via the WearOS DataLayer API using paths and serialization defined in `:net:sync:wear-interface`.

## Communication with Phone

`WearDataListenerService` extends `WearableListenerService` and receives all incoming messages from the phone.
Most requests are watch-initiated requests (polling regularly), rarely the phone pushes events.

`WearDataListenerService` decodes received payloads via `WearSerializer`.
`WearDataRepository` is a singleton holding `StateFlow`s for each data type.
Composables and ViewModels collect from these flows to stay up to date.
Updated exclusively by `WearDataListenerService`.

`WearMessageSender.send(context, path)` sends a message to the first connected phone.
Use this to trigger actions (playback control) or to request data (the phone then replies with the data on the same path).

## Episodes on the Watch

`WatchEpisodeStore` is a singleton holding the episodes that were selected for playback on the watch.
The list is persisted as a JSON file in the app's files directory, so it is available without the phone.
`EpisodeDownloadWorker` gets the media file of one episode to `files/episodes`.
The download mode in the settings of the watch (`WatchPreferences`) defines which ways are tried, in this order:

| Mode | Order |
|---|---|
| Wi-Fi only | Wi-Fi |
| Bluetooth only (from phone) | Phone |
| Prefer Wi-Fi | Wi-Fi, phone |
| Prefer Bluetooth | Phone, Wi-Fi |

Wi-Fi downloads the file directly from its original URL. The worker requests a Wi-Fi network and refuses to download over metered networks (except in debug builds, as the emulator only has a metered network).
If the watch has mobile data and it is enabled in the settings, mobile data is tried directly after Wi-Fi.
"Phone" means that `PhoneFileReceiver` asks the phone for the file it already downloaded. The phone (`WearFileTransferWorker`) sends it over a `ChannelClient` channel, which works for podcasts with a password, too.
If the episode is not downloaded on the phone, it cannot be transferred. With "Bluetooth only" the download then fails with "Not downloaded on phone".
If no way is available, the episode is marked as waiting (for Wi-Fi, for the phone or for a connection) and the worker retries with a linear backoff, without ever giving up.
The worker runs as a foreground service with a notification, so long downloads are not stopped after 10 minutes.
This only works if the system allows starting a foreground service at that moment (usually when the download was started from the app), otherwise it downloads without a notification.
Interrupted downloads continue where they stopped using HTTP range requests, or by telling the phone the offset to continue at.
Only real download errors count as failed attempts (`WatchEpisode.failedDownloads`), the download fails after the third retry.
Episodes are limited to 1 GB in total, and at least 100 MB of the storage of the watch stay free.
The free space is checked before the download using the size from the phone and again using the `Content-Length` of the server.

## Playback on the Watch

`WatchPlaybackService` is a Media3 `MediaSessionService` that plays the downloaded episodes without the phone.
The playlist consists of the downloaded episodes in the order of `WatchEpisodeStore`, the media id of each item is the episode id.
The service observes `WatchEpisodeStore` and updates the playlist around the current episode when episodes are moved, added or removed.
The service stores the playback position in `WatchEpisodeStore` and removes an episode from the watch when it has been played completely.
Skipping to the next episode within the "smart mark as played" time before the end also counts as played completely.
Skip amounts, playback speed and the smart mark as played time are taken from the settings last received from the phone, persisted in `WatchSettingsStore`.
New settings from the phone are applied to the running service immediately. The media session uses a `ForwardingPlayer` for this, because the skip amounts of ExoPlayer cannot be changed after it was built.
`WatchPlayerViewModel` connects to the service using a `MediaController` and only polls the playback position while the player screen is visible.

The playback speed is chosen like on the phone: a speed changed for the current episode, otherwise the speed of the podcast, otherwise the global speed.
Changing the speed on the watch also changes the global speed. It is reported to the phone using the data item `WATCH_PLAYBACK_SPEED` and kept as pending in `WatchSettingsStore` until the phone reports the same speed.
The chapters of an episode are requested from the phone (`ChapterSync`) when it is downloaded to the watch, or later when the player shows an episode without chapters. They are stored with the episode.
The buttons for the playback speed and the chapters are only shown if they are enabled in the settings of the watch.

## Settings and Images

`WatchSettingsActivity` shows the settings that are only made on the watch, stored in `WatchPreferences`: download mode, mobile data (only on watches with mobile data), podcast images, playback speed button and chapter selection.
`WatchImageLoader` requests small podcast and episode images from the phone, caches them in memory and in `files/images` and refreshes them after 7 days.
The images are shown in all lists and on the episode details, but not in the player.

## Synchronizing Playback Positions

The most recent timestamp wins, both on the watch and on the phone.
`PositionReporter` writes the position of an episode into a data item whenever `WatchPlaybackService` stores it.
Data items are delivered to the phone as soon as it is reachable, so this also works when playing without the phone.
`PositionSync` requests the playback state of all episodes on the watch from the phone when the list of episodes is opened and before playback starts.
Episodes that were played or deleted on the phone since the last synchronization are removed from the watch automatically, including their files.
The episode that is currently loaded in `WatchPlaybackService` and the episode that is being started are kept, they are removed on a later synchronization.
Episodes that were already played on the phone when they were downloaded to the watch are not removed automatically. They are marked in the list, are skipped when playing the next episode and can be removed with "Delete played".
