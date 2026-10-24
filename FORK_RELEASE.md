# Release-Anleitung für den Fork

Diese Anleitung beschreibt, wie aus einem neuen AntennaPod-Release und den Fork-Branches ein baubarer Stand entsteht. Die Kurzfassung für das reine Nachziehen von Upstream steht in `UPSTREAM_SYNC.md`.

## Branch-Modell

| Branch | Inhalt | Basis | Wird direkt bearbeitet? |
|---|---|---|---|
| `develop` | unveränderter Upstream-Stand von AntennaPod | `upstream/develop` bzw. Release-Tag | nein, nur vorspulen |
| `wearos-features` | Wear-OS-Funktionen (Download und Wiedergabe auf der Uhr, Positions-Sync), Strings, Tests, Doku. Kein Branding | `develop` | ja, alle Code-Änderungen |
| `wearos-support` | `develop` + `wearos-features` + Branding. Daraus werden die APKs gebaut | `develop` | ja, aber nur Branding |

```
upstream/develop → develop              (Spiegel)
                     └ wearos-features   (nur Wear-Funktionen)
                          └ wearos-support  (Funktionen + Branding, Release-Stand)
```

`wearos-support` wird nie neu erzeugt. Neue Funktionen und neuer Upstream-Stand kommen per Merge dorthin. Dadurch:
- lässt sich `wearos-features` ohne Branding upstream als Pull Request anbieten,
- entstehen Konflikte mit Upstream getrennt: Funktions-Konflikte in `wearos-features`, Branding-Konflikte in `wearos-support`,
- bleibt die Historie von `wearos-support` fortlaufend und muss nicht per Force-Push überschrieben werden.

### Was liegt wo, damit Merges konfliktarm bleiben

- **App-ID, App-Name, Provider-Authority, Version, minSdk 24 (für Google Play):** nur in `fork.gradle`. Sie wird über eine einzige Zeile am Ende der Root-`build.gradle` eingebunden. `app/build.gradle` und `app-wearos/build.gradle` enthalten dafür nichts.
- **Version:** `fork.gradle` liest `versionCode`/`versionName` aus `app/build.gradle` (pflegt Upstream) und hängt `-mod` an. Die Uhr bekommt `versionCode × 10 + 1`. Beispiel: Upstream 3.12.3 (`3120395`) ergibt Phone `3.12.3-mod` / `3120395` und Uhr `3.12.3-mod` / `31203951`. Bei einem neuen Release ist deshalb **nichts** anzupassen.
- **Launcher-Icons:** eigenes WearPod-Icon (mintfarbene Uhr mit Play-Button als Zifferblatt auf dunkelgrünem Verlauf) als Vector Drawables in `fork-res/main/mipmap-anydpi/`: Hintergrund, Vordergrund, Monochrom-Ebene und `ic_launcher.xml` für Android 7. Die Hintergrundfarben stehen in `fork-res/main/values/fork_icon_colors.xml`, Debug-Builds bekommen über `fork-res/debug/values/fork_icon_colors.xml` einen grauen Hintergrund. `fork.gradle` bindet beide Ordner für `:app` und `:app-wearos` ein. `mipmap-anydpi` hat Vorrang vor den PNGs in `ui/common`, das adaptive Icon `ui/common/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` greift auf die Fork-Ebenen zu. Die Upstream-Icons in `ui/common` bleiben unverändert.
- **Grafik unter Einstellungen → Über:** `fork-res/main/drawable-anydpi/teaser.xml` im Stil des Icons ersetzt `ui/common/src/main/res/drawable-nodpi/teaser.webp`.
- **README:** das Fork-README liegt in `.github/README.md` (GitHub zeigt es vor dem Root-`README.md` an). Das Root-`README.md` bleibt der Upstream-Stand.
- **Weitere Branding-Dateien:** `PRIVACY.md`, `FORK_RELEASE.md`, `UPSTREAM_SYNC.md`.
- **Einstellungen (Ausnahme, Upstream-Dateien geändert):** In `app/.../ui/screen/preferences/MainPreferencesFragment.java` ist der Upstream-Block entfernt, der bei fremder Package-ID den GPL-Hinweis anzeigt und die Kategorie "Projekt" ausblendet. "Forum" und "Mitwirken" dort sowie die Buttons in `ui/preferences/.../bugreport/BugReportFragment.java` zeigen auf `https://github.com/TinyScripter/WearPod` (bzw. `/issues`). "Dokumentation" und die Datenschutzerklärung zeigen weiter auf antennapod.org. In `ui/preferences/src/main/res/xml/preferences_about.xml` hat der Versionseintrag den Titel `@string/app_name` statt `@string/antennapod_version`.
- **Strings der Fork-Funktionen:** in `ui/i18n/src/main/res/values/strings_wearos_standalone.xml` und den `values-*/strings_wearos_standalone.xml`.
- **App-Name in den Texten (Ausnahme, Upstream-Dateien geändert):** In der englischen `ui/i18n/src/main/res/values/strings.xml` steht statt "AntennaPod" überall "WearPod". Bewusst unverändert bleiben `antennapod_version` (ungenutzt), `contributors_summary`, `rating_volunteers_label` und `rating_rate`, weil sie das Original meinen bzw. der Bewertungsdialog zum Play-Store-Eintrag des Originals führt. In `bottom_navigation_deprecation_warning` steht "AntennaPod, which WearPod is based on". Die von Weblate gepflegten Übersetzungen in `values-*/` bleiben unverändert und enthalten weiter "AntennaPod". Dasselbe gilt für `html-export-template.html` (Titel, Überschrift, Link zum Fork statt Logo und Play-Store-Link) und die beiden Hinweistexte in `FeedDatabaseWriter.java`.

## Einmalige Einrichtung

```bash
git remote add upstream https://github.com/AntennaPod/AntennaPod.git
git fetch upstream --tags
```

`origin` ist der eigene Fork.

## Ablauf für ein neues Release

Die Beispiele verwenden das Upstream-Release `3.13.0`. Statt eines Tags kann auch `upstream/develop` verwendet werden.

### 1. `develop` auf den Upstream-Stand bringen

```bash
git fetch upstream --tags
git checkout develop
git merge --ff-only 3.13.0        # oder: git merge --ff-only upstream/develop
git push origin develop
```

`--ff-only` stellt sicher, dass `develop` ein reiner Upstream-Spiegel bleibt. Schlägt es fehl, wurde versehentlich auf `develop` committet. Diese Commits gehören nach `wearos-features` oder `wearos-support`.

### 2. Funktionen auf den neuen Stand heben

```bash
git checkout wearos-features
git merge develop
```

`wearos-features` wird gemergt und nicht rebased, weil es in `wearos-support` hineingemergt wird. Ein Rebase würde die Historie umschreiben und dort doppelte Commits und Konflikte erzeugen.

Konflikte entstehen nur dort, wo Upstream dieselben Stellen geändert hat. Wahrscheinliche Kandidaten:
- `playback/service/.../Media3PlaybackService.java` (`updateDatabaseAfterPlayback`): Upstream-Änderung übernehmen und die Methode wieder als `public static` mit `Context` und `lastPlayedMediaId` bereitstellen
- `app/src/play/.../WearListenerService.java`, `net/sync/wear-interface/...`, `app-wearos/...` (die Upstream-Wear-App entwickelt sich weiter)
- `gradle/libs.versions.toml`, `app-wearos/build.gradle` (Abhängigkeiten)

Konflikt lösen, dann:

```bash
git add <dateien>
git commit
```

Danach die Tests der Fork-Funktionen ausführen. `wearos-features` baut auch ohne `fork.gradle`, also mit den Upstream-IDs:

```bash
./gradlew --console=plain :app:assembleDebug :app-wearos:assembleDebug
./gradlew --console=plain :net:sync:wear-interface:test :app-wearos:testPlayDebugUnitTest \
    :app:testPlayDebugUnitTest --tests de.danoeh.antennapod.WearPositionApplierTest
git push origin wearos-features
```

### 3. Funktionen und Branding zusammenführen

```bash
git checkout wearos-support
git merge wearos-features
```

Das bringt auch den neuen Stand von `develop` mit, weil er in Schritt 2 in `wearos-features` gemergt wurde. Mögliche Konflikte betreffen nur Branding-Dateien:

| Datei | Lösung |
|---|---|
| `build.gradle` (Root) | Upstream-Inhalt übernehmen und die Zeile `apply from: "fork.gradle"` am Ende wieder anhängen |
| `MainPreferencesFragment.java` | Upstream-Änderungen übernehmen, den Block mit dem GPL-Hinweis aber weiter entfernt lassen (nur der Debug-Hinweis `packageHash == -1190467065` bleibt). "Forum" und "Mitwirken" auf `https://github.com/TinyScripter/WearPod` lassen |
| `BugReportFragment.java` | Upstream-Änderungen übernehmen, die Buttons auf `https://github.com/TinyScripter/WearPod` und `https://github.com/TinyScripter/WearPod/issues` lassen |
| `preferences_about.xml` | Upstream-Änderungen übernehmen, der Versionseintrag behält den Titel `@string/app_name` |
| `values/strings.xml` | Upstream-Änderungen übernehmen und danach in der Datei nach `AntennaPod` suchen. Neue oder geänderte Texte wieder auf "WearPod" umstellen, außer die oben genannten Ausnahmen |
| `html-export-template.html`, `FeedDatabaseWriter.java` | Upstream-Änderungen übernehmen, "WearPod" und den Link zum Fork-Repo beibehalten |
| Launcher-Icons | Kein Merge-Konflikt, weil das WearPod-Icon ein eigener Entwurf in `fork-res/` ist. Nur prüfen, ob Upstream in `ui/common` die Namen `ic_launcher_background`, `ic_launcher_foreground` oder `ic_launcher_monochrome` bzw. `mipmap-anydpi-v26/ic_launcher.xml` geändert hat, und `fork-res/` dann angleichen |

```bash
git add <dateien>
git commit
```

Prüfen, ob `fork.gradle` noch passt: Die Datei liest `versionCode` und `versionName` per Regex aus `app/build.gradle`. Ändert Upstream das Format dort, bricht der Build mit der Meldung `fork.gradle: versionCode or versionName not found` ab. Dann den Regex in `fork.gradle` anpassen.

### 4. Bauen und prüfen

```bash
./gradlew --console=plain :app:assemblePlayRelease :app-wearos:assemblePlayRelease \
    -PreleaseStoreFile=<keystore> -PreleaseStorePassword=<passwort> \
    -PreleaseKeyAlias=<alias> -PreleaseKeyPassword=<passwort>
```

Gradle meldet dabei für `:app` und `:app-wearos` mehrmals `WARNING: BuildType(...): resValue 'string/app_name' value is being replaced` (bzw. `provider_authority`). Das ist erwartet: `fork.gradle` überschreibt an dieser Stelle die Upstream-Werte.

Package-Name und Version der APKs kontrollieren (`aapt2` liegt im Android SDK unter `build-tools/<version>/`):

```bash
aapt2 dump badging app/build/outputs/apk/play/release/app-play-release.apk | head -1
aapt2 dump badging app-wearos/build/outputs/apk/play/release/app-wearos-play-release.apk | head -1
```

Erwartet wird bei beiden `package: name='kleinabersmart.antennapod'`. Die Version ist `<upstream>-mod`, und der versionCode der Uhr ist der des Phones × 10 + 1. Beispiel für 3.12.3:

```
package: name='kleinabersmart.antennapod' versionCode='3120395' versionName='3.12.3-mod' ...
package: name='kleinabersmart.antennapod' versionCode='31203951' versionName='3.12.3-mod' ...
```

Zusätzlich kontrollieren, dass das WearPod-Icon verwendet wird: Im Launcher von Phone und Uhr muss das dunkelgrüne Icon mit der mintfarbenen Uhr und dem Play-Button erscheinen, nicht die AntennaPod-Antenne.

In der installierten App die Einstellungen öffnen: Es darf kein roter GPL-Hinweis erscheinen, die Kategorie "Projekt" mit "Über" muss sichtbar sein, und "Forum", "Mitwirken" und "Fehler melden" müssen zum Fork-Repo führen.

### 5. Veröffentlichen

```bash
git push origin wearos-support
git tag v3.13.0-mod
git push origin v3.13.0-mod
```

Danach die APKs im GitHub-Release `v3.13.0-mod` hochladen bzw. im Play-Test-Track veröffentlichen.

## Neue Funktion oder Fehlerbehebung ohne neues Upstream-Release

1. Änderung in `wearos-features` committen und die Tests ausführen.
2. Falls ein neues Release nötig ist: In `wearos-support` in `fork.gradle` `forkVersionCodeOffset` um 1 erhöhen (Upstream-Release-Codes enden auf `95`, die Offsets 1 bis 4 sind frei) und `forkVersionNameSuffix` z. B. auf `"-mod2"` setzen. Google Play verlangt einen höheren versionCode.
3. Schritte 3 bis 5 ausführen.
4. Beim nächsten Upstream-Release `forkVersionCodeOffset` wieder auf `0` und den Suffix auf `"-mod"` setzen.

## Upstream-Pull-Request

`wearos-features` enthält kein Branding, ist aber mit dem Fork-Modell verwoben (Merge-Commits). Für einen Pull-Request an AntennaPod einen Wegwerf-Branch von `upstream/develop` anlegen und nur die gewünschten Dateien übernehmen:

```bash
git checkout -b pr-<thema> upstream/develop
git checkout wearos-features -- <pfade>
```

## Regeln für die tägliche Arbeit

- Nie direkt auf `develop` committen.
- Code-Änderungen nur in `wearos-features`, Branding nur direkt in `wearos-support`.
- `wearos-features` nicht rebasen und nicht force-pushen, sobald es in `wearos-support` gemergt wurde.
- Neue Strings der Fork-Funktionen in `strings_wearos_standalone.xml` ablegen, nicht in `strings.xml`.
- Fork-spezifische Build-Einstellungen nur in `fork.gradle`, nie in den Modul-`build.gradle`-Dateien.
- Fork-Icons nur in `fork-res/` ablegen und das Fork-README nur in `.github/README.md`. `ui/common` und das Root-`README.md` bleiben beim Upstream-Stand.
- Einzige Ausnahme von Upstream-Dateien im Branding: `MainPreferencesFragment.java` und `BugReportFragment.java` (GPL-Hinweis entfernt, Links auf das Fork-Repo) sowie `preferences_about.xml` (App-Name als Titel des Versionseintrags), die englische `values/strings.xml`, `html-export-template.html` und `FeedDatabaseWriter.java` (Name "WearPod" in den Texten).
