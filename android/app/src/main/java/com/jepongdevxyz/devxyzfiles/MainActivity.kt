package com.jepongdevxyz.devxyzfiles

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as UiSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val HOME_PATH = "home"

private enum class Screen { BROWSE, RECENT, LIST, SOURCES, TRASH, CLEANUP, CATEGORY }
private enum class SortMode { NAME, DATE, SIZE, TYPE }

/** Loads an installed app's launcher icon, or null when not installed. */
fun loadAppIcon(context: Context, packageName: String): ImageBitmap? {
    return try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/** Deep link from a pinned desktop shortcut (ShortcutHelper.OPEN_PATH_EXTRA). */
object DeepLink {
    var pendingPath by mutableStateOf<String?>(null)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // installSplashScreen() puts the app edge-to-edge, so content
        // would draw under the status bar. setDecorFitsSystemWindows
        // alone does NOT fix it: Android 15 enforces edge-to-edge for
        // targetSdk 35+ and ignores that flag — the v1.3.0 fix proved
        // it on her phone (title still crowded the status bar). The
        // real fix is Compose-level: statusBarsPadding() on the root
        // Box pads every screen below the status bar, on any API level.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        intent?.getStringExtra(OPEN_PATH_EXTRA)?.let { DeepLink.pendingPath = it }
        setContent {
            DevxyzFilesTheme {
                DevxyzFilesApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(OPEN_PATH_EXTRA)?.let { DeepLink.pendingPath = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevxyzFilesApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { FileRepository(context.applicationContext) }
    val favouriteStore = remember { FavouriteStore(context.applicationContext) }
    val tagStore = remember { TagStore(context.applicationContext) }
    val sourceStore = remember { SourceStore(context.applicationContext) }
    val trashStore = remember { TrashStore(context.applicationContext) }

    // ---- Navigation state ----
    var screen by remember { mutableStateOf(Screen.BROWSE) }
    var currentPath by remember { mutableStateOf(HOME_PATH) }
    var myFilesMode by remember { mutableStateOf(false) }

    // ---- List state ----
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFocusNonce by remember { mutableStateOf(0) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var clipboard by remember { mutableStateOf<Clipboard?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var showHidden by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var mlbb by remember { mutableStateOf<MlbbStatus?>(null) }

    // ---- Dialog / sheet state ----
    var showDetails by remember { mutableStateOf<FileItem?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showNewFolderTarget by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var copyProgress by remember { mutableStateOf<Triple<String, Long, Long>?>(null) }
    var compressing by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showListMenu by remember { mutableStateOf(false) }
    var showHomeMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showMlbbSheet by remember { mutableStateOf(false) }
    var showFilesAccessDialog by remember { mutableStateOf(!Environment.isExternalStorageManager()) }
    var filesAccessDismissed by remember { mutableStateOf(false) }

    // ---- Stores / derived state ----
    val appIcons = remember { mutableStateMapOf<String, ImageBitmap>() }
    val folderCounts = remember { mutableStateMapOf<String, Int>() }
    var mlbbIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    var trashRecords by remember { mutableStateOf(trashStore.all()) }
    var favouritePaths by remember { mutableStateOf(favouriteStore.all()) }
    var favouriteItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var userSources by remember { mutableStateOf(sourceStore.all()) }
    var pendingTempZips by remember { mutableStateOf<List<File>>(emptyList()) }

    // ---- Category / Recent / Cleanup state ----
    var category by remember { mutableStateOf<Category?>(null) }
    var categoryItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var categoryLoading by remember { mutableStateOf(false) }
    var categoryNote by remember { mutableStateOf<String?>(null) }
    var categoryUris by remember { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    val categoryThumbs = remember { mutableStateMapOf<String, ImageBitmap>() }
    var recentItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var recentLoading by remember { mutableStateOf(false) }
    var cleanupItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var cleanupLoading by remember { mutableStateOf(false) }
    var cleanupPartial by remember { mutableStateOf(false) }

    val listSearchFocusRequester = remember { FocusRequester() }

    // Local functions are declared BEFORE their first use (the SAF launcher
    // callback below calls loadFolder; CI Run #2 proved the order matters).
    fun loadFolder() {
        if (currentPath == HOME_PATH) return
        loading = true
        error = null
        scope.launch {
            try {
                items = repository.list(currentPath)
            } catch (e: Exception) {
                items = emptyList()
                error = when {
                    repository.isRestricted(currentPath) ->
                        "Blocked: this folder can't be opened. " +
                            "It needs a folder grant, which this phone blocks."
                    else -> "Couldn't list: ${e.message}"
                }
            } finally {
                loading = false
            }
        }
    }

    fun runProbes() {
        scope.launch {
            mlbb = repository.probeMlbb()
        }
    }

    fun refreshTrash() {
        trashRecords = trashStore.all()
    }

    fun refreshFavourites() {
        favouritePaths = favouriteStore.all()
    }

    fun refreshSources() {
        userSources = sourceStore.all()
    }

    fun loadRecent() {
        recentLoading = true
        scope.launch {
            val (found, _) = queryMediaFiles(
                context,
                MediaStore.Files.getContentUri("external"),
                limit = 100,
                selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} != 0"
            )
            recentItems = found
            recentLoading = false
        }
    }

    fun goHome() {
        currentPath = HOME_PATH
        screen = Screen.BROWSE
        myFilesMode = false
    }

    fun openPath(path: String) {
        val p = normalisePath(path)
        if (p == HOME_PATH) {
            goHome()
        } else {
            currentPath = p
            screen = Screen.LIST
        }
    }

    fun onTabClick(tab: HomeTab) {
        when (tab) {
            HomeTab.BROWSE -> goHome()
            HomeTab.RECENT -> {
                screen = Screen.RECENT
                loadRecent()
            }
            HomeTab.MY_FILES -> {
                myFilesMode = true
                openPath(FileRepository.STORAGE_ROOT)
            }
        }
    }

    fun doPaste(destDir: String = currentPath) {
        val clip = clipboard ?: return
        if (destDir == HOME_PATH) {
            message = "Pick a folder to paste into first."
            return
        }
        // A copy can only write when All Files Access is on. Check it
        // BEFORE the copy runs — and open the prompt — instead of
        // failing every item with a generic "restricted" message.
        if (!Environment.isExternalStorageManager()) {
            showFilesAccessDialog = true
            message = "All Files Access is off — allow it first, then paste again."
            return
        }
        scope.launch {
            var done = 0
            var failed = 0
            var skipped = 0
            for (src in clip.paths) {
                val dest = joinPath(destDir, fileNameOf(src))
                if (normalisePath(src) == normalisePath(dest)) {
                    skipped++
                    continue
                }
                val ok = if (clip.cut) {
                    repository.move(src, dest)
                } else {
                    copyProgress = Triple(fileNameOf(src), 0L, 0L)
                    val result = repository.copyWithProgress(src, dest) { copied, total ->
                        scope.launch { copyProgress = Triple(fileNameOf(src), copied, total) }
                    }
                    copyProgress = null
                    result
                }
                if (ok) done++ else failed++
            }
            clipboard = null
            loadFolder()
            // The old message blamed "restriction" for everything —
            // including pasting into the same folder, which is not a
            // failure at all. Say what actually happened.
            val where = if (destDir == FileRepository.MLBB_ASSETS) " to MLBB Assets" else ""
            val verb = if (clip.cut) "moved" else "copied"
            message = when {
                failed == 0 && skipped == 0 ->
                    "Done: $done file(s) $verb$where."
                failed == 0 ->
                    "Done: $done file(s) $verb$where. $skipped already in this folder."
                else ->
                    "$done ok, $failed failed. File access is on, but the write into " +
                        "${if (destDir == FileRepository.MLBB_ASSETS) "MLBB Assets" else "this folder"} was blocked — check it in the Files app."
            }
        }
    }

    /** Unique name inside the trash dir — appends " (n)" on collision. */
    fun uniqueTrashName(name: String): String {
        val dir = trashStore.trashDir()
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val cand = "$base ($n)$ext"
            if (!File(dir, cand).exists()) return cand
            n++
        }
    }

    /** Delete = move to Recently deleted (never permanent from the UI). */
    fun moveItemsToTrash(targetItems: List<FileItem>) {
        scope.launch {
            var failed = 0
            for (item in targetItems) {
                val destName = uniqueTrashName(item.name)
                val destPath = File(trashStore.trashDir(), destName).absolutePath
                val size = if (item.isDir) {
                    try {
                        repository.accessFor(item.path).totalSize(item.path)
                    } catch (e: Exception) {
                        0L
                    }
                } else {
                    item.size
                }
                val ok = repository.move(item.path, destPath)
                if (ok) {
                    trashStore.add(
                        TrashRecord(
                            path = destPath,
                            name = destName,
                            originalParent = parentOf(item.path)
                                ?: FileRepository.STORAGE_ROOT,
                            deletedAt = System.currentTimeMillis(),
                            size = size,
                            isDir = item.isDir
                        )
                    )
                } else {
                    failed++
                }
            }
            selection = emptySet()
            selectionMode = false
            refreshTrash()
            if (screen == Screen.LIST) loadFolder()
            message = if (failed == 0) {
                "Nasa Recently deleted na: ${targetItems.size} item(s)."
            } else {
                "$failed item(s) couldn't be moved."
            }
        }
    }

    fun restoreTrash(record: TrashRecord) {
        scope.launch {
            try {
                repository.mkdir(record.originalParent)
            } catch (e: Exception) {
                // Parent may already exist — mkdir is best-effort here.
            }
            var dest = joinPath(record.originalParent, record.name)
            if (repository.exists(dest)) {
                val dot = record.name.lastIndexOf('.')
                val base = if (dot > 0 && !record.isDir) record.name.substring(0, dot) else record.name
                val ext = if (dot > 0 && !record.isDir) record.name.substring(dot) else ""
                var n = 1
                while (repository.exists(dest)) {
                    dest = joinPath(record.originalParent, "$base ($n)$ext")
                    n++
                }
            }
            val ok = repository.move(record.path, dest)
            if (ok) {
                trashStore.remove(record.path)
                message = "Na-restore na: ${record.name}"
            } else {
                message = "Couldn't restore."
            }
            refreshTrash()
        }
    }

    fun deleteForever(record: TrashRecord) {
        scope.launch {
            try {
                repository.delete(record.path)
            } catch (e: Exception) {
                // Best effort — the record is removed either way.
            }
            trashStore.remove(record.path)
            refreshTrash()
        }
    }

    fun emptyTrash() {
        scope.launch {
            for (record in trashStore.all()) {
                try {
                    repository.delete(record.path)
                } catch (e: Exception) {
                    // Best effort per record.
                }
            }
            trashStore.clear()
            refreshTrash()
        }
    }

    fun openFile(item: FileItem) {
        scope.launch {
            val uri = uriForPath(context, item.path)
            if (uri == null) {
                message = "Couldn't open the file."
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeFor(item.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(intent, "Open with"))
            } catch (e: Exception) {
                message = "No app can open this."
            }
        }
    }

    fun compressSelection() {
        val targetItems = items.filter { selection.contains(it.path) }
        showMoreMenu = false
        if (targetItems.isEmpty()) return
        scope.launch {
            compressing = true
            var done = 0
            var failed = 0
            for (item in targetItems) {
                val dest = joinPath(
                    parentOf(item.path) ?: FileRepository.STORAGE_ROOT,
                    "${item.name}.zip"
                )
                if (zipPath(context, repository, item.path, dest)) done++ else failed++
            }
            compressing = false
            selection = emptySet()
            selectionMode = false
            loadFolder()
            message = if (failed == 0) {
                "Na-compress: $done item(s)."
            } else {
                "$done ok, $failed failed."
            }
        }
    }

    fun openCategory(cat: Category) {
        category = cat
        screen = Screen.CATEGORY
        categoryItems = emptyList()
        categoryThumbs.clear()
        categoryUris = emptyMap()
        categoryNote = null
        categoryLoading = true
        scope.launch {
            when (cat) {
                Category.IMAGES -> {
                    val (found, foundUris) = queryMediaFiles(
                        context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    )
                    categoryItems = found
                    categoryUris = foundUris
                }
                Category.VIDEOS -> {
                    val (found, foundUris) = queryMediaFiles(
                        context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    )
                    categoryItems = found
                    categoryUris = foundUris
                }
                Category.AUDIO -> {
                    val (found, foundUris) = queryMediaFiles(
                        context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    )
                    categoryItems = found
                    categoryUris = foundUris
                }
                Category.DOCUMENTS -> {
                    val (found, foundNote) = scanExtensions(repository, DOC_EXTS)
                    categoryItems = found
                    categoryNote = foundNote
                }
                Category.ARCHIVES -> {
                    val (found, foundNote) = scanExtensions(repository, ARCHIVE_EXTS)
                    categoryItems = found
                    categoryNote = foundNote
                }
                Category.APPS -> {
                    categoryItems = loadApps(context)
                }
            }
            categoryLoading = false
        }
    }

    fun openCleanup() {
        screen = Screen.CLEANUP
        cleanupItems = emptyList()
        cleanupPartial = false
        cleanupLoading = true
        scope.launch {
            val (found, partial) = scanCleanup(repository)
            cleanupItems = found
            cleanupPartial = partial
            cleanupLoading = false
        }
    }

    fun openAppPackage(pkg: String) {
        scope.launch {
            val data = "${FileRepository.RESTRICTED_DATA}/$pkg"
            val obb = "${FileRepository.RESTRICTED_OBB}/$pkg"
            when {
                repository.exists(data) -> openPath(data)
                repository.exists(obb) -> openPath(obb)
                else -> message = "This app has no data folder."
            }
        }
    }

    /** One handler for SOURCE slots, used by Browse home and SourcesScreen:
     *  MLBB opens the data/obb sheet; Recorder is created on first use. */
    fun openSource(item: SourceItem) {
        when (item.kind) {
            SourceKind.APP -> showMlbbSheet = true
            SourceKind.RECORDER -> {
                scope.launch {
                    if (!repository.exists(item.path)) {
                        repository.mkdir(item.path)
                    }
                    openPath(item.path)
                }
            }
            else -> openPath(item.path)
        }
    }

    // Chooser launcher: deletes temp zips (folder shares) when it returns.
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingTempZips.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                // Best effort cleanup.
            }
        }
        pendingTempZips = emptyList()
    }

    fun shareSelection() {
        val targetItems = items.filter { selection.contains(it.path) }
        if (targetItems.isEmpty()) return
        scope.launch {
            val uris = ArrayList<Uri>()
            val temps = mutableListOf<File>()
            var failed = 0
            for (item in targetItems) {
                if (item.isDir) {
                    val zipFile = File(context.cacheDir, "${item.name}.zip")
                    if (zipPath(context, repository, item.path, zipFile.absolutePath)) {
                        temps.add(zipFile)
                        try {
                            uris.add(
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    zipFile
                                )
                            )
                        } catch (e: Exception) {
                            failed++
                        }
                    } else {
                        failed++
                    }
                } else {
                    val uri = uriForPath(context, item.path)
                    if (uri != null) uris.add(uri) else failed++
                }
            }
            if (uris.isEmpty()) {
                message = "Nothing to share."
                return@launch
            }
            pendingTempZips = temps
            val intent = Intent(
                if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            ).apply {
                type = if (uris.size == 1 && targetItems.size == 1 && !targetItems[0].isDir) {
                    mimeFor(targetItems[0].name)
                } else {
                    "*/*"
                }
                if (uris.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                shareLauncher.launch(Intent.createChooser(intent, "Share"))
            } catch (e: Exception) {
                message = "Couldn't share."
                pendingTempZips.forEach { file ->
                    try {
                        file.delete()
                    } catch (e2: Exception) {
                        // Best effort cleanup.
                    }
                }
                pendingTempZips = emptyList()
            }
            if (failed > 0) message = "$failed item(s) couldn't be shared."
            selection = emptySet()
            selectionMode = false
        }
    }

    // Folder-picker launcher for the Sources screen
    // ("Add a folder as source").
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            SafHelper.takePermission(context, uri)
            val path = SafHelper.treePath(uri)
            if (path != null && sourceStore.add(path)) {
                refreshSources()
                message = "Added as source: ${fileNameOf(path)}"
            } else if (path != null) {
                message = "Already a source."
            } else {
                message = "Couldn't get the path."
            }
        }
    }

    // Initial + resume refresh: a SAF grant can be revoked in system
    // settings, so re-probe live on every resume.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                runProbes()
                refreshTrash()
                refreshFavourites()
                refreshSources()
                if (currentPath != HOME_PATH) loadFolder()
                // All Files Access can be granted/revoked in system settings:
                // re-check on every resume so the prompt follows reality.
                if (Environment.isExternalStorageManager()) {
                    showFilesAccessDialog = false
                } else if (!filesAccessDismissed) {
                    showFilesAccessDialog = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentPath) {
        selection = emptySet()
        selectionMode = false
        searchQuery = ""
        tagFilter = null
        folderCounts.clear()
        if (currentPath != HOME_PATH) loadFolder()
    }

    // App icons for package-named folders under Android/data and Android/obb,
    // plus the Apps category (keyed by package name in both cases).
    LaunchedEffect(items, categoryItems, currentPath, category) {
        val parent = currentPath
        if (parent == FileRepository.RESTRICTED_DATA || parent == FileRepository.RESTRICTED_OBB) {
            val candidates = items.filter {
                it.isDir && it.name.contains('.') && !appIcons.containsKey(it.name)
            }
            for (item in candidates) {
                val icon = withContext(Dispatchers.IO) { loadAppIcon(context, item.name) }
                if (icon != null) appIcons[item.name] = icon
            }
        }
        if (category == Category.APPS) {
            for (item in categoryItems) {
                if (appIcons.containsKey(item.path)) continue
                val icon = withContext(Dispatchers.IO) { loadAppIcon(context, item.path) }
                if (icon != null) appIcons[item.path] = icon
            }
        }
    }

    // Lazy folder counts (§3.5): filled in the background after the list
    // renders — a row shows only its date until its count arrives.
    LaunchedEffect(items, currentPath) {
        val folders = items.filter { it.isDir }.take(40)
        for (folder in folders) {
            val count = try {
                withContext(Dispatchers.IO) { repository.list(folder.path).size }
            } catch (e: Exception) {
                null
            }
            if (count != null) folderCounts[folder.path] = count
        }
    }

    // Real thumbnails for the Images / Videos / Audio categories (§7).
    LaunchedEffect(categoryItems, category) {
        if (category != Category.IMAGES && category != Category.VIDEOS &&
            category != Category.AUDIO
        ) {
            return@LaunchedEffect
        }
        for (item in categoryItems.take(100)) {
            val uri = categoryUris[item.path] ?: continue
            val thumb = loadThumbnailBitmap(context, uri)
            if (thumb != null) categoryThumbs[item.path] = thumb
        }
    }

    // Favourite rows for Browse home: resolve each starred path to its live
    // FileItem (so the icon is right); gone items are skipped silently.
    LaunchedEffect(favouritePaths) {
        val out = mutableListOf<FileItem>()
        for (path in favouritePaths) {
            val parent = parentOf(path) ?: continue
            val found = try {
                repository.list(parent).firstOrNull { it.path == normalisePath(path) }
            } catch (e: Exception) {
                null
            }
            if (found != null) out.add(found)
        }
        favouriteItems = out
    }

    // MLBB launcher icon for the SOURCE slot.
    // Trash prune on start: records + files older than 30 days are deleted.
    LaunchedEffect(Unit) {
        mlbbIcon = withContext(Dispatchers.IO) {
            loadAppIcon(context, FileRepository.MLBB_PACKAGE)
        }
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val old = trashStore.all().filter { it.deletedAt < cutoff }
        for (record in old) {
            try {
                repository.delete(record.path)
            } catch (e: Exception) {
                // Best effort — the record is removed either way.
            }
            trashStore.remove(record.path)
        }
        if (old.isNotEmpty()) refreshTrash()
    }

    // Pinned-shortcut deep link (ShortcutHelper.OPEN_PATH_EXTRA).
    LaunchedEffect(DeepLink.pendingPath) {
        val path = DeepLink.pendingPath ?: return@LaunchedEffect
        DeepLink.pendingPath = null
        val ok = try {
            repository.list(path)
            true
        } catch (e: Exception) {
            false
        }
        if (ok) {
            openPath(path)
        } else {
            message = "This folder can't be opened anymore — it may have moved, or the grant is gone."
        }
    }

    // Transient messages go to a Toast (v1.2.0 has no status-pill line).
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            message = null
        }
    }

    BackHandler(enabled = true) {
        when {
            showMoreMenu -> showMoreMenu = false
            showHomeMenu -> showHomeMenu = false
            showListMenu -> showListMenu = false
            showSortSheet -> showSortSheet = false
            showTagsDialog -> showTagsDialog = false
            showMlbbSheet -> showMlbbSheet = false
            selectionMode -> {
                selectionMode = false
                selection = emptySet()
            }
            screen == Screen.LIST -> {
                val parent = parentOf(currentPath)
                if (parent == null) goHome() else currentPath = parent
            }
            screen != Screen.BROWSE -> goHome()
            else -> (context as? Activity)?.finish()
        }
    }

    // ---- Derived UI data ----
    val phoneLabel = phoneStats()
    val sourcesList = remember(mlbbIcon, userSources) {
        buildList {
            add(
                SourceItem(
                    "Mobile Legends",
                    FileRepository.MLBB_DATA,
                    SourceKind.APP,
                    mlbbIcon
                )
            )
            add(SourceItem("Camera", "/storage/emulated/0/DCIM/Camera", SourceKind.CAMERA))
            add(SourceItem("Recorder", "/storage/emulated/0/Recordings", SourceKind.RECORDER))
            userSources.forEach { path ->
                add(SourceItem(fileNameOf(path), path, SourceKind.FOLDER))
            }
        }
    }
    val showNavOnList = screen == Screen.LIST &&
        currentPath == FileRepository.STORAGE_ROOT && myFilesMode
    val selectionTags = selection.flatMap { tagStore.tagsFor(it) }.toSet()
    val folderTags = items.flatMap { tagStore.tagsFor(it.path) }.distinct().sorted()

    // The list screen's visible rows: hidden filter → tag filter → search
    // filter → sort. Hoisted here so select-all (glyph, list ⋮ menu) selects
    // exactly the rows the user sees (§9.4: no exceptions).
    val visibleListItems = remember(items, searchQuery, sortMode, showHidden, tagFilter) {
        var list = items
        if (!showHidden) list = list.filter { !it.name.startsWith(".") }
        if (tagFilter != null) {
            list = list.filter { tagStore.tagsFor(it.path).contains(tagFilter) }
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        when (sortMode) {
            SortMode.NAME -> list.sortedWith(
                compareByDescending<FileItem> { it.isDir }.thenBy { it.name.lowercase() }
            )
            SortMode.DATE -> list.sortedWith(
                compareByDescending<FileItem> { it.lastModified }
            )
            SortMode.SIZE -> list.sortedWith(
                compareByDescending<FileItem> { it.size }.thenBy { it.name.lowercase() }
            )
            SortMode.TYPE -> list.sortedWith(
                compareByDescending<FileItem> { it.isDir }
                    .thenBy { it.name.substringAfterLast('.', "").lowercase() }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HonorColors.Background)
            .statusBarsPadding()
    ) {
        when (screen) {
            Screen.BROWSE -> BrowseHome(
                phoneLabel = phoneLabel,
                recentlyDeletedCount = trashRecords.size,
                favourites = favouriteItems,
                sources = sourcesList,
                selectedTab = HomeTab.BROWSE,
                onTabClick = ::onTabClick,
                onSearchClick = {
                    myFilesMode = false
                    openPath(FileRepository.STORAGE_ROOT)
                    searchFocusNonce++
                },
                onSourceClick = ::openSource,
                onAllSourcesClick = { screen = Screen.SOURCES },
                onPhoneClick = {
                    myFilesMode = false
                    openPath(FileRepository.STORAGE_ROOT)
                },
                onCleanUpClick = ::openCleanup,
                onRecentlyDeletedClick = { screen = Screen.TRASH },
                onFavouriteClick = { item ->
                    if (item.isDir) openPath(item.path) else openFile(item)
                },
                onAddClick = {
                    showNewFolderTarget = FileRepository.STORAGE_ROOT
                    showNewFolder = true
                },
                onOverflowClick = { showHomeMenu = true }
            )

            Screen.RECENT -> RecentScreen(
                items = recentItems,
                loading = recentLoading,
                selectedTab = HomeTab.RECENT,
                onTabClick = ::onTabClick,
                onItemClick = { item ->
                    val parent = parentOf(item.path)
                    if (parent != null) openPath(parent)
                    else message = "No parent folder."
                },
                onBack = ::goHome
            )

            Screen.SOURCES -> SourcesScreen(
                sources = sourcesList,
                onSourceClick = ::openSource,
                onRemoveSource = { item ->
                    if (userSources.contains(item.path)) {
                        sourceStore.remove(item.path)
                        refreshSources()
                        message = "Removed from sources: ${item.label}"
                    } else {
                        message = "This is a built-in source — it can't be removed."
                    }
                },
                onAddSourceClick = {
                    safLauncher.launch(initialTreeUri(FileRepository.STORAGE_ROOT))
                },
                onBack = ::goHome
            )

            Screen.TRASH -> TrashScreen(
                records = trashRecords,
                onRestore = ::restoreTrash,
                onDeleteForever = ::deleteForever,
                onEmptyTrash = ::emptyTrash,
                onBack = ::goHome
            )

            Screen.CLEANUP -> CleanupScreen(
                largest = cleanupItems,
                loading = cleanupLoading,
                partial = cleanupPartial,
                onDelete = { item ->
                    moveItemsToTrash(listOf(item))
                    cleanupItems = cleanupItems - item
                },
                onOpen = { item ->
                    if (item.isDir) openPath(item.path) else openFile(item)
                },
                onBack = ::goHome
            )

            Screen.CATEGORY -> CategoryScreen(
                title = when (category) {
                    Category.IMAGES -> "Images"
                    Category.VIDEOS -> "Videos"
                    Category.DOCUMENTS -> "Documents"
                    Category.AUDIO -> "Audio"
                    Category.APPS -> "Apps"
                    Category.ARCHIVES -> "Archives"
                    null -> ""
                },
                items = categoryItems,
                loading = categoryLoading,
                note = categoryNote,
                thumbs = categoryThumbs,
                appIcons = appIcons,
                isApps = category == Category.APPS,
                onItemClick = { item ->
                    when {
                        category == Category.APPS -> openAppPackage(item.path)
                        item.isDir -> openPath(item.path)
                        else -> openFile(item)
                    }
                },
                onBack = ::goHome
            )

            Screen.LIST -> Column(modifier = Modifier.fillMaxSize()) {
                HonorListScreen(
                    currentPath = currentPath,
                    items = visibleListItems,
                    loading = loading,
                    error = error,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    searchFocusRequester = listSearchFocusRequester,
                    searchFocusNonce = searchFocusNonce,
                    selectionMode = selectionMode,
                    selection = selection,
                    folderCounts = folderCounts,
                    appIcons = appIcons,
                    favouritePaths = favouritePaths,
                    tagStore = tagStore,
                    tagFilter = tagFilter,
                    onClearTagFilter = { tagFilter = null },
                    clipboard = clipboard,
                    onBack = {
                        val parent = parentOf(currentPath)
                        if (parent == null) goHome() else currentPath = parent
                    },
                    onOpen = { item ->
                        if (item.isDir) openPath(item.path) else openFile(item)
                    },
                    onToggleSelect = { path ->
                        val next = if (selection.contains(path)) selection - path else selection + path
                        selection = next
                        // Long-press enters selection mode (non-empty selection);
                        // tapping the last selected row off exits it. Select-all
                        // toggle-off does NOT go through here, so "None selected"
                        // keeps the mode active (§6).
                        selectionMode = next.isNotEmpty()
                    },
                    onSelectAll = {
                        val paths = visibleListItems.map { it.path }
                        selection = if (selection.size == paths.size && paths.isNotEmpty()) {
                            // Toggle off — selection mode STAYS active ("None selected").
                            emptySet()
                        } else {
                            paths.toSet()
                        }
                    },
                    onExitSelection = {
                        selectionMode = false
                        selection = emptySet()
                    },
                    onNewFolder = {
                        showNewFolderTarget = currentPath
                        showNewFolder = true
                    },
                    onSortClick = { showSortSheet = true },
                    onListMenuClick = { showListMenu = true },
                    onNavigateTo = ::openPath,
                    isRestricted = repository.isRestricted(currentPath),
                    onOpenMlbbData = { openPath(FileRepository.MLBB_DATA) },
                    onOpenMlbbObb = { openPath(FileRepository.MLBB_OBB) },
                    onOpenInFiles = {
                        if (!SafHelper.openFolderInFilesApp(context, currentPath)) {
                            Toast.makeText(
                                context,
                                "The Files app couldn't open this folder.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onPaste = { doPaste() },
                    onPasteToMlbb = { doPaste(FileRepository.MLBB_ASSETS) },
                    onShare = ::shareSelection,
                    onTags = { showTagsDialog = true },
                    onMove = {
                        if (selection.isNotEmpty()) {
                            clipboard = Clipboard(selection.toList(), cut = true)
                            message = "${selection.size} item(s) cut — pick a folder, then Paste."
                            selection = emptySet()
                            selectionMode = false
                        }
                    },
                    onDelete = { showDeleteConfirm = true },
                    onMore = { showMoreMenu = true },
                    modifier = Modifier.weight(1f)
                )
                if (showNavOnList) {
                    HonorBottomNav(selectedTab = HomeTab.MY_FILES, onTabClick = ::onTabClick)
                }
            }
        }

        // ---- Overlays / dialogs ----
        if (showHomeMenu) {
            HonorPopupMenu(
                items = listOf(
                    MenuItemSpec("Refresh") {
                        showHomeMenu = false
                        runProbes()
                        refreshTrash()
                        refreshFavourites()
                        refreshSources()
                        message = "Refreshed."
                    }
                ),
                onDismiss = { showHomeMenu = false }
            )
        }

        if (showListMenu) {
            HonorPopupMenu(
                items = listOf(
                    MenuItemSpec("Select all") {
                        showListMenu = false
                        selectionMode = true
                        selection = visibleListItems.map { it.path }.toSet()
                    },
                    MenuItemSpec("Sort by") {
                        showListMenu = false
                        showSortSheet = true
                    },
                    MenuItemSpec("Show hidden files", checked = showHidden) {
                        showListMenu = false
                        showHidden = !showHidden
                    }
                ),
                onDismiss = { showListMenu = false }
            )
        }

        if (showMoreMenu) {
            val selectedItems = items.filter { selection.contains(it.path) }
            val single = if (selectedItems.size == 1) selectedItems[0] else null
            val allFav = selectedItems.isNotEmpty() &&
                selectedItems.all { favouriteStore.isFavourite(it.path) }
            val isSource = single?.isDir == true && sourceStore.contains(single.path)
            val menuItems = buildList {
                add(
                    MenuItemSpec(if (allFav) "Remove from favourites" else "Favourite") {
                        showMoreMenu = false
                        for (item in selectedItems) favouriteStore.toggle(item.path)
                        refreshFavourites()
                        selection = emptySet()
                        selectionMode = false
                    }
                )
                if (single != null) {
                    add(
                        MenuItemSpec("Rename") {
                            showMoreMenu = false
                            renameTarget = single
                        }
                    )
                }
                add(
                    MenuItemSpec("Copy") {
                        showMoreMenu = false
                        clipboard = Clipboard(selection.toList(), cut = false)
                        message = "${selection.size} item(s) copied — pick a folder, then Paste."
                        selection = emptySet()
                        selectionMode = false
                    }
                )
                if (single != null) {
                    add(
                        MenuItemSpec("Details") {
                            showMoreMenu = false
                            showDetails = single
                        }
                    )
                }
                add(
                    MenuItemSpec("Compress") {
                        compressSelection()
                    }
                )
                if (single?.isDir == true) {
                    add(
                        MenuItemSpec("Create desktop shortcut") {
                            showMoreMenu = false
                            val ok = requestPinShortcut(context, single.name, single.path)
                            message = if (ok) {
                                "Shortcut request sent — i-confirm sa launcher."
                            } else {
                                "Your launcher doesn't support this."
                            }
                            selection = emptySet()
                            selectionMode = false
                        }
                    )
                    add(
                        MenuItemSpec(if (isSource) "Remove from sources" else "Set as source") {
                            showMoreMenu = false
                            if (isSource) {
                                sourceStore.remove(single.path)
                                message = "Removed from sources: ${single.name}"
                            } else {
                                sourceStore.add(single.path)
                                message = "Na-add as source: ${single.name}"
                            }
                            refreshSources()
                            selection = emptySet()
                            selectionMode = false
                        }
                    )
                }
            }
            HonorPopupMenu(
                items = menuItems,
                anchorBottom = true,
                onDismiss = { showMoreMenu = false }
            )
        }
    }

    if (copyProgress != null) {
        val (label, copied, total) = copyProgress!!
        CopyProgressDialog(label = label, copied = copied, total = total)
    }

    if (compressing) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = HonorColors.Card,
            titleContentColor = HonorColors.OnSurface,
            textContentColor = HonorColors.Muted,
            title = { Text("Compressing…") },
            text = {
                Column {
                    LinearProgressIndicator(
                        color = HonorColors.AccentBlue,
                        trackColor = HonorColors.Divider,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Zipping your selection. Don't close the app.", color = HonorColors.Muted)
                }
            },
            confirmButton = {}
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = HonorColors.Card,
            titleContentColor = HonorColors.OnSurface,
            textContentColor = HonorColors.Muted,
            title = { Text("Delete?") },
            text = {
                Text(
                    "The ${selection.size} item(s) will be moved to Recently deleted. " +
                        "You can still restore them within 30 days.",
                    color = HonorColors.Muted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        moveItemsToTrash(items.filter { selection.contains(it.path) })
                    }
                ) { Text("Move to trash", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = HonorColors.AccentBlue)
                }
            }
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            target = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                scope.launch {
                    val ok = repository.rename(
                        target.path,
                        joinPath(parentOf(target.path) ?: FileRepository.STORAGE_ROOT, newName)
                    )
                    message = if (ok) "Renamed." else "Couldn't rename."
                    loadFolder()
                }
            }
        )
    }

    if (showNewFolder) {
        val target = showNewFolderTarget ?: currentPath
        NewFolderDialog(
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                scope.launch {
                    val ok = repository.mkdir(joinPath(target, name))
                    message = if (ok) "Folder created." else "Couldn't create the folder."
                    if (target == currentPath) loadFolder()
                }
            }
        )
    }

    showDetails?.let { item ->
        DetailsSheet(item = item, onDismiss = { showDetails = null })
    }

    if (showSortSheet) {
        SortSheet(
            current = sortMode,
            onSelect = { mode ->
                sortMode = mode
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }

    if (showTagsDialog) {
        TagsDialog(
            tags = selectionTags,
            allFolderTags = folderTags,
            onAddTag = { tag ->
                for (path in selection) tagStore.addTag(path, tag)
            },
            onRemoveTag = { tag ->
                for (path in selection) tagStore.removeTag(path, tag)
            },
            onFilterTag = { tag ->
                tagFilter = tag
                showTagsDialog = false
            },
            onDismiss = { showTagsDialog = false }
        )
    }

    if (showMlbbSheet) {
        MlbbSourceSheet(
            dataLabel = "MLBB data — " + (mlbb?.data?.let {
                if (it.accessible) "${it.count} items (${it.via})" else "blocked"
            } ?: "…"),
            obbLabel = "MLBB obb — " + (mlbb?.obb?.let {
                if (it.accessible) "${it.count} items (${it.via})" else "blocked"
            } ?: "…"),
            onDataClick = {
                showMlbbSheet = false
                openPath(FileRepository.MLBB_DATA)
            },
            onObbClick = {
                showMlbbSheet = false
                openPath(FileRepository.MLBB_OBB)
            },
            onOpenInFiles = { path ->
                showMlbbSheet = false
                if (!SafHelper.openFolderInFilesApp(context, path)) {
                    Toast.makeText(
                        context,
                        "The Files app couldn't open this folder.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { showMlbbSheet = false }
        )
    }

    if (showFilesAccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showFilesAccessDialog = false
                filesAccessDismissed = true
            },
            containerColor = HonorColors.Card,
            titleContentColor = HonorColors.OnSurface,
            textContentColor = HonorColors.Muted,
            title = { Text("Allow file access") },
            text = {
                Text(
                    "DevxyzFiles needs All Files Access to find your files and copy them — including into Mobile Legends. Without it, folders look empty.",
                    color = HonorColors.Muted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFilesAccessDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                ) { Text("Allow", color = HonorColors.AccentBlue) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFilesAccessDialog = false
                        filesAccessDismissed = true
                    }
                ) { Text("Later", color = HonorColors.AccentBlue) }
            }
        )
    }
}

private fun initialTreeUri(path: String): Uri {
    val target = normalisePath(path)
    val relative = target.removePrefix("/storage/emulated/0/")
    val encoded = Uri.encode(relative)
    val restricted = target == FileRepository.RESTRICTED_DATA ||
        target.startsWith("${FileRepository.RESTRICTED_DATA}/") ||
        target == FileRepository.RESTRICTED_OBB ||
        target.startsWith("${FileRepository.RESTRICTED_OBB}/")
    // Restricted folders (Android/data, Android/obb): Honor's DocumentsUI
    // ignores a tree-URI deep link and opens at the storage root instead
    // (proven on her Honor 400, v1.2.0). A document URI is the other form
    // the picker accepts as its initial location — the last SAF attempt
    // before concluding the phone itself blocks the grant. Normal folders
    // keep the tree form that already works.
    return if (restricted) {
        Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3A" + encoded
        )
    } else {
        Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3A" + encoded
        )
    }
}

/** Real StatFs phone stats for the Browse home Phone card (§4.5). */
private fun phoneStats(): String {
    return try {
        val stat = StatFs(FileRepository.STORAGE_ROOT)
        "${formatGb2(stat.availableBytes)} Available/${formatGb2(stat.totalBytes)}"
    } catch (e: Exception) {
        "—"
    }
}

/** Share / open URI for a path: the SAF DocumentFile URI when a grant
 *  covers it, otherwise a FileProvider URI for the Direct file. */
private fun uriForPath(context: Context, path: String): Uri? {
    val p = normalisePath(path)
    if (SafHelper.covers(context, p)) {
        val root = SafHelper.persistedRoots(context)
            .firstOrNull { (_, rootPath) -> p == rootPath || p.startsWith("$rootPath/") }
            ?: return null
        val tree = DocumentFile.fromTreeUri(context, root.first) ?: return null
        return SafHelper.resolve(context, tree, p)?.uri
    }
    val file = File(p)
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        null
    }
}

private fun mimeFor(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}

/** MediaStore query shared by Recent + Images/Videos/Audio categories.
 *  Items without a DATA path are skipped (the app needs a real path to
 *  open the parent folder). Returns the items plus their content URIs
 *  (for thumbnails and ACTION_VIEW). */
private suspend fun queryMediaFiles(
    context: Context,
    contentUri: Uri,
    limit: Int = 200,
    selection: String? = null
): Pair<List<FileItem>, Map<String, Uri>> = withContext(Dispatchers.IO) {
    val items = mutableListOf<FileItem>()
    val uris = mutableMapOf<String, Uri>()
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED
    )
    try {
        context.contentResolver.query(
            contentUri,
            projection,
            selection,
            null,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            while (cursor.moveToNext() && items.size < limit) {
                val data = cursor.getString(dataIdx) ?: continue
                val id = cursor.getLong(idIdx)
                val item = FileItem(
                    name = cursor.getString(nameIdx) ?: fileNameOf(data),
                    path = normalisePath(data),
                    isDir = false,
                    size = cursor.getLong(sizeIdx),
                    lastModified = cursor.getLong(dateIdx) * 1000,
                    via = AccessMode.DIRECT
                )
                items.add(item)
                uris[item.path] = ContentUris.withAppendedId(contentUri, id)
            }
        }
    } catch (e: Exception) {
        // MediaStore query failed — the category shows empty, no crash.
    }
    items to uris
}

private suspend fun loadThumbnailBitmap(context: Context, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        try {
            val bitmap = context.contentResolver.loadThumbnail(uri, Size(96, 96), null)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

private val DOC_EXTS = setOf(
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "md", "csv", "txt", "log"
)
private val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso")

/** Recursive extension scan of Phone root (§7). Returns the matches plus
 *  a note when the 500-match cap or the 5 s time cap was hit. */
private suspend fun scanExtensions(
    repository: FileRepository,
    exts: Set<String>,
    limit: Int = 500,
    timeBudgetMs: Long = 5000
): Pair<List<FileItem>, String?> {
    val out = mutableListOf<FileItem>()
    val start = System.currentTimeMillis()
    var limitHit = false
    var timeHit = false
    suspend fun walk(path: String) {
        if (out.size >= limit) {
            limitHit = true
            return
        }
        if (System.currentTimeMillis() - start > timeBudgetMs) {
            timeHit = true
            return
        }
        val children = try {
            repository.list(path)
        } catch (e: Exception) {
            return
        }
        for (child in children) {
            if (out.size >= limit) {
                limitHit = true
                return
            }
            if (System.currentTimeMillis() - start > timeBudgetMs) {
                timeHit = true
                return
            }
            if (child.isDir) {
                // App-data trees hold thousands of junk files and would eat
                // the whole time budget before real documents are reached.
                if (child.path == FileRepository.RESTRICTED_DATA ||
                    child.path == FileRepository.RESTRICTED_OBB
                ) {
                    continue
                }
                walk(child.path)
            } else if (exts.contains(child.name.substringAfterLast('.', "").lowercase())) {
                out.add(child)
            }
        }
    }
    walk(FileRepository.STORAGE_ROOT)
    val note = when {
        limitHit -> "Showing first 500"
        timeHit -> "Scan stopped — showing partial results"
        else -> null
    }
    return out to note
}

/** Largest files/folders under Phone root (§7): recursive file walk plus
 *  a totalSize per top-level folder, top 50 by size, ~5 s time cap. */
private suspend fun scanCleanup(
    repository: FileRepository,
    timeBudgetMs: Long = 5000,
    limit: Int = 50
): Pair<List<FileItem>, Boolean> {
    val start = System.currentTimeMillis()
    var partial = false
    val files = mutableListOf<FileItem>()
    suspend fun walk(path: String) {
        if (System.currentTimeMillis() - start > timeBudgetMs) {
            partial = true
            return
        }
        val children = try {
            repository.list(path)
        } catch (e: Exception) {
            return
        }
        for (child in children) {
            if (System.currentTimeMillis() - start > timeBudgetMs) {
                partial = true
                return
            }
            if (child.isDir) {
                if (child.path == FileRepository.RESTRICTED_DATA ||
                    child.path == FileRepository.RESTRICTED_OBB
                ) {
                    continue
                }
                walk(child.path)
            } else {
                files.add(child)
            }
        }
    }
    walk(FileRepository.STORAGE_ROOT)
    val dirs = mutableListOf<FileItem>()
    val topDirs = try {
        repository.list(FileRepository.STORAGE_ROOT).filter { it.isDir }
    } catch (e: Exception) {
        emptyList()
    }
    for (dir in topDirs) {
        if (System.currentTimeMillis() - start > timeBudgetMs) {
            partial = true
            break
        }
        if (dir.path == FileRepository.RESTRICTED_DATA ||
            dir.path == FileRepository.RESTRICTED_OBB
        ) {
            continue
        }
        val size = try {
            repository.accessFor(dir.path).totalSize(dir.path)
        } catch (e: Exception) {
            0L
        }
        if (size > 0) dirs.add(dir.copy(size = size))
    }
    val all = (files + dirs).sortedByDescending { it.size }.take(limit)
    return all to partial
}

/** Installed user apps (Name A–Z) for the Apps category (§7). The path
 *  carries the package name; the click handler resolves it to the app's
 *  Android/data or Android/obb folder. */
private suspend fun loadApps(context: Context): List<FileItem> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    try {
        pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            .map { app ->
                FileItem(
                    name = pm.getApplicationLabel(app).toString(),
                    path = app.packageName,
                    isDir = true,
                    size = 0L,
                    lastModified = 0L,
                    via = AccessMode.DIRECT
                )
            }
    } catch (e: Exception) {
        emptyList()
    }
}

/* ------------------------------------------------------------------ */
/* Honor Files UI (v1.2.0) — list screens, rows, menus, dialogs.      */
/* ------------------------------------------------------------------ */

/** Shared search pill (Browse home style): 52dp, #191919, fully rounded. */
@Composable
private fun HonorSearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(HonorColors.SearchPill)
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = HonorColors.OnSurface),
            cursorBrush = SolidColor(HonorColors.OnSurface),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HonorIcons.Search, contentDescription = null, tint = HonorColors.Muted)
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search", fontSize = 16.sp, color = HonorColors.Muted)
                        }
                        innerTextField()
                    }
                }
            }
        )
    }
}

/** The Honor list screen (§5): top bar, search pill, breadcrumbs,
 *  rows with lazy counts, selection mode, clipboard bar, scrollbar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HonorListScreen(
    currentPath: String,
    items: List<FileItem>,
    loading: Boolean,
    error: String?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    searchFocusNonce: Int,
    selectionMode: Boolean,
    selection: Set<String>,
    folderCounts: Map<String, Int>,
    appIcons: Map<String, ImageBitmap>,
    favouritePaths: Set<String>,
    tagStore: TagStore,
    tagFilter: String?,
    onClearTagFilter: () -> Unit,
    clipboard: Clipboard?,
    onBack: () -> Unit,
    onOpen: (FileItem) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
    onNewFolder: () -> Unit,
    onSortClick: () -> Unit,
    onListMenuClick: () -> Unit,
    onNavigateTo: (String) -> Unit,
    isRestricted: Boolean,
    onOpenMlbbData: () -> Unit,
    onOpenMlbbObb: () -> Unit,
    onOpenInFiles: () -> Unit,
    onPaste: () -> Unit,
    onPasteToMlbb: () -> Unit,
    onShare: () -> Unit,
    onTags: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Thin scrollbar (§3.10): visible while scrolling, fades ~1s after idle.
    var scrollbarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            scrollbarVisible = true
        } else {
            delay(1000)
            scrollbarVisible = false
        }
    }
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (scrollbarVisible) 1f else 0f,
        label = "scrollbar"
    )

    LaunchedEffect(searchFocusNonce) {
        if (searchFocusNonce > 0) searchFocusRequester.requestFocus()
    }

    // Breadcrumb: "Phone" + relative segments, auto-scrolled to the end.
    val isRoot = normalisePath(currentPath) == FileRepository.STORAGE_ROOT
    val relative = normalisePath(currentPath)
        .removePrefix("/storage/emulated/0").trimStart('/')
    val segments = if (relative.isEmpty()) emptyList() else relative.split('/')
    val crumbScroll = rememberScrollState()
    LaunchedEffect(currentPath) {
        crumbScroll.scrollTo(crumbScroll.maxValue)
    }
    val allVisibleSelected = items.isNotEmpty() &&
        items.all { selection.contains(it.path) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HonorColors.Background)
    ) {
        if (selectionMode) {
            // Selection top bar: ✕ + count + select-all glyph.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExitSelection) {
                    Icon(
                        HonorIcons.Close,
                        contentDescription = "Exit selection",
                        tint = HonorColors.OnSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    if (selection.isEmpty()) {
                        "None selected"
                    } else {
                        "${selection.size} item${if (selection.size == 1) "" else "s"} selected"
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = HonorColors.OnSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSelectAll) {
                    Icon(
                        HonorIcons.selectAllGlyph(allVisibleSelected),
                        contentDescription = "Select all",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else {
            // Normal top bar: back + "Phone" (always) + new-folder + funnel + ⋮.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        HonorIcons.ChevronLeft,
                        contentDescription = "Back",
                        tint = HonorColors.OnSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    "Phone",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = HonorColors.OnSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNewFolder) {
                    Icon(
                        HonorIcons.FolderPlus,
                        contentDescription = "New folder",
                        tint = HonorColors.OnSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = onSortClick) {
                    Icon(
                        HonorIcons.funnel(),
                        contentDescription = "Sort",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onListMenuClick) {
                    Icon(
                        HonorIcons.MoreVert,
                        contentDescription = "More options",
                        tint = HonorColors.OnSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        HonorSearchPill(
            query = searchQuery,
            onQueryChange = onSearchChange,
            focusRequester = searchFocusRequester
        )

        if (tagFilter != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(HonorColors.SearchPill)
                        .clickable(onClick = onClearTagFilter)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Tag: $tagFilter ✕",
                        fontSize = 14.sp,
                        color = HonorColors.AccentBlue
                    )
                }
            }
        }

        // Breadcrumb row (§5.3): ancestors blue, current white, "›" grey.
        // Middle segments truncate with a LEADING ellipsis ("…obile.legends").
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(crumbScroll)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Phone",
                fontSize = 17.sp,
                color = if (isRoot) HonorColors.OnSurface else HonorColors.AccentBlue,
                modifier = Modifier.clickable { onNavigateTo(FileRepository.STORAGE_ROOT) }
            )
            segments.forEachIndexed { index, segment ->
                val target = FileRepository.STORAGE_ROOT + "/" +
                    segments.subList(0, index + 1).joinToString("/")
                val isCurrent = index == segments.lastIndex
                Text(" › ", fontSize = 17.sp, color = HonorColors.Muted)
                val display = if (!isCurrent && segment.length > 14) {
                    "…" + segment.takeLast(12)
                } else {
                    segment
                }
                Text(
                    display,
                    fontSize = 17.sp,
                    maxLines = 1,
                    color = if (isCurrent) HonorColors.OnSurface else HonorColors.AccentBlue,
                    modifier = Modifier.clickable { onNavigateTo(target) }
                )
            }
        }

        if (clipboard != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${clipboard.paths.size} item(s) ${if (clipboard.cut) "cut" else "copied"}",
                    fontSize = 15.sp,
                    color = HonorColors.OnSurface,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onPaste) {
                    Text("Paste here", color = HonorColors.AccentBlue)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onPasteToMlbb) {
                    Text("Paste to MLBB", color = HonorColors.AccentBlue)
                }
            }
        }

        if (error != null) {
            Text(
                error,
                fontSize = 14.sp,
                color = Color(0xFFEF4444),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CenteredListMessage(
                    text = "Loading... Please wait.",
                    showSpinner = true
                )
                items.isEmpty() -> if (isRestricted) {
                    BlockedFolderMessage(
                        onOpenMlbbData = onOpenMlbbData,
                        onOpenMlbbObb = onOpenMlbbObb,
                        onOpenInFiles = onOpenInFiles,
                        // A jump to the folder we're already in (or inside of)
                        // just reloads this same blocked screen — hide it.
                        showMlbbData = currentPath != FileRepository.MLBB_DATA &&
                            !currentPath.startsWith(FileRepository.MLBB_DATA + "/"),
                        showMlbbObb = currentPath != FileRepository.MLBB_OBB &&
                            !currentPath.startsWith(FileRepository.MLBB_OBB + "/")
                    )
                } else {
                    CenteredListMessage(
                        text = "Empty here — or the folder is blocked."
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .honorScrollbar(listState, scrollbarAlpha)
                ) {
                    items(items, key = { it.path }) { item ->
                        Column {
                            HonorRow(
                                item = item,
                                folderCount = folderCounts[item.path],
                                appIcon = if (item.isDir) appIcons[item.name] else null,
                                isFavourite = favouritePaths.contains(item.path),
                                hasTags = tagStore.tagsFor(item.path).isNotEmpty(),
                                selectionMode = selectionMode,
                                selected = selection.contains(item.path),
                                onClick = {
                                    if (selectionMode) onToggleSelect(item.path) else onOpen(item)
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        onToggleSelect(item.path)
                                    }
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(start = 72.dp)
                                    .background(HonorColors.Divider)
                            )
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            // Bottom action bar (§6): Share / Tags / Move / Delete / More.
            // Greyed out (disabled) while "None selected".
            val enabled = selection.isNotEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HonorColors.Background)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SelectionBarItem(HonorIcons.Share, "Share", enabled, onShare)
                SelectionBarItem(HonorIcons.bookmarkOutline(), "Tags", enabled, onTags)
                SelectionBarItem(HonorIcons.moveIcon(), "Move", enabled, onMove)
                SelectionBarItem(HonorIcons.Delete, "Delete", enabled, onDelete)
                SelectionBarItem(HonorIcons.MoreVert, "More", enabled, onMore)
            }
        }
    }
}

@Composable
private fun SelectionBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) HonorColors.OnSurface else HonorColors.InactiveGrey,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = if (enabled) HonorColors.OnSurface else HonorColors.InactiveGrey
        )
    }
}

@Composable
private fun CenteredListMessage(text: String, showSpinner: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSpinner) {
                CircularProgressIndicator(color = HonorColors.AccentBlue)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text,
                fontSize = 15.sp,
                color = HonorColors.Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

/**
 * Dead-end screen for a restricted folder (Android/data, Android/obb).
 * Android itself blocks LISTING these folders — no app can enumerate
 * them — but a known path below them (the MLBB folder) lists and
 * copies fine on this phone (proven Nov 27: 45 MB into assets). So
 * instead of a dead end, offer the jump straight into MLBB.
 */
@Composable
private fun BlockedFolderMessage(
    onOpenMlbbData: () -> Unit,
    onOpenMlbbObb: () -> Unit,
    onOpenInFiles: () -> Unit,
    showMlbbData: Boolean = true,
    showMlbbObb: Boolean = true
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                "Empty here — or the folder is blocked.",
                fontSize = 15.sp,
                color = HonorColors.Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Android won't list this folder, but the Files app can open it and show everything inside.",
                fontSize = 13.sp,
                color = HonorColors.Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(onClick = onOpenInFiles, modifier = Modifier.fillMaxWidth()) {
                Text("Open in Files app", color = HonorColors.OnSurface)
            }
            if (showMlbbData) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenMlbbData, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Mobile Legends data", color = HonorColors.OnSurface)
                }
            }
            if (showMlbbObb) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenMlbbObb, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Mobile Legends obb", color = HonorColors.OnSurface)
                }
            }
        }
    }
}

/** One Honor list row (§5.4): icon + name (max 2 lines, wraps) + sub-line
 *  + chevron, or a radio circle in selection mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HonorRow(
    item: FileItem,
    folderCount: Int?,
    appIcon: ImageBitmap?,
    thumb: ImageBitmap? = null,
    isFavourite: Boolean,
    hasTags: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    subLineOverride: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            when {
                thumb != null -> Image(
                    thumb,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                appIcon != null -> Image(
                    appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                item.isDir -> Icon(
                    HonorIcons.folderYellow(),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(48.dp)
                )
                else -> Icon(
                    HonorIcons.docIcon(HonorIcons.docTypeFor(item.name)),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                )
            }
            // A folder literally named "android" gets the gear badge (§5.4).
            if (item.isDir && item.name.equals("android", ignoreCase = true)) {
                GearBadge(modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    fontSize = 17.sp,
                    color = if (selectionMode && !selected) HonorColors.DimmedText
                    else HonorColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (hasTags) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        HonorIcons.bookmarkOutline(),
                        contentDescription = "Tagged",
                        tint = HonorColors.AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                subLineOverride ?: subLine(item, folderCount),
                fontSize = 14.sp,
                color = if (selectionMode && !selected) HonorColors.DimmedText
                else HonorColors.Muted
            )
        }
        if (isFavourite) {
            Icon(
                HonorIcons.Star,
                contentDescription = "Favourite",
                tint = HonorColors.FolderYellow,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (selectionMode) {
            Icon(
                if (selected) HonorIcons.checkCircleBlue() else HonorIcons.radioRing(),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Icon(
                HonorIcons.ChevronRight,
                contentDescription = null,
                tint = HonorColors.Muted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun subLine(item: FileItem, folderCount: Int?): String {
    return if (item.isDir) {
        val date = formatDateRelative(item.lastModified)
        if (folderCount != null) "$date - ($folderCount)" else date
    } else {
        "${formatSize(item.size)} • ${formatDateRelative(item.lastModified)}"
    }
}

/** Category view (§7): Honor top bar + search pill + Honor rows.
 *  Media categories show real thumbnails; Apps shows the app icon and
 *  the package name as the sub-line. */
@Composable
private fun CategoryScreen(
    title: String,
    items: List<FileItem>,
    loading: Boolean,
    note: String?,
    thumbs: Map<String, ImageBitmap>,
    appIcons: Map<String, ImageBitmap>,
    isApps: Boolean,
    onItemClick: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    var query by remember(title) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val visible = remember(items, query) {
        if (query.isBlank()) items
        else items.filter { it.name.contains(query, ignoreCase = true) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HonorColors.Background)
    ) {
        HonorTopBar(title = title, onBack = onBack)
        HonorSearchPill(
            query = query,
            onQueryChange = { query = it },
            focusRequester = focusRequester
        )
        if (note != null) {
            Text(
                note,
                fontSize = 13.sp,
                color = HonorColors.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CenteredListMessage(
                    text = "Loading... Please wait.",
                    showSpinner = true
                )
                visible.isEmpty() -> CenteredListMessage(
                    text = if (query.isBlank()) "Empty." else "No match for \"$query\"."
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.path }) { item ->
                        Column {
                            HonorRow(
                                item = item,
                                folderCount = null,
                                appIcon = if (isApps) appIcons[item.path] else null,
                                thumb = thumbs[item.path],
                                isFavourite = false,
                                hasTags = false,
                                selectionMode = false,
                                selected = false,
                                subLineOverride = if (isApps) item.path else null,
                                onClick = { onItemClick(item) },
                                onLongClick = {}
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(start = 72.dp)
                                    .background(HonorColors.Divider)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Recent tab (§7): MediaStore query, tap opens the parent folder. */
@Composable
private fun RecentScreen(
    items: List<FileItem>,
    loading: Boolean,
    selectedTab: HomeTab,
    onTabClick: (HomeTab) -> Unit,
    onItemClick: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HonorColors.Background)
    ) {
        HonorTopBar(title = "Recent", onBack = onBack)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> CenteredListMessage(
                    text = "Loading... Please wait.",
                    showSpinner = true
                )
                items.isEmpty() -> CenteredListMessage(text = "No recent files.")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.path }) { item ->
                        Column {
                            HonorRow(
                                item = item,
                                folderCount = null,
                                appIcon = null,
                                isFavourite = false,
                                hasTags = false,
                                selectionMode = false,
                                selected = false,
                                onClick = { onItemClick(item) },
                                onLongClick = {}
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(start = 72.dp)
                                    .background(HonorColors.Divider)
                            )
                        }
                    }
                }
            }
        }
        HonorBottomNav(selectedTab = selectedTab, onTabClick = onTabClick)
    }
}

private data class MenuItemSpec(
    val label: String,
    val checked: Boolean? = null,
    val onClick: () -> Unit
)

/** Honor popup menu (§6): #404040 card, ~20dp radius, ~60% screen width,
 *  17sp items, hairline separators. Tap outside dismisses. */
@Composable
private fun HonorPopupMenu(
    items: List<MenuItemSpec>,
    anchorBottom: Boolean = false,
    onDismiss: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = if (anchorBottom) Alignment.BottomEnd else Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .padding(
                    top = if (anchorBottom) 0.dp else 96.dp,
                    bottom = if (anchorBottom) 96.dp else 0.dp,
                    end = 16.dp
                )
                .width(screenWidth * 0.6f)
                .clip(RoundedCornerShape(20.dp))
                .background(HonorColors.MenuCard)
        ) {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = item.onClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.label,
                        fontSize = 17.sp,
                        color = HonorColors.OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.checked == true) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = HonorColors.OnSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (index < items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 16.dp)
                            .background(HonorColors.Divider)
                    )
                }
            }
        }
    }
}

/** Sort bottom sheet (§5.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current: SortMode,
    onSelect: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HonorColors.Card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Sort by",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = HonorColors.OnSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            val options = listOf(
                SortMode.NAME to "Name (A–Z)",
                SortMode.DATE to "Date (newest first)",
                SortMode.SIZE to "Size (largest first)",
                SortMode.TYPE to "Type (folders first, then by extension)"
            )
            options.forEach { (mode, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(mode) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        fontSize = 17.sp,
                        color = HonorColors.OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (mode == current) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = HonorColors.AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(HonorColors.Divider)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Tags dialog (§8): current tags as removable chips, add field, and a
 *  tag-filter row (tapping a tag filters the current list). */
@Composable
private fun TagsDialog(
    tags: Set<String>,
    allFolderTags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onFilterTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HonorColors.Card,
        titleContentColor = HonorColors.OnSurface,
        textContentColor = HonorColors.Muted,
        title = { Text("Tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            TagChip(label = tag, onRemove = { onRemoveTag(tag) })
                        }
                    }
                } else {
                    Text("Your selection has no tags.", color = HonorColors.Muted)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("Bagong tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            if (newTag.isNotBlank()) {
                                onAddTag(newTag.trim())
                                newTag = ""
                            }
                        },
                        enabled = newTag.isNotBlank()
                    ) { Text("Add", color = HonorColors.AccentBlue) }
                }
                if (allFolderTags.isNotEmpty()) {
                    Text("I-filter sa tag:", color = HonorColors.Muted)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allFolderTags.forEach { tag ->
                            TagChip(label = tag, onClick = { onFilterTag(tag) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = HonorColors.AccentBlue)
            }
        }
    )
}

@Composable
private fun TagChip(
    label: String,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(HonorColors.SearchPill)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, color = HonorColors.OnSurface)
            if (onRemove != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "✕",
                    fontSize = 14.sp,
                    color = HonorColors.Muted,
                    modifier = Modifier.clickable { onRemove() }
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(target: FileItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(target.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HonorColors.Card,
        titleContentColor = HonorColors.OnSurface,
        textContentColor = HonorColors.Muted,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Bagong pangalan") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && name != target.name) onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name != target.name
            ) { Text("Rename", color = HonorColors.AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = HonorColors.AccentBlue) }
        }
    )
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HonorColors.Card,
        titleContentColor = HonorColors.OnSurface,
        textContentColor = HonorColors.Muted,
        title = { Text("Bagong folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Pangalan ng folder") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Gumawa", color = HonorColors.AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = HonorColors.AccentBlue) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(item: FileItem, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HonorColors.Card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleMedium,
                color = HonorColors.OnSurface
            )
            Text("Path: ${item.path}", color = HonorColors.Muted)
            Text("Uri: ${if (item.isDir) "Folder" else "File"}", color = HonorColors.Muted)
            if (!item.isDir) Text("Laki: ${formatSize(item.size)}", color = HonorColors.Muted)
            Text("Binago: ${formatDateRelative(item.lastModified)}", color = HonorColors.Muted)
            Text("Binuksan via: ${item.via}", color = HonorColors.Muted)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Copy-progress dialog (§7), restyled black. Non-dismissible while copying. */
@Composable
private fun CopyProgressDialog(label: String, copied: Long, total: Long) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = HonorColors.Card,
        titleContentColor = HonorColors.OnSurface,
        textContentColor = HonorColors.Muted,
        title = { Text("Copying…") },
        text = {
            Column {
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = HonorColors.OnSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                        color = HonorColors.AccentBlue,
                        trackColor = HonorColors.Divider,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${formatSize(copied)} / ${formatSize(total)}", color = HonorColors.Muted)
                } else {
                    LinearProgressIndicator(
                        color = HonorColors.AccentBlue,
                        trackColor = HonorColors.Divider,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Calculating size…", color = HonorColors.Muted)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Don't close the app while copying. " +
                        "These are large OBB files (GBs) — streamed, never loaded fully into memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HonorColors.Muted
                )
            }
        },
        confirmButton = {}
    )
}

/** Thin Honor scrollbar (§3.10): 4dp, #757477, right edge. Drawn from the
 *  list state — track + thumb — with the alpha driven by scroll idleness. */
private fun Modifier.honorScrollbar(listState: LazyListState, alpha: Float): Modifier =
    this.drawWithContent {
        drawContent()
        if (alpha <= 0f) return@drawWithContent
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        if (total <= 0) return@drawWithContent
        val visible = info.visibleItemsInfo
        if (visible.isEmpty()) return@drawWithContent
        val viewportH = size.height
        val avgH = visible.sumOf { it.size }.toFloat() / visible.size
        val totalH = avgH * total
        if (totalH <= viewportH) return@drawWithContent
        val thumbH = (viewportH / totalH * viewportH).coerceAtLeast(48f)
        val scrolled = (visible.first().index * avgH - visible.first().offset)
            .coerceAtLeast(0f)
        val scrollRange = (totalH - viewportH).coerceAtLeast(1f)
        val thumbTop = ((scrolled / scrollRange) * (viewportH - thumbH))
            .coerceIn(0f, viewportH - thumbH)
        val barColor = Color(0xFF757477)
        drawRoundRect(
            color = barColor.copy(alpha = 0.25f * alpha),
            topLeft = Offset(size.width - 6f, 0f),
            size = UiSize(4f, viewportH),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = barColor.copy(alpha = 0.6f * alpha),
            topLeft = Offset(size.width - 6f, thumbTop),
            size = UiSize(4f, thumbH),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }
