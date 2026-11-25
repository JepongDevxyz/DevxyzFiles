# DevxyzFiles — v1.0.0

Jepong's own Android file manager for her **Android 16 phone, one phone only, no PC**.
It can browse, copy, move, delete and rename files inside **Android/data** and
**Android/obb** — including **Mobile Legends (com.mobile.legends)** as the
first-class test case.

No internet permission. No analytics. Offline file manager.

## The honest truth first

Android blocks normal apps from other apps' `Android/data` and `Android/obb`
since Android 11 (scoped storage). **No app can grant itself shell access** —
a fully automatic, zero-setup bypass does not exist, and anyone claiming one
is lying. DevxyzFiles does the next best thing: a **built-in Shizuku client**
plus a guided one-time setup that needs **only the phone itself**.

### What v1 does

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
  memory — and shows bytes copied / total. (Do not close the app mid-copy.)
- Live status pill: `Shizuku • connected` / `Shizuku • hindi tumatakbo` /
  `Direct lang`, plus live data/obb accessibility
- Setup Wizard (first launch + Settings): 4 steps with live status checks

### Access layers (in order)

1. **Shizuku (main path for Android/data and Android/obb).** The app binds
   a UserService (`ShizukuFileService`) that runs in the Shizuku server
   process as shell (UID 2000). Its plain `java.io.File` calls can list and
   write the restricted folders. Copy/move run entirely inside that
   privileged process; progress is polled from its counters.
2. **Direct `java.io.File`** for normal storage (with All Files Access).
3. **SAF fallback** (`ACTION_OPEN_DOCUMENT_TREE` + persistable grant) for
   folders the user granted. On Android 16 the picker refuses Android/data
   and Android/obb when the Files app is up to date — the wizard explains
   the no-PC workaround (Settings > Apps > Files > Uninstall updates).

### Setup — phone only, no PC (one time)

1. **All Files Access** — the wizard deep-links to the system page.
2. **Install Shizuku** (free, Play Store, `moe.shizuku.privileged.api`).
3. **Wireless Debugging pairing, phone-only:**
   - Settings > About phone > tap Build number 7 times
   - Developer options > turn ON Wireless debugging (needs Wi-Fi on)
   - In Shizuku, tap **Pairing**
   - In Settings, tap **Pair device with pairing code** — a 6-digit code appears
   - Enter the code in the Shizuku notification, then tap **Start** in Shizuku
   - Pairing is once only. After a reboot, open Shizuku and tap Start again.
4. **Test** — the wizard really lists Android/data, Android/obb,
   Android/data/com.mobile.legends and Android/obb/com.mobile.legends and
   shows live counts.

### Honest limits (also shown in the app)

- **`/data/data/com.mobile.legends` is NOT accessible — not even via
  Shizuku.** Shell (UID 2000) cannot read `/data/data` on Android 16.
  That needs root. DevxyzFiles never claims otherwise.
- Shizuku needs Wi-Fi on for Wireless debugging, and a manual Start tap
  after every reboot (Android kills it).
- SAF cannot grant Android/data or Android/obb on Android 16 while the
  Files app is fully updated (see the workaround above).
- Copy progress for SAF copies is coarse (no live byte counter); Direct and
  Shizuku copies report real progress.
- No root mode in v1. No `/data` browsing, no APK install, no archive
  handling yet.

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
  app/src/main/aidl/.../shizuku/IDevxyzFilesService.aidl
  app/src/main/java/com/jepongdevxyz/devxyzfiles/
    MainActivity.kt       browser + home + MLBB card + copy progress
    SetupWizard.kt        4-step wizard incl. MLBB test + SAF help
    ShizukuManager.kt     install / binder / permission / bind state
    FileAccess.kt         Direct + Shizuku + SAF + router + MLBB probe
    SafHelper.kt          persisted SAF grants, picker intents
    shizuku/ShizukuFileService.kt   privileged file ops (shell UID)
    Models.kt / Format.kt / Theme.kt / AppContext.kt
.github/workflows/android.yml
README.md / SPEC.md
```

**AGP note:** the DevxyzCode template uses AGP 8.5.2, which cannot target
Android 16 (API 36). v1 uses AGP 8.7.3 — the only deliberate build change.
Gradle 8.9, Kotlin 2.0.20 and JDK 17 are unchanged.

**Shizuku versions:** client API `dev.rikka.shizuku:api:13.1.5` +
`provider:13.1.5` — the newest artifacts on Maven Central (verified
2026-11-25). The Shizuku server app is v13.6.0+ but still embeds API 13.1.5.

## Permissions (each justified)

| Permission | Why |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | File manager: full shared-storage access |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Legacy, Android 10 and below only |
| `WRITE_EXTERNAL_STORAGE` (maxSdk 29) | Legacy, Android 10 and below only |
| Shizuku `ShizukuProvider` + `<queries>` for `moe.shizuku.privileged.api` | Required by the Shizuku client library |

No `INTERNET`, no `REQUEST_INSTALL_PACKAGES` in v1.
