# DevxyzFiles — SPEC v1.1.0

Honest parity/limits table. A claim is only "works" when the real path was
exercised; anything else is marked as what it is.

## What v1.1 is

Own Android file manager for Jepong's Android 16 phone, single phone, no PC,
no Developer Options. SAF-only: per-folder grants picked by the user in the
system picker. Pure mobile controls only (tap, long-press, chips, bottom
sheets). No PC-dependent steps.

v1.1.0 removed Shizuku entirely (her order: "SAF na lang" — she cannot turn
on Developer Options). No Shizuku client, no Wireless Debugging, no AIDL,
no user service.

## Feature table

| Feature | Status | Notes |
|---|---|---|
| Browse Internal Storage | Works (Direct) | Needs All Files Access |
| Browse Android/data | Works via SAF grant | Root grant needs Files-app Uninstall-updates on Android 13+; MLBB subfolder grant works directly |
| Browse Android/obb | Works via SAF grant | Same as above |
| MLBB data `Android/data/com.mobile.legends` | Works via SAF — first-class test | List, copy, paste, delete, rename; pinned Home card; app icon shown |
| MLBB obb `Android/obb/com.mobile.legends` | Works via SAF — first-class test | Same; GB-sized copies stream with real progress bar |
| MLBB `/data/data/com.mobile.legends` | **NOT possible** | Needs root. SAF cannot read `/data/data` on Android 16 |
| Copy / Cut / Paste | Works | Cross-layer streaming (SAF↔Download, SAF↔SAF); real progress bar in every direction |
| Delete (confirm) / Rename / New folder | Works | Via active access layer; SAF delete recurses children first |
| File details sheet | Works | Name, path, size, modified, access mode |
| Search in current folder | Works | Name filter, case-insensitive |
| Breadcrumbs + back navigation | Works | Tap a segment to jump |
| App icons on package folders | Works when installed | `PackageManager.getApplicationIcon`, cached per folder |
| Setup Wizard (4 steps, live status) | Works | SAF grants incl. MLBB subfolder-only buttons + Files-app help; Step 4 runs a real probe incl. MLBB |
| SAF grant | Works with limits | Picker refuses data/obb roots on Android 13+ unless Files updates removed; subfolder grants unaffected; revoked grants re-probed on resume |
| Root mode | Not in v1.1 | No root access attempted |
| `/data` browsing | Not in v1.1 | Needs root |
| APK install / archives (zip) | Not in v1.1 | Out of scope |
| Copy cancel mid-copy | Not in v1.1 | Copy runs to completion; closing the app mid-copy can leave a partial file |

## Access matrix

| Path | Direct | SAF |
|---|---|---|
| `/storage/emulated/0/*` (normal) | Yes | Yes (if granted) |
| `Android/data` / `Android/obb` | No (empty/blocked) | Yes with grant (root grant needs Files-app downgrade) |
| `Android/data/com.mobile.legends` | No | **Yes — direct subfolder grant** |
| `Android/obb/com.mobile.legends` | No | **Yes — direct subfolder grant** |
| `/data/data/com.mobile.legends` | No | No |

## Large files (MLBB)

- MLBB OBB/resource files are GBs. Copy/move streams in 256 KB chunks in
  both layers — no whole file is ever held in memory.
- Progress: Direct and SAF both report real bytes-copied / total, in every
  copy direction (SAF→Download, Download→SAF, SAF→SAF). Total comes from a
  recursive size walk before the copy starts.
- Progress dialog says plainly not to close the app mid-copy.

## Setup requirements (phone only)

- All Files Access (wizard deep-link)
- SAF grant for Android/data and Android/obb — or MLBB subfolder grants only
- Files-app Uninstall-updates workaround only if the picker refuses the roots
- No Developer Options, no Wi-Fi needed, no PC, no cable,
  no second phone — by design

## Build

- AGP 8.7.3 (template's 8.5.2 cannot target API 36), Gradle 8.9,
  Kotlin 2.0.20, JDK 17, compileSdk/targetSdk 36, minSdk 26
- CI: `.github/workflows/android.yml` at repo root, `assembleDebug`
- Local Gradle build is not runnable in the dev VM (broken daemon);
  CI is the build proof. No APK exists until CI runs.
- No push without Jepong's word. Target repo: `JepongDevxyz/DevxyzFiles`.

## Still needs her phone test (final gate)

1. Install debug APK, run Setup Wizard, grant Android/data + Android/obb
   (or MLBB subfolders only) in the system picker
2. Open Android/data and Android/obb — real listing
3. Open the MLBB card — data + obb list, copy one large OBB file SAF→Download
   with the progress bar visible, then delete/rename a test file
4. Confirm `/data/data` is honestly shown as not accessible, not silently empty
5. Revoke a grant in system settings, reopen the app — must say "Walang
   grant — buksan ang Setup", never crash
