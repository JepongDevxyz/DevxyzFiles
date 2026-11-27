# DevxyzFiles — v1.2.0

Jepong's own Android file manager for her **Android 16 phone, one phone only, no PC**.
It can browse, copy, move, delete and rename files inside **Android/data** and
**Android/obb** — including **Mobile Legends (com.mobile.legends)** as the
first-class test case. The UI is a frame-by-frame rebuild of **Honor's Files
app** (her order: "Ganitong UI gawin mo, make no mistakes" — see
`UI-SPEC-HONOR.md`): pure black background, yellow folders, blue links,
category grid, SOURCE card, Recently deleted, selection mode with a More
menu — every visible control wired to a real action.

No internet permission. No analytics. Offline file manager.

## The honest truth first

Android blocks normal apps from other apps' `Android/data` and `Android/obb`
since Android 11 (scoped storage). **No app can grant itself shell access** —
a fully automatic, zero-setup bypass does not exist, and anyone claiming one
is lying. DevxyzFiles uses the **Storage Access Framework (SAF)**: you pick
the folders yourself in the system picker, and the grant is saved. No
Developer Options, no PC, no root.

### What v1.2 does (Honor UI — everything in v1.1, plus)

- **Browse home**: "Browse" title, search pill, category grid (Images, Videos,
  Documents, Audio, Apps, Archives — each a real view: MediaStore queries
  with real thumbnails, extension scans, installed-apps list), SOURCE card
  (Mobile Legends → data/obb sheet, Camera, Recorder, + user sources),
  LOCAL STORAGE Phone card with real StatFs stats
  ("70.94 GB Available/512 GB"), FAVOURITES section (only when non-empty),
  LOCATION → Recently deleted
- **Phone list**: back + "Phone" title + new-folder + sort + ⋮, search pill,
  blue-link breadcrumbs with leading-ellipsis truncation, yellow folder rows
  (gear badge on a folder literally named "android"), app icons on package
  folders, relative dates + lazy async " - (N)" folder counts, thin
  auto-fading scrollbar, sort sheet (Name / Date / Size / Type), Show hidden
  files toggle
- **Selection mode** (long-press): ✕ + "N item(s) selected" / "None selected",
  radio circles, dimmed unselected names, bottom bar Share / Tags / Move /
  Delete / More — all real (Share via FileProvider/SAF URIs, folders zip
  first; Tags with filter; Move = cut + Paste; Delete = move to trash)
- **More menu** (exact order): Favourite, Rename, Copy, Details, Compress
  (real ZIP), Create desktop shortcut (real pinned shortcut), Set as source
- **Recently deleted**: delete moves to trash (30-day prune on start),
  Restore (recreates the parent, suffix on collision), Delete forever,
  Empty trash
- **Clean up**: largest files/folders on Phone, biggest first, ~5 s scan cap
  with a partial-results note; red trash button moves to trash
- **Setup Wizard** (first launch + Settings): same 4 SAF steps, restyled black
- **MLBB first-class, still**: SOURCE slot + data/obb sheet, no pinned card
  (per the Honor layout decision) — same folders, same SAF grants
- Status pill is GONE (v1.1.0): the grant state is shown by the rows
  themselves — a folder that opens is granted; a blocked folder says so
- Messages (toasts) instead of inline status text; copy-progress dialog
  restyled black

### Deliberate omissions (not oversights)

- **Safe (encrypted vault)** — the reference has it; a crypto vault is a
  whole separate feature. No Safe row is shown. Later, as v1.3.0, if she asks.
- **Honor Share / Wi-Fi Direct** — Honor system services a third-party app
  cannot invoke. The SOURCE card shows real sources instead.

### What v1.1 did

- Home shortcuts: Internal Storage, Android/data, Android/obb, Download, DCIM, Pictures
- **Pinned Mobile Legends card** on Home: MLBB data + MLBB obb with live
  counts, MLBB launcher icon when installed, and a tap to open each folder
  (v1.2.0: the pinned card is replaced by the SOURCE slot + data/obb sheet,
  per the Honor layout — same folders, same grants)
- File browser: folders first, sort by name, size + date, breadcrumbs, back
  navigation, refresh, search inside the current folder
- Long-press multi-select: copy, cut, paste, delete (confirm dialog),
  rename, new folder, file details sheet
  (v1.2.0: delete = move to Recently deleted, never permanent from the UI)
- App icons on package-named folders under Android/data and Android/obb
- **Large-file copy with a real progress bar**: MLBB OBB and resource files
  are GBs. Copy/move streams in 256 KB chunks — never the whole file in
  memory — and shows bytes copied / total, in every direction:
  SAF→Download, Download→SAF, and SAF grant to SAF grant. (Do not close
  the app mid-copy.)
- Live status pill: `SAF • data+obb granted` / `SAF • data lang` /
  `Walang grant — buksan ang Setup`, plus live data/obb accessibility
  (v1.2.0: status pill REMOVED — rows show the grant state themselves)
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
  Android 13+. Single app-folder grants work on Pixel/Samsung via a
  deep link — but Honor's picker (MagicOS, Android 15, Files v16)
  ignores the deep link and opens at the storage root, and that phone
  has no Uninstall-updates menu, so on Honor no SAF grant for
  Android/data or Android/obb is possible at all (proven on her
  Honor 400, Nov 26, 2026 — v1.2.1's document URI also opened at
  root). **Fallback that does work:** "Buksan sa Files app" asks the
  system Files app itself to open AT the MLBB folder (ACTION_VIEW,
  plain document URI — no grant, view only). Whether the Files app
  shows the folder contents on her Honor is still untested.
- A grant can be revoked in system settings — the app re-probes on
  every resume and says so instead of crashing.
- No root mode. No `/data` browsing, no APK install. ZIP compression is in
  (Compress in the More menu, plus folder shares zip first); ZIP extraction
  is not in yet.

## Changelog

- **v1.3.2** — Category grid removed + real status-bar fix (her order,
  Nov 27): the 6 category tiles (Images, Videos, Documents, Audio,
  Apps, Archives) are gone from the Browse home — SOURCE, LOCAL
  STORAGE and the bottom nav stay. Status bar, second attempt: the
  v1.3.0 `setDecorFitsSystemWindows(true)` did NOT work on her phone
  because Android 15 enforces edge-to-edge for targetSdk 35+ and
  ignores that flag (her screenshot proved it — the title still
  crowded the status bar). The real fix is Compose-level:
  `statusBarsPadding()` on the root Box pads every screen below the
  status bar on any API level. The flag call stays as a harmless
  fallback for older Android.
- **v1.3.1** — Blocked-folder jump (her screenshot, Nov 27): opening
  Android/data itself still shows "Blocked" — Android blocks LISTING
  that folder, no app can enumerate it — but the screen was a dead
  end. Now a blocked restricted folder shows "Open Mobile Legends
  data" and "Open Mobile Legends obb" buttons that jump straight to
  the known MLBB path, where listing and copying work on her phone
  (proven Nov 27: 45 MB into assets). The dead end is now a doorway.
  Also fixes one Tagalog string v1.3.0 missed: the single-item
  "Remove from sources" toast ("Naalis sa sources") is now English.
- **v1.3.0** — All English + no more setup steps (her order, Nov 27):
  the 4-step Setup Wizard is gone — the app opens straight to the main
  Browse screen, which is where everything works (including copying
  into the MLBB folder, proven on her phone Nov 27: 4 folders, 45 MB
  into dragon2017/assets). Every Tagalog string in the app is now
  English. Status-bar fit: `installSplashScreen()` put the app
  edge-to-edge, so content drew under the status bar — onCreate now
  restores `setDecorFitsSystemWindows(window, true)` so the UI sits
  exactly below the status bar, never under it. The SAF grant flow is
  gone with the wizard (the picker blocks it on this phone anyway);
  "Add a folder as source" still uses the folder picker.
- **v1.2.3** — Wizard layout fix (her video, Nov 27): the MLBB button
  pairs sat side by side in a Row wider than the dialog, so the second
  button was clipped off-screen — "Buksan sa Files app" was invisible
  even though the code was there. All three pairs (Step 2, Step 3, and
  the MLBB test row) are now stacked vertically, full-width, so every
  button is visible. No behavior change.
- **v1.2.2** — "Buksan sa Files app" button (wizard Steps 2/3 + MLBB
  source sheet): asks the system Files app itself to open AT the MLBB
  data/obb folder (ACTION_VIEW, plain document URI — view only, NOT a
  grant, NO bypass trick). This is the honest fallback now that SAF is
  proven dead on her Honor (v1.2.1 document URI also opened at root).
  Whether Honor's Files app shows the folder contents is untested —
  her phone test decides.
- **v1.2.1** — Last SAF attempt for Honor: the MLBB/data/obb grant now
  opens the picker with a document URI instead of a tree URI (Honor's
  picker ignores tree-URI deep links under Android/). Wizard tip fixed
  — it no longer claims the MLBB grant works on Honor, and the Files
  Uninstall-updates workaround is gone from the tip (no such menu on
  her phone). No Developer Options needed; if this fails, the phone
  itself blocks SAF grants for Android/data+obb.
- **v1.2.0** — Honor Files UI rebuild (her order, 2 reference videos,
  spec `UI-SPEC-HONOR.md`): black theme, Browse home + categories + SOURCE
  card + Recently deleted + Clean up, Honor list with breadcrumbs/lazy
  counts/scrollbar, selection mode + More menu (Favourite, Rename, Copy,
  Details, Compress, desktop shortcut, Set as source), Share, Tags, trash
  with 30-day prune, FileProvider for sharing. Pinned MLBB card replaced by
  the SOURCE slot + data/obb sheet. Status pill removed.
- **v1.1.0** — SAF-only rebuild (Jepong's call: "SAF na lang"). Shizuku
  removed entirely: no Shizuku client, no Wireless Debugging pairing,
  no Developer Options needed. Real SAF copy progress (256 KB-chunk
  streaming, live copied/total). Cross-layer copy: SAF↔Download and
  SAF↔SAF stream across layers. Wizard rewritten for SAF grants incl.
  MLBB subfolder-only grants and the Files-app Uninstall-updates help.
  **CI Run #4 failed on one bug**: `SafFileAccess.copy/copyWithProgress`
  passed plain `this` (the `withContext` CoroutineScope) to `streamCopy`
  — fixed locally as `this@SafFileAccess` (`FileAccess.kt:370,381`); the
  fix rides with v1.2.0, CI Run #5 is its compile proof.
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
  app/src/main/AndroidManifest.xml          (FileProvider for Share)
  app/src/main/res/xml/file_paths.xml       (FileProvider paths)
  app/src/main/java/com/jepongdevxyz/devxyzfiles/
    MainActivity.kt       Honor UI: Browse home wiring, list, selection,
                          categories, Recent, trash, cleanup, share, tags
    BrowseHome.kt         Browse home + Sources/Trash/Cleanup/MLBB sheet
    HonorIcons.kt         code-drawn Honor icons (folder, doc types, gear)
    HonorStores.kt        FavouriteStore / TagStore / SourceStore / TrashStore
    ZipHelper.kt          real ZIP (Compress + folder share)
    ShortcutHelper.kt     pinned desktop shortcuts (OPEN_PATH_EXTRA)
    SetupWizard.kt        4-step SAF wizard incl. MLBB test + Files help
    FileAccess.kt         Direct + SAF + cross-layer streaming copy
    SafHelper.kt          persisted SAF grants, picker intents
    Models.kt / Format.kt / Theme.kt   (Theme = Honor black, always dark)
.github/workflows/android.yml
README.md / SPEC.md / UI-SPEC-HONOR.md
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
