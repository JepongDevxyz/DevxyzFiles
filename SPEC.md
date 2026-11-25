# DevxyzFiles — SPEC v1.0.0

Honest parity/limits table. A claim is only "works" when the real path was
exercised; anything else is marked as what it is.

## What v1 is

Own Android file manager for Jepong's Android 16 phone, single phone, no PC.
Built-in Shizuku client + on-device Wireless Debugging pairing. Pure mobile
controls only (tap, long-press, chips, bottom sheets). No PC-dependent steps.

## Feature table

| Feature | Status | Notes |
|---|---|---|
| Browse Internal Storage | Works (Direct) | Needs All Files Access |
| Browse Android/data | Works via Shizuku | Blocked without Shizuku; SAF fallback only with Files-app downgrade |
| Browse Android/obb | Works via Shizuku | Same as above |
| MLBB data `Android/data/com.mobile.legends` | Works via Shizuku — first-class test | List, copy, paste, delete, rename; pinned Home card; app icon shown |
| MLBB obb `Android/obb/com.mobile.legends` | Works via Shizuku — first-class test | Same; GB-sized copies stream with progress bar |
| MLBB `/data/data/com.mobile.legends` | **NOT possible** | Needs root. Shizuku shell UID cannot read `/data/data` on Android 16 |
| Copy / Cut / Paste | Works | Via active access layer; progress bar for Direct + Shizuku |
| Delete (confirm) / Rename / New folder | Works | Via active access layer |
| File details sheet | Works | Name, path, size, modified, access mode |
| Search in current folder | Works | Name filter, case-insensitive |
| Breadcrumbs + back navigation | Works | Tap a segment to jump |
| App icons on package folders | Works when installed | `PackageManager.getApplicationIcon`, cached per folder |
| Setup Wizard (4 steps, live status) | Works | Deep-links to system pages; Step 4 runs a real probe incl. MLBB |
| SAF fallback grant | Works with limits | Picker refuses data/obb on Android 16 unless Files updates removed |
| Root mode | Not in v1 | No root access attempted |
| `/data` browsing | Not in v1 | Needs root |
| APK install / archives (zip) | Not in v1 | Out of scope for v1 |
| Copy cancel mid-copy | Not in v1 | Copy runs to completion; closing the app mid-copy can leave a partial file |

## Access matrix

| Path | Direct | Shizuku | SAF |
|---|---|---|---|
| `/storage/emulated/0/*` (normal) | Yes | Yes | Yes (if granted) |
| `Android/data` / `Android/obb` | No (empty/blocked) | **Yes** | Only with Files-app downgrade |
| `Android/data/com.mobile.legends` | No | **Yes** | Only with Files-app downgrade |
| `Android/obb/com.mobile.legends` | No | **Yes** | Only with Files-app downgrade |
| `/data/data/com.mobile.legends` | No | **No** | No |

## Large files (MLBB)

- MLBB OBB/resource files are GBs. Copy/move streams in 256 KB chunks in
  both the Direct layer and the privileged Shizuku service — no whole file
  is ever held in memory.
- Progress: Direct and Shizuku report real bytes-copied / total. SAF copies
  report completion only (coarse).
- Progress dialog says plainly not to close the app mid-copy.

## Setup requirements (phone only)

- Wi-Fi ON (Wireless debugging needs it; no internet needed)
- Developer options enabled (tap Build number 7x)
- Shizuku installed (Play Store, free)
- One-time 6-digit pairing; after reboot, tap Start in Shizuku again
- No PC, no cable, no second phone — by design

## Build

- AGP 8.7.3 (template's 8.5.2 cannot target API 36), Gradle 8.9,
  Kotlin 2.0.20, JDK 17, compileSdk/targetSdk 36, minSdk 26
- CI: `.github/workflows/android.yml` at repo root, `assembleDebug`
- Local Gradle build is not runnable in the dev VM (broken daemon);
  CI is the build proof. No APK exists until CI runs.
- No push without Jepong's word. Target repo (when she says so):
  `JepongDevxyz/DevxyzFiles`.

## Still needs her phone test (final gate)

1. Install debug APK, run Setup Wizard, pair Shizuku (phone-only steps)
2. Open Android/data and Android/obb — real listing
3. Open the MLBB card — data + obb list, copy one large OBB file with
   the progress bar visible, then delete/rename a test file
4. Confirm `/data/data` is honestly shown as not accessible, not silently empty
5. Reboot, tap Start in Shizuku, confirm DevxyzFiles reconnects
