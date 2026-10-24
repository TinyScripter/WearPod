# Upstream-Updates in den WearOS-Mod übernehmen

Kurzfassung. Das Branch-Modell, den Release-Build und das Veröffentlichen beschreibt `FORK_RELEASE.md`.

## Branches

| Branch | Inhalt |
|---|---|
| `develop` | Reiner Spiegel von `upstream/develop`, keine eigenen Commits |
| `wearos-features` | `develop` plus Wear-Funktionen, kein Branding |
| `wearos-support` | `wearos-features` plus Branding (Name, App-IDs, Icons in `fork-res/`, `.github/README.md`) |

Remotes: `upstream` = AntennaPod/AntennaPod, `origin` = eigener Fork.

## Ablauf

1. Upstream holen und `develop` per Fast-Forward aktualisieren:

   ```bash
   git fetch upstream
   git checkout develop
   git merge --ff-only upstream/develop
   git push origin develop
   ```

   Schlägt `--ff-only` fehl, liegen eigene Commits auf `develop`. Die gehören nach `wearos-features` (Funktionen) oder `wearos-support` (Branding).

2. Neuen Upstream-Stand in `wearos-features` bringen:

   ```bash
   git checkout wearos-features
   git merge develop
   ```

   Nicht rebasen: `wearos-features` wird in `wearos-support` hineingemergt, ein Rebase würde dort doppelte Commits und Konflikte erzeugen.

   Typische Konfliktstellen:

   - `playback/service/.../Media3PlaybackService.java`: Wear-Hooks
   - `app/src/play/AndroidManifest.xml`: Wear-Listener-Service
   - `app/src/play/.../WearListenerService.java`, `net/sync/wear-interface/...`, `app-wearos/...`: Upstream entwickelt die Wear-App weiter
   - `gradle/libs.versions.toml`, `app-wearos/build.gradle`: Abhängigkeiten

   Hat Upstream eine API geändert, die der Wear-Code nutzt (zum Beispiel `FeedMedia`), den Wear-Code anpassen (`WearPositionApplier`, `WearListenerService`).

3. Funktionen nach `wearos-support` übernehmen:

   ```bash
   git checkout wearos-support
   git merge wearos-features
   ```

   Hier kann nur das Branding kollidieren:

   - Root-`build.gradle`: Upstream-Inhalt übernehmen und die Zeile `apply from: "fork.gradle"` am Ende wieder anhängen
   - `app/.../ui/screen/preferences/MainPreferencesFragment.java`: Der Upstream-Block mit dem Hinweis "If you can read this message, the developers of this modification violate the GNU General Public License (GPL)" bleibt entfernt, nur der Debug-Hinweis (`packageHash == -1190467065`) bleibt. "Forum" und "Mitwirken" zeigen auf `https://github.com/TinyScripter/AntennaPod`
   - `ui/preferences/.../bugreport/BugReportFragment.java`: Die Buttons zeigen auf `https://github.com/TinyScripter/AntennaPod` und `https://github.com/TinyScripter/AntennaPod/issues`
   - `ui/preferences/src/main/res/xml/preferences_about.xml`: Der Versionseintrag hat den Titel `@string/app_name` statt `@string/antennapod_version`
   - `ui/i18n/src/main/res/values/strings.xml`: Nach dem Merge nach `AntennaPod` suchen und neue Texte auf "WearPod" umstellen. Ausnahmen stehen in `FORK_RELEASE.md`
   - `storage/importexport/src/main/assets/html-export-template.html` und `storage/database/.../FeedDatabaseWriter.java`: "WearPod" und der Link zum Fork-Repo bleiben

   Das Fork-README (`.github/README.md`) und die Fork-Icons (`fork-res/`, eingebunden in `fork.gradle`) sind neue Dateien und verursachen keine Konflikte. Das Root-`README.md` und `ui/common` bleiben auf dem Upstream-Stand.

4. Prüfen, dass der Mod noch erhalten ist:

   - Anwendungsname und Application-ID sind weiterhin die des Mods (`fork.gradle`)
   - Die Wear-App nutzt dieselbe Application-ID wie die Phone-App
   - `app-wearos/proguard.cfg` passt zu neuen Abhängigkeiten
   - `fork.gradle` findet `versionCode` und `versionName` noch in `app/build.gradle`
   - Der App-Name erscheint in den Texten der App als "WearPod". `grep AntennaPod ui/i18n/src/main/res/values/strings.xml` darf nur die Ausnahmen aus `FORK_RELEASE.md` zeigen
   - In den Einstellungen erscheint kein GPL-Hinweis, die Kategorie "Projekt" mit "Über" ist sichtbar, und "Forum", "Mitwirken" und "Fehler melden" führen zum Fork-Repo. Hat Upstream dort neue Links auf AntennaPod-Kanäle ergänzt, diese ebenfalls umstellen

5. Bauen und testen (zuerst in `wearos-features`, danach in `wearos-support`):

   ```bash
   ./gradlew :app:assembleDebug
   ./gradlew :app-wearos:assembleDebug
   ./gradlew --console=plain :net:sync:wear-interface:test
   ./gradlew --console=plain :app:testPlayDebugUnitTest
   ```

6. Veröffentlichen:

   ```bash
   git push origin wearos-features
   git push origin wearos-support
   ```

## Neue Upstream-Release-Tags

Neue Tags (zum Beispiel `3.12.3`) kommen mit `git fetch upstream`. Für einen Release-Stand des Mods einen eigenen Branch oder Tag von `wearos-support` anlegen, etwa `wearos-mod-3.12.3`. Der Ablauf für Release-Builds steht in `FORK_RELEASE.md`.
