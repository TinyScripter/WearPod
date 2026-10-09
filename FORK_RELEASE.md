# Release-Anleitung für den Fork

Diese Anleitung beschreibt, wie aus einem neuen AntennaPod-Release und den beiden Fork-Branches ein baubarer Stand entsteht.

## Branch-Modell

| Branch | Inhalt | Basis | Wird direkt bearbeitet? |
|---|---|---|---|
| `develop` | unveränderter Upstream-Stand von AntennaPod | `upstream/develop` bzw. Release-Tag | nein, nur vorspulen |
| `wearos-support-features` | Wear-OS-Funktionen (Download und Wiedergabe auf der Uhr, Positions-Sync), Tests, Doku | `develop` | ja, alle Code-Änderungen |
| `wearos-support-branding` | `fork.gradle` (App-ID, Name, Version), Icons, `README.md`, `PRIVACY.md`, diese Anleitung | `develop` | ja, nur Branding |
| `wearos-support` | Konsolidierung: `develop` + Features + Branding, daraus werden die APKs gebaut | wird bei jedem Release neu erzeugt | **nein** |

Beide Arbeits-Branches basieren direkt auf `develop` und hängen nicht voneinander ab. Dadurch:
- lässt sich `wearos-support-features` ohne Branding upstream als Pull Request anbieten,
- kollidieren Feature- und Branding-Änderungen nie miteinander,
- liegt das Branding beim Konsolidieren immer **oben** (es wird zuletzt gemergt).

### Was liegt wo, damit Merges konfliktarm bleiben

- **App-ID, App-Name, Provider-Authority, Version:** nur in `fork.gradle`. Sie wird über eine einzige Zeile am Ende der Root-`build.gradle` eingebunden. `app/build.gradle` und `app-wearos/build.gradle` bleiben identisch mit Upstream.
- **Version:** `fork.gradle` liest `versionCode`/`versionName` aus `app/build.gradle` (pflegt Upstream) und hängt `-mod` an. Die Uhr bekommt `versionCode × 10 + 1`. Beispiel: Upstream 3.12.3 (`3120395`) ergibt Phone `3.12.3-mod` / `3120395` und Uhr `3.12.3-mod` / `31203951`. Bei einem neuen Release ist deshalb **nichts** anzupassen.
- **Strings der Fork-Funktionen:** in `ui/i18n/src/main/res/values/strings_wearos_standalone.xml` und `values-de/strings_wearos_standalone.xml`. `strings.xml` und die von Weblate gepflegten Übersetzungen bleiben unverändert.

## Einmalige Einrichtung

```bash
git remote add upstream https://github.com/AntennaPod/AntennaPod.git
git fetch upstream --tags

# Alten Stand von wearos-support sichern, bevor der Branch das erste Mal neu erzeugt wird
git tag wearos-support-legacy origin/wearos-support
git push origin wearos-support-legacy
```

## Ablauf für ein neues Release

Die Beispiele verwenden das Upstream-Release `3.13.0`. Statt eines Tags kann auch `upstream/develop` verwendet werden.

### 1. `develop` auf den Upstream-Stand bringen

```bash
git fetch upstream --tags
git checkout develop
git merge --ff-only 3.13.0        # oder: git merge --ff-only upstream/develop
git push origin develop
```

`--ff-only` stellt sicher, dass `develop` ein reiner Upstream-Spiegel bleibt. Schlägt es fehl, wurde versehentlich auf `develop` committet. Diese Commits gehören in einen der beiden Arbeits-Branches.

### 2. Features auf den neuen Stand heben

```bash
git checkout wearos-support-features
git rebase develop
```

Konflikte entstehen nur dort, wo Upstream dieselben Stellen geändert hat. Wahrscheinliche Kandidaten:
- `playback/service/.../Media3PlaybackService.java` (`updateDatabaseAfterPlayback`): Upstream-Änderung übernehmen und die Methode wieder als `public static` mit `Context` und `lastPlayedMediaId` bereitstellen
- `app/src/play/.../WearListenerService.java`, `net/sync/wear-interface/...`, `app-wearos/...` (die Upstream-Wear-App entwickelt sich weiter)
- `gradle/libs.versions.toml`, `app-wearos/build.gradle` (Abhängigkeiten)

Konflikt lösen, dann:

```bash
git add <dateien>
git rebase --continue
```

Danach die Tests der Fork-Funktionen ausführen:

```bash
./gradlew --console=plain :net:sync:wear-interface:test :app-wearos:testPlayDebugUnitTest \
    :app:testPlayDebugUnitTest --tests de.danoeh.antennapod.WearPositionApplierTest
git push --force-with-lease origin wearos-support-features
```

### 3. Branding auf den neuen Stand heben

```bash
git checkout wearos-support-branding
git rebase develop
```

Mögliche Konflikte und ihre Lösung. **Achtung:** Beim Rebase ist `--ours` der neue Upstream-Stand und `--theirs` der eigene Fork-Commit.

| Datei | Lösung |
|---|---|
| `README.md` | Fork-Version behalten: `git checkout --theirs README.md`, danach prüfen, ob die Upstream-Änderung inhaltlich übernommen werden soll |
| Icons (`ic_launcher*.png`) | Fork-Icon behalten: `git checkout --theirs <datei>`. Hat Upstream das Logo neu gestaltet, das Fork-Icon auf Basis des neuen Logos neu erzeugen |
| `build.gradle` (Root) | Upstream-Inhalt übernehmen und die Zeile `apply from: "fork.gradle"` am Ende wieder anhängen |

```bash
git add <dateien>
git rebase --continue
git push --force-with-lease origin wearos-support-branding
```

Prüfen, ob `fork.gradle` noch passt: Die Datei liest `versionCode` und `versionName` per Regex aus `app/build.gradle`. Ändert Upstream das Format dort, bricht der Build mit der Meldung `fork.gradle: versionCode or versionName not found` ab. Dann den Regex in `fork.gradle` anpassen.

### 4. Konsolidieren

```bash
git checkout -B wearos-support develop
git merge --no-ff wearos-support-features -m "Merge wearos-support-features for 3.13.0-mod"
git merge --no-ff wearos-support-branding -m "Merge wearos-support-branding for 3.13.0-mod"
```

Da beide Branches eben auf `develop` rebased wurden und unterschiedliche Dateien ändern, laufen diese Merges ohne Konflikte durch. Gibt es doch einen Konflikt, ändern beide Branches dieselbe Datei. Dann sollte die Änderung in den passenden Branch verschoben werden.

### 5. Bauen und prüfen

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

### 6. Veröffentlichen

```bash
git push --force-with-lease origin wearos-support
git tag v3.13.0-mod
git push origin v3.13.0-mod
```

Danach die APKs im GitHub-Release `v3.13.0-mod` hochladen bzw. im Play-Test-Track veröffentlichen.

## Hotfix ohne neues Upstream-Release

1. Fix in `wearos-support-features` committen.
2. In `wearos-support-branding` in `fork.gradle` `forkVersionCodeOffset` um 1 erhöhen (Upstream-Release-Codes enden auf `95`, die Offsets 1 bis 4 sind frei) und `forkVersionNameSuffix` z. B. auf `"-mod2"` setzen. Google Play verlangt einen höheren versionCode.
3. Schritte 4 bis 6 ausführen.
4. Beim nächsten Upstream-Release `forkVersionCodeOffset` wieder auf `0` und den Suffix auf `"-mod"` setzen.

## Regeln für die tägliche Arbeit

- Nie direkt auf `develop` oder `wearos-support` committen.
- Neue Strings der Fork-Funktionen in `strings_wearos_standalone.xml` ablegen, nicht in `strings.xml`.
- Fork-spezifische Build-Einstellungen nur in `fork.gradle`, nie in den Modul-`build.gradle`-Dateien.
- Code-Änderungen nur in `wearos-support-features`, Branding nur in `wearos-support-branding`.
