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
`EpisodeDownloadWorker` downloads the media file of one episode directly from its original URL to `files/episodes`.
It requests a Wi-Fi network first and refuses to download over metered networks (except in debug builds, as the emulator only has a metered network).

## Playback on the Watch

`WatchPlaybackService` is a Media3 `MediaSessionService` that plays the downloaded episodes without the phone.
The playlist consists of the downloaded episodes in the order of `WatchEpisodeStore`, the media id of each item is the episode id.
The service observes `WatchEpisodeStore` and updates the playlist around the current episode when episodes are moved, added or removed.
The service stores the playback position in `WatchEpisodeStore` and removes an episode from the watch when it has been played completely.
Skip amounts and playback speed are taken from the settings last received from the phone, persisted in `WatchSettingsStore`.
`WatchPlayerViewModel` connects to the service using a `MediaController`.

## Synchronizing Playback Positions

The most recent timestamp wins, both on the watch and on the phone.
`PositionReporter` writes the position of an episode into a data item whenever `WatchPlaybackService` stores it.
Data items are delivered to the phone as soon as it is reachable, so this also works when playing without the phone.
`PositionSync` requests the playback state of all episodes on the watch from the phone when the list of episodes is opened and before playback starts.
Episodes that were played or deleted on the phone are marked in the list and are skipped when playing the next episode.
