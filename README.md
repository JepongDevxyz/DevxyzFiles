# DevxyzFiles — v1.1.0

Jepong's own Android file manager for her **Android 16 phone, one phone only, no PC**.
It can browse, copy, move, delete and rename files inside **Android/data** and
**Android/obb** — including **Mobile Legends (com.mobile.legends)** as the
first-class test case.

No internet permission. No analytics. Offline file manager.

## The honest truth first

Android blocks normal apps from other apps' `Android/data` and `Android/obb`
since Android 11 (scoped storage). **No app can grant itself shell access** —
a fully automatic, zero-setup bypass does not exist, and anyone claiming one
is lying. DevxyzFiles uses the **Storage Access Framework (SAF)**: you pick
the folders yourself in the system picker, and the grant is saved. No
Developer Options, no PC, no root.

### What v1.1 does

- Home shortcuts: Internal Storage, Android/data, Android/obb, Download, DCIM, Pictures
- **Pinned Mobile Legends card** on Home: MLBB data + MLBB obb with live
  counts, MLBB launcher icon when installed, and a tap to open each folder
- File browser: folders first, sort by name, size + date, breadcrumbs, back
  navigation, refresh, search inside the current folder
- Long-press multi-select: copy, cut, paste, delete (confirm dialog),
  rename, new folder, file details sheet
- App icons on package-named folders under Android/data and Android/obb
- **Large-file copy with a real progress bar**: MLBB OBB and resource files
  are GBs. Copy/move streams in 256 KB chunks — never the whole file in
  memory — and shows bytes copied / total, in every direction:
  SAF→Download, Download→SAF, and SAF grant to SAF grant. (Do not close
  the app mid-copy.)
- Live status pill: `SAF • data+obb granted` / `SAF • data lang` /
  `Walang grant — buksan ang Setup`, plus live data/obb accessibility
- Setup Wizard (first launch + Settings): 4 steps with live status checks

### Access layers (in order)

1. **SAF (the only path for Android/data and Android/obb).** The user
   grants a folder in the system picker (`ACTION_OPEN_DOCUMENT_TREE` +
   persistable grant). The grant survives reboots and app restarts.
   A single app-folder grant (e.g. `Android/data/com.mobile.legends`)
   works directly, even when the picker refuses the `Android/data` root.
2. **Direct `java.io.File`** for normal storage (with All Files Access).
   For restricted folders without a grant it fails honestly — the UI says
   so instead of showing a silent empty list.

### Setup — phone only, no PC, no Developer Options (one time)

1. **All Files Access** — the wizard deep-links to the system page.
2. **Grant Android/data** in the system picker. If the picker refuses
   the root ("Can't use this folder"), grant the **MLBB data folder
   only** instead — that works directly. Or use the no-PC workaround:
   Settings > Apps > Files > three-dot menu > **Uninstall updates**,
   then grant again. A grant already given is NOT lost when the Files
   app updates again.
3. **Grant Android/obb** the same way (separate grant), or the MLBB
   obb folder only.
4. **Test** — the wizard really lists Android/data, Android/obb,
   Android/data/com.mobile.legends and Android/obb/com.mobile.legends and
   shows live counts.

### Honest limits (also shown in the app)

- **`/data/data/com.mobile.legends` is NOT accessible — not even via
  SAF.** Nothing at app level can read `/data/data` on Android 16.
  That needs root. DevxyzFiles never claims otherwise.
- SAF is slower than shell access for multi-GB OBB copies — the bytes
  go through the system's document provider. The progress bar is real,
  so at least you can see it moving.
- The picker refuses the `Android/data` and `Android/obb` roots on
  Android 13+ while the Files app is fully updated (see the workaround
  above). Single app-folder grants are not affected.
- A grant can be revoked in system settings — the app re-probes on
  every resume and says so instead of crashing.
- No root mode. No `/data` browsing, no APK install, no archive
  handling yet.

## Changelog

- **v1.1.0** — SAF-only rebuild (Jepong's call: "SAF na lang"). Shizuku
  removed entirely: no Shizuku client, no Wireless Debugging pairing,
  no Developer Options needed. Real SAF copy progress (256 KB-chunk
  streaming, live copied/total). Cross-layer copy: SAF↔Download and
  SAF↔SAF stream across layers. Wizard rewritten for SAF grants incl.
  MLBB subfolder-only grants and the Files-app Uninstall-updates help.
- **v1.0.0** — first build. CI Run #3 green; APK delivered.

## Build (CI is the build proof)

Local Gradle builds are broken in the dev VM (Gradle daemon socket failure —
known environment quirk), so **GitHub Actions is the build proof**, same as
DevxyzCode.

```text
JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.20, compileSdk/targetSdk 36
```

Workflow: `.github/workflows/android.yml` (repo root — GitHub only discovers
workflows at the repo root). It installs SDK 36 and runs
`gradle assembleDebug` in `android/`, then uploads the debug APK.

> NOTE: the agent's GitHub App has no `workflows` permission, so the
> workflow file cannot be pushed via API. It must be committed via the
> GitHub web UI by Jepong, like every other workflow in her projects.
> **No push happens without her word.**

## Project layout

```text
android/
  settings.gradle.kts / build.gradle.kts / gradle.properties
  gradle/wrapper/gradle-wrapper.properties   (Gradle 8.9)
  app/build.gradle.kts                      (AGP 8.7.3 — see note below)
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/jepongdevxyz/devxyzfiles/
    MainActivity.kt       browser + home + MLBB card + copy progress
    SetupWizard.kt        4-step SAF wizard incl. MLBB test + Files help
    FileAccess.kt         Direct + SAF + cross-layer streaming copy
    SafHelper.kt          persisted SAF grants, picker intents
    Models.kt / Format.kt / Theme.kt
.github/workflows/android.yml
README.md / SPEC.md
```

**AGP note:** the DevxyzCode template uses AGP 8.5.2, which cannot target
Android 16 (API 36). v1 uses AGP 8.7.3 — the only deliberate build change.
Gradle 8.9, Kotlin 2.0.20 and JDK 17 are unchanged.

## Permissions (each justified)

| Permission | Why |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | File manager: full shared-storage access |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Legacy, Android 10 and below only |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 29) | Legacy, Android 10 and below only |

No `INTERNET`, no `REQUEST_INSTALL_PACKAGES` in v1.1.
