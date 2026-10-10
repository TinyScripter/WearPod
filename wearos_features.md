# WearOS-Erweiterung: Einstellungen, Download-Modi, Geschwindigkeit, Kapitel, Grafiken

Stand: 2026-10-10 · Branch `wearos-support-features-dev` · Basis `wearos-support-features` (`feac231`)

Dieses Dokument hält fest, was in diesem Schritt geändert wurde, warum, und was beim Weiterentwickeln zu beachten ist.
Grundlagen: `wearos_concept.md` (Gesamtkonzept), `app-wearos/README.md` und `net/sync/wear-interface/README.md` (technische Kurzbeschreibung).

## 1. Anforderungen und Entscheidungen

Vorgaben des Nutzers, auf denen die Umsetzung beruht:

| # | Frage | Entscheidung |
|---|---|---|
| 1 | Wo ist der Button für die Einstellungen? | Auf der Uhr, im Hauptmenü direkt unter „On watch“. |
| 2 | Was heißt „Nur Bluetooth (vom Smartphone)“? | Das Phone schickt die Datei, die es selbst schon heruntergeladen hat. Ist die Episode nicht auf dem Phone, geht es nicht. |
| 3 | Wie verhält sich die Geschwindigkeit? | Wie in der Phone-App und mit dem Phone synchronisiert. In den Einstellungen gibt es nur den Schalter, ob der Geschwindigkeits-Button im Player angezeigt wird. |
| 4 | Wo werden Grafiken angezeigt? | Überall, aber nicht im Player. |
| 5 | LTE? | Eigene Option, nur nachrangig nach WLAN („LTE, falls kein WLAN verfügbar“). Nur auf Uhren mit Mobilfunk sichtbar. |
| 6 | Kapitelwahl? | Wie bei der Geschwindigkeit: Der Schalter in den Einstellungen bestimmt, ob es im Player eine Kapitelwahl gibt. |

Eigene Festlegungen (vom Nutzer noch zu bestätigen):

- **LTE folgt immer direkt auf WLAN**, auch bei „WLAN bevorzugen“ vor dem Phone (siehe Tabelle in 3.2).
- **Standardwerte:** Download „Nur WLAN“ (bisheriges Verhalten), mobile Daten aus, Grafiken an, Geschwindigkeit an, Kapitel an.
- Die Einstellungen liegen **nur auf der Uhr** und gelten auch ohne Phone.

## 2. Überblick der Änderungen

35 Dateien, +1473 / −62 Zeilen. Keine neue Abhängigkeit, weder auf dem Phone noch auf der Uhr.

| Bereich | Neue Dateien | Geänderte Dateien |
|---|---|---|
| Einstellungen (Uhr) | `settings/WatchPreferences.kt`, `WatchSettingsActivity.kt` | `MainListActivity.kt`, `AndroidManifest.xml` |
| Download-Modi | `download/PhoneFileReceiver.kt` (Uhr), `WearFileTransferWorker.java` (Phone) | `EpisodeDownloadWorker.kt`, `WatchEpisode.kt`, `WatchListActivity.kt`, `EpisodeDetailActivity.kt`, `WearListenerService.java` |
| Geschwindigkeit | `sync/PlaybackSpeedReporter.kt` | `WatchPlaybackService.kt`, `WatchSettingsStore.kt`, `WatchPlayerActivity.kt`, `WatchPlayerViewModel.kt`, `WearDataListenerService.kt`, `WearSettings.java`, `WearSerializer.java`, `app/src/play/AndroidManifest.xml` |
| Kapitel | `sync/ChapterSync.kt` | `WatchEpisode.kt`, `WearDataRepository.kt`, `EpisodeDetailViewModel.kt`, Player |
| Grafiken | `images/WatchImageLoader.kt` (Uhr), `WearImageLoader.java` (Phone) | `ListItem.kt`, alle Listen, `EpisodeDetailActivity.kt` |
| Protokoll | – | `WearDataPaths.java`, `WearSerializer.java`, `WearSettings.java` |
| Tests | `EpisodeDownloadWorkerTest.kt` | `WearSerializerTest.java`, `WatchEpisodeStoreTest.kt` |
| Doku | dieses Dokument | `app-wearos/README.md`, `net/sync/wear-interface/README.md` |
| Texte | – | `ui/i18n/src/main/res/values/strings_wearos_standalone.xml` (12 neue Strings, nur Englisch) |

Pfade der Uhr relativ zu `app-wearos/src/main/java/de/danoeh/antennapod/wearos/`, Pfade des Phones relativ zu `app/src/play/java/de/danoeh/antennapod/`.

## 3. Umsetzung im Detail

### 3.1 Einstellungen

- `WatchPreferences` (Uhr): `SharedPreferences` „watch_preferences“ plus `StateFlow`, damit Player und Listen live reagieren. Felder: `downloadMode`, `useMobileData`, `showImages`, `showPlaybackSpeed`, `showChapters`.
- `hasMobileData()` prüft `PackageManager.FEATURE_TELEPHONY`. `canUseMobileData()` = Mobilfunk vorhanden **und** Schalter an.
- `WatchSettingsActivity`: Wear Compose Material3 1.6 (`RadioButton` für den Download-Modus, `SwitchButton` für die Schalter). Abschnitte: Downloads, Menu display, Playback.
- `WatchSettingsStore` bleibt der Spiegel der **Phone**-Einstellungen. Die beiden Speicher nicht vermischen.

### 3.2 Download-Modi

`EpisodeDownloadWorker.transports(mode, useMobileData)` legt die Reihenfolge fest:

| Modus | Reihenfolge |
|---|---|
| `WIFI_ONLY` | WLAN → (LTE) |
| `BLUETOOTH_ONLY` | Phone |
| `PREFER_WIFI` | WLAN → (LTE) → Phone |
| `PREFER_BLUETOOTH` | Phone → WLAN → (LTE) |

Ablauf im Worker:

- Jeder Weg wird nacheinander versucht. `null` bedeutet „nicht verfügbar“, dann kommt der nächste Weg.
- **WLAN und LTE** fordern per `ConnectivityManager.requestNetwork` ein Netz an, `TRANSPORT_WIFI` bzw. `TRANSPORT_CELLULAR`, mit 30 s Timeout. Das Netz wird nach dem Download wieder freigegeben.
- **Ohne WLAN** greift weiter der alte Fallback `downloadWithoutWifi`: Ist das aktive Netz nicht getaktet, oder handelt es sich um einen Debug-Build, wird über das aktive Netz geladen.
- **Ein `IOException`** auf einem Weg lässt den nächsten Weg probieren. Am Ende zählt `retryOrFail` als Fehlversuch.
- **Ist kein Weg verfügbar,** wird `Result.retry()` ohne Fehlerzähler zurückgegeben. Der Status ist dann `WAITING_FOR_WIFI`, `WAITING_FOR_PHONE` oder `WAITING_FOR_CONNECTION`, je nach Modus.
- **`NotOnPhoneException` bei `BLUETOOTH_ONLY`** führt zu Status `NOT_ON_PHONE` und `Result.failure()`. In den „bevorzugen“-Modi wartet der Worker stattdessen.
- `DownloadStatus.isFailed` umfasst `FAILED` und `NOT_ON_PHONE`. In beiden Fällen bieten Liste und Detailseite „Retry“ bzw. erneutes Herunterladen an.

Übertragung vom Phone (Protokoll in 4):

1. Die Uhr (`PhoneFileReceiver`) sucht den erreichbaren Phone-Knoten mit der Capability `wear_capability_phone_standalone`.
2. Sie registriert einen `ChannelClient.ChannelCallback` und schickt dann die Message `/episode_file/<id>`. Die Payload ist der Offset, also die Länge der schon vorhandenen Teildatei.
3. Das Phone (`WearListenerService`) startet `WearFileTransferWorker`: expedited mit `RUN_AS_NON_EXPEDITED_WORK_REQUEST`, Unique-Work `wear_file_transfer_<id>`, `REPLACE`.
4. Der Worker öffnet einen Channel auf demselben Pfad und schreibt drei Dinge: `long` Dateigröße, `long` Start-Offset, dann die Bytes.
   - Dateigröße `-1` heißt: Die Episode ist nicht heruntergeladen.
   - Ist der Offset größer als die Datei, beginnt der Worker bei 0. Die Uhr überschreibt dann ihre Teildatei.
5. Die Uhr wartet höchstens 30 s auf den Channel. Danach prüft sie den Speicherplatz, schreibt die Datei mit Fortschrittsanzeige und kontrolliert am Ende die Größe.

Die Datei auf dem Phone wird über `ContentResolver.openAssetFileDescriptor` geöffnet. Das funktioniert für `file://` und für `content://`, also auch für lokale Feeds.

### 3.3 Wiedergabegeschwindigkeit

Gleiches Verhalten wie `PlaybackSpeedUtils.getCurrentPlaybackSpeed` auf dem Phone. Reihenfolge in `WatchPlaybackService.applyPlaybackSpeed()`:

1. **Temporäre Geschwindigkeit der aktuellen Episode** aus `WatchSettingsStore.getTemporarySpeed(itemId)`. Das entspricht `PlaybackPreferences.currentlyPlayingTemporaryPlaybackSpeed` auf dem Phone.
2. **Geschwindigkeit des Podcasts** aus `WatchEpisode.feedPlaybackSpeed`. Sie kommt aus den Episode-Details; `-1` steht für `SPEED_USE_GLOBAL`.
3. **Globale Geschwindigkeit** aus den Phone-Einstellungen, gegebenenfalls ersetzt durch eine noch nicht bestätigte Änderung auf der Uhr.

Ändern auf der Uhr (wie im `VariableSpeedDialog` des Phones):

- `ForwardingPlayer.setPlaybackSpeed` in der Media Session setzt die Geschwindigkeit, speichert sie als temporär für die Episode und als „pending“ global.
- Danach schreibt die Uhr das Data Item `/watch_playback_speed`.
- Das Phone setzt `UserPreferences.setPlaybackSpeed` und löscht das Data Item. Ein laufender Player auf dem Phone wird **nicht** geändert.
- `WatchSettingsStore.save` löscht „pending“ erst, wenn das Phone dieselbe Geschwindigkeit meldet. So überschreibt eine veraltete `/settings`-Antwort die Änderung auf der Uhr nicht.
- Auswahl: Liste der Geschwindigkeits-Stufen vom Phone (`UserPreferences.getPlaybackSpeedArray`, neu in `/settings`). Standard ist 1.0, 1.25, 1.5. Einen Schieberegler gibt es auf der Uhr nicht.

### 3.4 Kapitel

- **Phone:** `/chapters/<id>` ruft `ChapterUtils.loadChapters(media, context, false)` auf. Das führt Kapitel aus DB, Mediendatei und Podcast-Index zusammen und lädt bei Bedarf übers Netz. Gesendet werden nur Start und Titel, höchstens 500 Kapitel.
- **Uhr:** `ChapterSync` fragt nach dem Herunterladen auf die Uhr (`EpisodeDetailViewModel`) und speichert in `WatchEpisode.chapters`.
  - `null` heißt „noch nicht geholt“, eine leere Liste heißt „keine Kapitel“.
  - Fehlen die Kapitel, holt der Player sie einmal pro ViewModel nach.
- **Player:** Ein Button öffnet einen `AlertDialog` mit den Kapiteln. Das aktuelle Kapitel ist per `Chapter.getAfterPosition` mit einem Play-Icon markiert. Tippen führt `MediaController.seekTo(start)` aus.

### 3.5 Grafiken

- **Phone (`WearImageLoader`):** Glide (`ApGlideModule`, auch für eingebettete Cover und lokale Feeds) erzeugt ein 96×96-Bild mit center crop und JPEG-Qualität 85. Das sind wenige KB, weit unter dem 100-KB-Limit für Messages.
  - Bei Episoden wird erst `item.getImageLocation()` versucht, dann das Bild des Podcasts.
  - Eine leere Antwort heißt „kein Bild“.
- **Uhr (`WatchImageLoader`):**
  - `LruCache` mit 100 Einträgen im Speicher, Dateien unter `files/images/`, nach 7 Tagen erneuern.
  - Höchstens 3 Anfragen parallel, Timeout 10 s.
  - „Kein Bild“ merkt sich der Loader pro Prozess und fragt dann nicht erneut.
  - Ohne Verbindung wird ein veraltetes Bild weiter angezeigt.
- **Anzeige:** `rememberWatchImage(path)` liefert `null`, wenn die Einstellung aus ist.
  - `ListItem` zeigt das Bild als 32-dp-Kreis.
  - Detailseite: 48 dp.
  - Im Player ist absichtlich kein Bild.
- **Wo Bilder erscheinen:** Abos, Episodenlisten, „On watch“, „Now playing“ im Hauptmenü, Episodendetails.

## 4. Protokoll (`:net:sync:wear-interface`)

Alles ist nur ergänzt; alte Gegenstellen ignorieren unbekannte Pfade.

| Pfad | Art | Richtung | Inhalt |
|---|---|---|---|
| `/episode_file/<id>` | Message + Channel | Uhr → Phone, Channel Phone → Uhr | Payload: Offset (Text). Channel: Größe, Start, Bytes. |
| `/chapters/<id>` | Message | Uhr ↔ Phone | JSON-Array `{start, title}` |
| `/image/feed/<id>`, `/image/item/<id>` | Message | Uhr ↔ Phone | JPEG-Bytes oder leer |
| `/watch_playback_speed` | Data Item | Uhr → Phone | JSON `{playback_speed, timestamp}` |
| `/settings` | Message | (erweitert) | zusätzlich `playback_speeds` |
| `/episode_details/<id>` | Message | (erweitert) | zusätzlich `feed_playback_speed` |

- Neue Methoden in `WearSerializer`: `chaptersToBytes`/`chaptersFromBytes`, `playbackSpeedToBytes`/`playbackSpeedFromBytes`.
- Der zusätzliche Konstruktor von `WearSettings` hat die Stufen als 5. Parameter. Der alte 4-Parameter-Konstruktor bleibt und nutzt `DEFAULT_PLAYBACK_SPEEDS`.
- Im Phone-Manifest gibt es einen zweiten `DATA_CHANGED`-Intent-Filter mit `android:path="/watch_playback_speed"`.
- Eine neue Capability-Version gibt es **nicht**. Kann ein altes Phone die neuen Pfade nicht, läuft die Uhr in einen Timeout und zeigt dann eben keine Bilder oder Kapitel an bzw. wartet beim Download.

## 5. Bekannte Grenzen und offene Punkte

1. **Nicht auf Geräten getestet.** Bluetooth-Übertragung, LTE, Speed-Sync und Bilder sind nur kompiliert und per Unit-Test abgedeckt. Testfälle siehe 6.
2. **Phone-Übertragung ohne Foreground-Service:** Normale bzw. expedited Jobs können nach ca. 10 Minuten beendet werden. Die Uhr setzt beim nächsten Versuch am Offset fort; sehr große Dateien brauchen eventuell mehrere Anläufe. Ein Foreground-Service aus dem Hintergrund ist ab Android 12 eingeschränkt.
3. **Debug-Builds:** Wegen des alten Fallbacks (aktives Netz im Debug-Build) wird bei „WLAN bevorzugen“ nie das Phone erreicht. Zum Testen der Übertragung „Nur Bluetooth“ oder „Bluetooth bevorzugen“ verwenden.
4. **Deutsche Texte fehlen:** Die 12 neuen Strings stehen nur in `values/strings_wearos_standalone.xml`. `AGENTS.md` verbietet Änderungen an `values-*`. Der Fork pflegt aber `values-de/strings_wearos_standalone.xml`, das muss der Nutzer entscheiden.
5. **Netzwerk auf dem Computation-Scheduler:** `/chapters` und `/image/...` laufen im `WearListenerService` auf `Schedulers.computation()` (bestehendes Muster) und können dort ein paar Sekunden blockieren.
6. **`WearMessageSender`** und die Bilder-Anfragen nutzen den ersten verbundenen Knoten, nicht den mit Capability. Das ist bekannt aus dem Review (Punkt 3.10).
7. **Bilder-Cache:** Es gibt keine Größenbegrenzung auf der Disk; bei ca. 5 KB pro Bild unkritisch. Bilder entfernter Episoden bleiben liegen.
8. **Geschwindigkeit:** Nur Auswahl aus den Stufen, kein Feinregler. „Skip silence“ wird nicht übernommen.
9. **Konflikt bei Geschwindigkeit:** Ändert der Nutzer die globale Geschwindigkeit am Phone, bevor das Data Item der Uhr angekommen ist, gewinnt die Uhr („last delivered wins“).

## 6. Testfälle für Geräte

1. Einstellungen öffnen: Der Eintrag steht unter „On watch“. Der LTE-Schalter erscheint nur auf einer LTE-Uhr. Werte bleiben nach einem Neustart erhalten.
2. „Nur Bluetooth“ mit einer auf dem Phone heruntergeladenen Episode: Fortschritt läuft, danach ist sie abspielbar.
3. „Nur Bluetooth“ mit einer nicht heruntergeladenen Episode: Status „Not downloaded on phone“, „Retry“ möglich.
4. „Nur Bluetooth“, Phone aus: „Waiting for phone“. Phone wieder an: Der Download startet beim nächsten Versuch (Backoff 1 min linear).
5. Übertragung mittendrin abbrechen (BT aus): Der nächste Versuch setzt fort und lädt nicht von vorn.
6. „Bluetooth bevorzugen“, nicht auf dem Phone, WLAN vorhanden: Download über WLAN.
7. „Nur WLAN“ mit LTE an, WLAN aus: Download über LTE.
8. Geschwindigkeit am Player ändern: Gilt sofort. Danach am Phone in den Einstellungen bzw. im Dialog prüfen. Mit Phone offline ändern, dann verbinden: Das Phone übernimmt.
9. Podcast mit eigener Geschwindigkeit: Startet mit der Podcast-Geschwindigkeit. Eine Änderung gilt für diese Episode und wird global.
10. Kapitel: Eine Episode mit Kapiteln herunterladen, im Player den Button antippen, ein Kapitel wählen, die Position springt. Schalter aus: Kein Button.
11. Grafiken: Abo-Liste, Episodenliste, „On watch“, Details zeigen Bilder; im Player keins. Schalter aus: nur Text. Offline: „On watch“ zeigt die zwischengespeicherten Bilder.

## 7. Bauen und Testen

Befehle laut `AGENTS.md`:

- Phone: `./gradlew :app:assembleDebug`
- Uhr: `./gradlew :app-wearos:assembleDebug` (`:app:assembleDebug` baut die Uhr nicht mit)
- Tests: `./gradlew --console=plain :net:sync:wear-interface:test :app-wearos:test`
- Style: `./gradlew checkstyle lint` (enthält ktlint für `app-wearos` und SpotBugs)

Ergebnis am 2026-10-10: beide Builds erfolgreich, alle Unit-Tests grün, `checkstyle lint` ohne Befund.

Hinweise für die Cloud-Umgebung (nicht Teil des Repositorys):

- **Android SDK:** Es ist nicht vorinstalliert. Installiert wurden cmdline-tools, `platforms;android-36`, `build-tools;36.0.0` und `platform-tools` unter `/opt/android-sdk`; `local.properties` setzt `sdk.dir` und wird von git ignoriert.
- **Maven Central drosselt mit HTTP 429.** Abhilfe:
  - für Gradle das Init-Skript `~/.gradle/init.d/mirror.gradle`, das `https://maven-central.storage-download.googleapis.com/maven2/` vor die anderen Repositories setzt;
  - für Robolectric die Property `-Drobolectric.dependency.repo.url=https://maven-central.storage-download.googleapis.com/maven2` über `JAVA_TOOL_OPTIONS`.

## 8. Stand von Branch und Push

- Commit `8d57966` „Add settings, download modes, playback speed, chapters and images to the WearOS app“ auf `wearos-support-features-dev`.
- **Der Push ist fehlgeschlagen (HTTP 403).** Laut Nutzer ist sein GitHub-Konto gesperrt (Grund und Zeitpunkt unklar); zu Beginn der Sitzung funktionierte `git fetch` noch. Der Commit wurde dem Nutzer als Patch übergeben (`git am` auf `wearos-support-features`).
- Nach Freigabe des Kontos: `git push -u origin wearos-support-features-dev`.
