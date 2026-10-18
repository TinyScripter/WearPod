# Upstream-Updates in den WearOS-Mod übernehmen

## Branches

| Branch | Inhalt |
|---|---|
| `develop` | Reiner Spiegel von `upstream/develop`, keine eigenen Commits |
| `wearos-support` | `develop` plus WearOS-Funktion plus Mod (Name, App-IDs, README, Icon) |

Remotes: `upstream` = AntennaPod/AntennaPod, `origin` = eigener Fork.

## Ablauf

1. Upstream holen und `develop` per Fast-Forward aktualisieren:

   ```bash
   git fetch upstream
   git checkout develop
   git merge --ff-only upstream/develop
   git push origin develop
   ```

   Schlägt `--ff-only` fehl, liegen eigene Commits auf `develop`. Die gehören auf `wearos-support`.

2. Neuen Upstream-Stand in den Mod-Branch bringen:

   ```bash
   git checkout wearos-support
   git merge develop
   ```

   Alternative mit linearer Historie (benötigt anschließend einen Force-Push):

   ```bash
   git rebase develop
   git push --force-with-lease origin wearos-support
   ```

3. Konflikte lösen. Typische Stellen:

   - `app/build.gradle`, `common.gradle`: Application-ID, Versionsname und Wear-Abhängigkeiten
   - `app/src/play/AndroidManifest.xml`: Wear-Listener-Service
   - `ui/i18n/src/main/res/values/strings.xml`: neue Wear-Strings
   - `playback/service/.../Media3PlaybackService.java`: Wear-Hooks

   Das Fork-README (`.github/README.md`) und die Fork-Icons (`fork-res/`, eingebunden in `fork.gradle`) sind neue Dateien und verursachen keine Konflikte. Das Root-`README.md` und `ui/common` bleiben auf dem Upstream-Stand.

   Bei Konflikten im Mod-Teil die Mod-Werte behalten, alles andere von Upstream übernehmen.

4. Prüfen, dass der Mod noch erhalten ist:

   - Anwendungsname und Application-ID sind weiterhin die des Mods (`app/build.gradle`, `common.gradle`, `app-wearos/build.gradle`)
   - Die Wear-App nutzt dieselbe Application-ID wie die Phone-App
   - `app-wearos/proguard.cfg` passt zu neuen Abhängigkeiten
   - Hat Upstream eine API geändert, die der Wear-Code nutzt (zum Beispiel `FeedMedia`), den Wear-Code anpassen (`WearPositionApplier`, `WearListenerService`)

5. Bauen und testen:

   ```bash
   ./gradlew :app:assembleDebug
   ./gradlew :app-wearos:assembleDebug
   ./gradlew --console=plain :net:sync:wear-interface:test
   ./gradlew --console=plain :app:testPlayDebugUnitTest
   ```

6. Veröffentlichen:

   ```bash
   git push origin wearos-support
   ```

## Neue Upstream-Release-Tags

Neue Tags (zum Beispiel `3.12.3`) kommen mit `git fetch upstream`. Für einen Release-Stand des Mods einen eigenen Branch oder Tag von `wearos-support` anlegen, etwa `wearos-mod-3.12.3`.
