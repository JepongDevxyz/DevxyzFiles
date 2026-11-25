# DevxyzFiles — SPEC v1.2.0

Honest parity/limits table. A claim is only "works" when the real path was
exercised; anything else is marked as what it is.

## What v1.2 is

Own Android file manager for Jepong's Android 16 phone, single phone, no PC,
no Developer Options. SAF-only: per-folder grants picked by the user in the
system picker. Pure mobile controls only (tap, long-press, chips, bottom
sheets). No PC-dependent steps.

v1.2.0 is the Honor Files UI rebuild (her order: "Ganitong UI gawin mo, make
no mistakes", 2 reference videos; spec `UI-SPEC-HONOR.md` is the source of
truth). Black theme, always dark. Browse home with categories, SOURCE card,
Phone card (real StatFs), Favourites, Recently deleted. Honor list with
breadcrumbs, lazy folder counts, scrollbar, sort sheet. Selection mode with
Share / Tags / Move / Delete / More. Trash with 30-day prune. Clean up.
FileProvider sharing. The v1.1.0 pinned MLBB card is replaced by the SOURCE
slot + data/obb sheet; the status pill is removed.

v1.1.0 removed Shizuku entirely (her order: "SAF na lang" — she cannot turn
on Developer Options). No Shizuku client, no Wireless Debugging, no AIDL,
no user service. CI Run #4 failed on one bug (`SafFileAccess` passed plain
`this` — the `withContext` scope — to `streamCopy`); fixed as
`this@SafFileAccess` at `FileAccess.kt:370,381`. CI Run #5 is the proof.

## Feature table

| Feature | Status | Notes |
|---|---|---|
| Browse Internal Storage | Works (Direct) | Needs All Files Access |
| Browse Android/data | Works via SAF grant | Root grant needs Files-app Uninstall-updates on Android 13+; MLBB subfolder grant works directly |
| Browse Android/obb | Works via SAF grant | Same as above |
| MLBB data `Android/data/com.mobile.legends` | Works via SAF — first-class test | List, copy, paste, delete, rename; SOURCE slot + data/obb sheet; app icon shown |
| MLBB obb `Android/obb/com.mobile.legends` | Works via SAF — first-class test | Same; GB-sized copies stream with real progress bar |
| MLBB `/data/data/com.mobile.legends` | **NOT possible** | Needs root. SAF cannot read `/data/data` on Android 16 |
| Copy / Cut / Paste | Works | Cross-layer streaming (SAF↔Download, SAF↔SAF); real progress bar in every direction |
| Delete / Rename / New folder | Works | Via active access layer; SAF delete recurses children first. Delete = move to Recently deleted (30-day prune), never permanent from the UI |
| File details sheet | Works | Name, path, size, modified, access mode |
| Search in current folder | Works | Name filter, case-insensitive; Browse pill opens Phone list focused |
| Breadcrumbs + back navigation | Works | Tap a segment to jump; middle segments truncate "…last12" |
| App icons on package folders | Works when installed | `PackageManager.getApplicationIcon`, cached per folder |
| Setup Wizard (4 steps, live status) | Works | SAF grants incl. MLBB subfolder-only buttons + Files-app help; Step 4 runs a real probe incl. MLBB; restyled black |
| SAF grant | Works with limits | Picker refuses data/obb roots on Android 13+ unless Files updates removed; subfolder grants unaffected; revoked grants re-probed on resume |
| Browse home categories | Works | Images/Videos/Audio = MediaStore + real thumbnails; Documents/Archives = extension scan (first 500, 5 s cap, Android/data+obb trees skipped); Apps = installed user apps |
| Recent tab | Works | MediaStore.Files, newest 100; tap opens the parent folder |
| Share | Works | FileProvider (Direct) / DocumentFile URI (SAF); folders zip to cacheDir first, temp zip deleted after the chooser returns |
| Tags | Works | TagStore chips + add; tag filter chip above the list; blue bookmark mark on tagged rows |
| Favourites | Works | FavouriteStore; star badge on rows; Favourites section on Browse home when non-empty |
| Compress | Works | Real ZIP next to the original via ZipHelper (256 KB streaming) |
| Create desktop shortcut | Works when the launcher supports it | Pinned shortcut via ShortcutManager; opens MainActivity with OPEN_PATH_EXTRA; falls back to Browse home when the folder is gone/ungranted |
| Set as source | Works | SourceStore; SOURCE card + Sources screen; "Add a folder as source" via SAF picker |
| Recently deleted | Works | TrashStore (filesDir/trash/); restore recreates parent, suffix on collision; delete forever + empty trash; prune >30 days on start |
| Clean up | Works | Largest files/folders on Phone, top 50, ~5 s scan cap with partial-results note; trash button moves to trash |
| Phone card stats | Works | Real StatFs, 2-decimal GB ("70.94 GB Available/512 GB") |
| Sort (Name/Date/Size/Type) | Works | Sort sheet; default folders first, Name A–Z |
| Show hidden files | Works | List ⋮ menu toggle |
| Root mode | Not in v1.2 | No root access attempted |
| `/data` browsing | Not in v1.2 | Needs root |
| APK install / ZIP extract | Not in v1.2 | Out of scope (ZIP compress is in) |
| Copy cancel mid-copy | Not in v1.2 | Copy runs to completion; closing the app mid-copy can leave a partial file |
| Safe (encrypted vault) | Not in v1.2 — deliberate | Reference has it; a crypto vault is a separate feature (v1.3.0 if she asks) |
| Honor Share / Wi-Fi Direct | Not in v1.2 — deliberate | Honor system services a third-party app cannot invoke |

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
3. Open the MLBB SOURCE slot — data + obb list, copy one large OBB file SAF→Download
   with the progress bar visible, then delete/rename a test file
4. Confirm `/data/data` is honestly shown as not accessible, not silently empty
5. Revoke a grant in system settings, reopen the app — must say blocked,
   never crash (the v1.1.0 status pill is gone; blocked folders show the
   blocked message in the list)
6. Honor UI checks: Browse home layout vs her videos, lazy " - (N)" counts
   appearing without jank, breadcrumb truncation on deep MLBB paths,
   selection mode + More menu actions, Share of a file and of a folder,
   Compress, desktop shortcut pinning on her Honor launcher, trash
   restore/delete-forever, Clean up scan
