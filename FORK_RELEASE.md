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
- **Launcher-Icons:** in `fork-res/main/` und `fork-res/debug/`. `fork.gradle` bindet beide Ordner für `:app` und `:app-wearos` ein. Die Upstream-Icons in `ui/common` bleiben unverändert.
- **README:** das Fork-README liegt in `.github/README.md` (GitHub zeigt es vor dem Root-`README.md` an). Das Root-`README.md` bleibt der Upstream-Stand.
- **Weitere Branding-Dateien:** `PRIVACY.md`, `FORK_RELEASE.md`, `UPSTREAM_SYNC.md`.
- **Strings der Fork-Funktionen:** in `ui/i18n/src/main/res/values/strings_wearos_standalone.xml` und den `values-*/strings_wearos_standalone.xml`. `strings.xml` und die von Weblate gepflegten Übersetzungen bleiben unverändert.

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
| Launcher-Icons | Hat Upstream das Logo neu gestaltet, das Fork-Icon in `fork-res/` auf Basis des neuen Logos neu erzeugen. Das ist kein Merge-Konflikt, weil die Dateien in unterschiedlichen Ordnern liegen |

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

Zusätzlich kontrollieren, dass die Fork-Icons verwendet werden. Dazu die `ic_launcher*.png` aus dem APK mit den Dateien in `fork-res/main/` vergleichen, zum Beispiel mit `md5sum`.

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
