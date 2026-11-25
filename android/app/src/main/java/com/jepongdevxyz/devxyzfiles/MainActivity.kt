package com.jepongdevxyz.devxyzfiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HOME_PATH = "home"

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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppContext.init(applicationContext)
        ShizukuManager.attach(applicationContext)
        setContent {
            DevxyzFilesTheme {
                DevxyzFilesApp()
            }
        }
    }

    override fun onDestroy() {
        ShizukuManager.detach()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevxyzFilesApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { FileRepository(context.applicationContext) }
    val shizukuState by ShizukuManager.state.collectAsState()

    var currentPath by remember { mutableStateOf(HOME_PATH) }
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<Clipboard?>(null) }
    var restricted by remember { mutableStateOf<RestrictedStatus?>(null) }
    var mlbb by remember { mutableStateOf<MlbbStatus?>(null) }

    var showWizard by remember {
        val seen = context.getSharedPreferences("devxyzfiles_prefs", Context.MODE_PRIVATE)
            .getBoolean("setup_seen", false)
        mutableStateOf(!seen)
    }
    var showDetails by remember { mutableStateOf<FileItem?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var copyProgress by remember { mutableStateOf<Triple<String, Long, Long>?>(null) }

    val appIcons = remember { mutableStateMapOf<String, ImageBitmap>() }
    var mlbbIcon by remember { mutableStateOf<ImageBitmap?>(null) }

    // Declared BEFORE the SAF launcher below: Kotlin local functions must
    // be declared before their first use, and the launcher's callback calls
    // loadFolder() (CI Run #2: unresolved reference at the old position).
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
                        "Blocked: hindi mabuksan ang folder na ito. " +
                            "Kailangan ng Shizuku (tingnan ang Setup) o SAF grant."
                    else -> "Hindi ma-list: ${e.message}"
                }
            } finally {
                loading = false
            }
        }
    }

    fun runProbes() {
        scope.launch {
            restricted = repository.probeRestricted()
            mlbb = repository.probeMlbb()
        }
    }

    // SAF folder-picker launcher, shared with the Setup Wizard.
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            SafHelper.takePermission(context, uri)
            message = "SAF grant saved: ${SafHelper.treePath(uri) ?: uri}"
            scope.launch {
                restricted = repository.probeRestricted()
                mlbb = repository.probeMlbb()
                if (currentPath != HOME_PATH) loadFolder()
            }
        }
    }
    SafPickerLauncher.current = { path ->
        safLauncher.launch(initialTreeUri(path))
    }

    // Initial + resume refresh: Shizuku can die on reboot, so re-check live.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ShizukuManager.refresh(context)
                if (ShizukuManager.state.value.binderAlive &&
                    ShizukuManager.state.value.permissionGranted &&
                    !ShizukuManager.state.value.serviceBound
                ) {
                    ShizukuManager.bindService()
                }
                runProbes()
                if (currentPath != HOME_PATH) loadFolder()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentPath) {
        selection = emptySet()
        searchQuery = ""
        if (currentPath != HOME_PATH) loadFolder()
    }

    // App icons for package-named folders under Android/data and Android/obb.
    LaunchedEffect(items, currentPath) {
        val parent = currentPath
        if (parent != FileRepository.RESTRICTED_DATA && parent != FileRepository.RESTRICTED_OBB) return@LaunchedEffect
        val candidates = items.filter { it.isDir && it.name.contains('.') && !appIcons.containsKey(it.name) }
        for (item in candidates) {
            val icon = withContext(Dispatchers.IO) { loadAppIcon(context, item.name) }
            if (icon != null) appIcons[item.name] = icon
        }
    }

    // MLBB launcher icon for the pinned home card.
    LaunchedEffect(Unit) {
        mlbbIcon = withContext(Dispatchers.IO) {
            loadAppIcon(context, FileRepository.MLBB_PACKAGE)
        }
    }

    BackHandler(enabled = currentPath != HOME_PATH) {
        val parent = parentOf(currentPath)
        currentPath = parent ?: HOME_PATH
    }

    fun openPath(path: String) {
        currentPath = normalisePath(path)
    }

    fun doPaste() {
        val clip = clipboard ?: return
        val destDir = currentPath
        if (destDir == HOME_PATH) {
            message = "Pumili muna ng folder na paglalagyan."
            return
        }
        scope.launch {
            var done = 0
            var failed = 0
            for (src in clip.paths) {
                val dest = joinPath(destDir, fileNameOf(src))
                if (normalisePath(src) == normalisePath(dest)) {
                    failed++
                    continue
                }
                val ok = if (clip.cut) {
                    repository.move(src, dest)
                } else {
                    copyProgress = Triple(fileNameOf(src), 0L, 0L)
                    val result = repository.copyWithProgress(src, dest) { copied, total ->
                        // State writes hop back to the main thread.
                        scope.launch { copyProgress = Triple(fileNameOf(src), copied, total) }
                    }
                    copyProgress = null
                    result
                }
                if (ok) done++ else failed++
            }
            clipboard = null
            loadFolder()
            message = if (failed == 0) {
                "Tapos: $done file(s) na-${if (clip.cut) "move" else "copy"}."
            } else {
                "$done ok, $failed failed. Tingnan ang Setup kung restricted folder."
            }
        }
    }

    fun doDelete() {
        val targets = selection.toList()
        scope.launch {
            var failed = 0
            for (path in targets) {
                if (!repository.delete(path)) failed++
            }
            selection = emptySet()
            loadFolder()
            message = if (failed == 0) "Deleted: ${targets.size} item(s)." else "$failed item(s) hindi na-delete."
        }
    }

    val statusText = when {
        ShizukuManager.isReady() -> "Shizuku • connected"
        shizukuState.binderAlive -> "Shizuku • hindi naka-grant"
        shizukuState.installed -> "Shizuku • hindi tumatakbo"
        else -> "Direct lang — walang Shizuku"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DevxyzFiles") },
                actions = {
                    IconButton(onClick = {
                        if (currentPath != HOME_PATH) loadFolder() else runProbes()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showWizard = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Setup")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            // Status pill — always visible, always live.
            Text(
                "$statusText  •  data: ${restricted?.data?.let { if (it.accessible) "OK (${it.count})" else "blocked" } ?: "…"}  •  obb: ${restricted?.obb?.let { if (it.accessible) "OK (${it.count})" else "blocked" } ?: "…"}",
                style = MaterialTheme.typography.bodySmall
            )
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            if (message != null) {
                Text(message!!, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (currentPath == HOME_PATH) {
                HomeScreen(
                    restricted = restricted,
                    mlbb = mlbb,
                    mlbbIcon = mlbbIcon,
                    onOpenPath = ::openPath,
                    onOpenSetup = { showWizard = true }
                )
            } else {
                BrowserScreen(
                    currentPath = currentPath,
                    items = items,
                    loading = loading,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    selection = selection,
                    onToggleSelect = { path ->
                        selection = if (selection.contains(path)) selection - path else selection + path
                    },
                    onOpen = { item ->
                        if (item.isDir) openPath(item.path) else showDetails = item
                    },
                    onOpenParent = {
                        currentPath = parentOf(currentPath) ?: HOME_PATH
                    },
                    onGoHome = { currentPath = HOME_PATH },
                    onNavigateTo = ::openPath,
                    appIcons = appIcons,
                    clipboard = clipboard,
                    onPaste = ::doPaste,
                    onCopy = {
                        if (selection.isNotEmpty()) {
                            clipboard = Clipboard(selection.toList(), cut = false)
                            message = "${selection.size} item(s) naka-copy — pumili ng folder tapos Paste."
                            selection = emptySet()
                        }
                    },
                    onCut = {
                        if (selection.isNotEmpty()) {
                            clipboard = Clipboard(selection.toList(), cut = true)
                            message = "${selection.size} item(s) naka-cut — pumili ng folder tapos Paste."
                            selection = emptySet()
                        }
                    },
                    onDelete = { showDeleteConfirm = true },
                    onRename = {
                        renameTarget = items.firstOrNull { it.path == selection.firstOrNull() }
                    },
                    onDetails = {
                        showDetails = items.firstOrNull { it.path == selection.firstOrNull() }
                    },
                    onNewFolder = { showNewFolder = true }
                )
            }
        }
    }

    if (showWizard) {
        SetupWizardDialog(
            repository = repository,
            shizukuState = shizukuState,
            restricted = restricted,
            mlbb = mlbb,
            onRunTest = { runProbes() },
            onOpenPath = { path ->
                showWizard = false
                context.getSharedPreferences("devxyzfiles_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("setup_seen", true).apply()
                openPath(path)
            },
            onDismiss = {
                showWizard = false
                context.getSharedPreferences("devxyzfiles_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("setup_seen", true).apply()
            }
        )
    }

    if (copyProgress != null) {
        val (label, copied, total) = copyProgress!!
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Nagko-copy…") },
            text = {
                Column {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${formatSize(copied)} / ${formatSize(total)}")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Kinakalkula ang laki…")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Huwag isara ang app habang nagko-copy. " +
                            "Malalaking OBB ito (GBs) — streaming lang, hindi buo sa memory.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("I-delete?") },
            text = { Text("Mabubura ang ${selection.size} item(s). Hindi na ito maibabalik.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    doDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
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
                    val ok = repository.rename(target.path, joinPath(parentOf(target.path) ?: FileRepository.STORAGE_ROOT, newName))
                    message = if (ok) "Na-rename na." else "Hindi na-rename."
                    loadFolder()
                }
            }
        )
    }

    if (showNewFolder) {
        NewFolderDialog(
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                scope.launch {
                    val ok = repository.mkdir(joinPath(currentPath, name))
                    message = if (ok) "Folder na-gawa na." else "Hindi magawa ang folder."
                    loadFolder()
                }
            }
        )
    }

    showDetails?.let { item ->
        DetailsSheet(item = item, onDismiss = { showDetails = null })
    }
}

private fun initialTreeUri(path: String): Uri {
    val relative = normalisePath(path).removePrefix("/storage/emulated/0/")
    return Uri.parse(
        "content://com.android.externalstorage.documents/tree/primary%3A" +
            Uri.encode(relative)
    )
}

@Composable
private fun HomeScreen(
    restricted: RestrictedStatus?,
    mlbb: MlbbStatus?,
    mlbbIcon: ImageBitmap?,
    onOpenPath: (String) -> Unit,
    onOpenSetup: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Storage", style = MaterialTheme.typography.titleMedium)
        HomeRow(
            icon = Icons.Filled.Storage,
            label = "Internal Storage",
            sub = "/storage/emulated/0",
            onClick = { onOpenPath(FileRepository.STORAGE_ROOT) }
        )
        HomeRow(
            icon = Icons.Filled.Folder,
            label = "Android/data",
            sub = restricted?.data?.let {
                if (it.accessible) "OK — ${it.count} apps (${it.via})" else "Blocked (${it.via})"
            } ?: "Tine-test…",
            onClick = { onOpenPath(FileRepository.RESTRICTED_DATA) }
        )
        HomeRow(
            icon = Icons.Filled.Folder,
            label = "Android/obb",
            sub = restricted?.obb?.let {
                if (it.accessible) "OK — ${it.count} apps (${it.via})" else "Blocked (${it.via})"
            } ?: "Tine-test…",
            onClick = { onOpenPath(FileRepository.RESTRICTED_OBB) }
        )
        HomeRow(
            icon = Icons.Filled.Download,
            label = "Download",
            sub = "Downloads folder",
            onClick = { onOpenPath("/storage/emulated/0/Download") }
        )
        HomeRow(
            icon = Icons.Filled.Image,
            label = "DCIM (Camera)",
            sub = "Photos at videos",
            onClick = { onOpenPath("/storage/emulated/0/DCIM") }
        )
        HomeRow(
            icon = Icons.Filled.Movie,
            label = "Pictures",
            sub = "Pictures folder",
            onClick = { onOpenPath("/storage/emulated/0/Pictures") }
        )

        Text("Mobile Legends — pinned", style = MaterialTheme.typography.titleMedium)
        MlbbCard(
            mlbb = mlbb,
            icon = mlbbIcon,
            onOpenData = { onOpenPath(FileRepository.MLBB_DATA) },
            onOpenObb = { onOpenPath(FileRepository.MLBB_OBB) }
        )

        OutlinedButton(onClick = onOpenSetup) { Text("Buksan ang Setup / Shizuku Guide") }
        Text(
            "Paalala: /data/data/com.mobile.legends ay HINDI mabubuksan kahit " +
                "via Shizuku — kailangan ng root doon. Android/data at " +
                "Android/obb lang ang kaya ng DevxyzFiles.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun HomeRow(icon: ImageVector, label: String, sub: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(label)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MlbbCard(
    mlbb: MlbbStatus?,
    icon: ImageBitmap?,
    onOpenData: () -> Unit,
    onOpenObb: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(icon, contentDescription = "Mobile Legends icon", modifier = Modifier.size(40.dp))
            } else {
                Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Mobile Legends", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (mlbb?.installed == true) "Naka-install" else "Hindi pa naka-install / hindi makita",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        OutlinedButton(onClick = onOpenData, modifier = Modifier.fillMaxWidth()) {
            Text(
                "MLBB data — " + (mlbb?.data?.let {
                    if (it.accessible) "${it.count} items (${it.via})" else "blocked"
                } ?: "…")
            )
        }
        OutlinedButton(onClick = onOpenObb, modifier = Modifier.fillMaxWidth()) {
            Text(
                "MLBB obb — " + (mlbb?.obb?.let {
                    if (it.accessible) "${it.count} items (${it.via})" else "blocked"
                } ?: "…")
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                "  /data/data — hindi kaya (root lang)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowserScreen(
    currentPath: String,
    items: List<FileItem>,
    loading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selection: Set<String>,
    onToggleSelect: (String) -> Unit,
    onOpen: (FileItem) -> Unit,
    onOpenParent: () -> Unit,
    onGoHome: () -> Unit,
    onNavigateTo: (String) -> Unit,
    appIcons: Map<String, ImageBitmap>,
    clipboard: Clipboard?,
    onPaste: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onDetails: () -> Unit,
    onNewFolder: () -> Unit
) {
    val visible = if (searchQuery.isBlank()) {
        items
    } else {
        items.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenParent) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
        }
        IconButton(onClick = onGoHome) {
            Icon(Icons.Filled.Home, contentDescription = "Home")
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            val segments = normalisePath(currentPath).split('/').filter { it.isNotEmpty() }
            var acc = ""
            segments.forEach { seg ->
                acc += "/$seg"
                val target = acc
                TextButton(onClick = { onNavigateTo(target) }) { Text(seg) }
            }
        }
        IconButton(onClick = onNewFolder) {
            Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder")
        }
    }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        label = { Text("Search dito sa folder") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (selection.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopy) { Text("Copy (${selection.size})") }
            OutlinedButton(onClick = onCut) { Text("Cut") }
            OutlinedButton(onClick = onDelete) { Text("Delete") }
            if (selection.size == 1) {
                OutlinedButton(onClick = onRename) { Text("Rename") }
                OutlinedButton(onClick = onDetails) { Text("Details") }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    if (clipboard != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${clipboard.paths.size} item(s) naka-${if (clipboard.cut) "cut" else "copy"}",
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onPaste) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Paste dito")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (loading) {
        CircularProgressIndicator()
        return
    }
    if (visible.isEmpty()) {
        Text("Walang laman dito — o blocked ang folder. Tingnan ang Setup.")
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(visible, key = { it.path }) { item ->
            val selected = selection.contains(item.path)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (selection.isNotEmpty()) onToggleSelect(item.path) else onOpen(item)
                        },
                        onLongClick = { onToggleSelect(item.path) }
                    )
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconBitmap = if (item.isDir) appIcons[item.name] else null
                if (iconBitmap != null) {
                    Image(iconBitmap, contentDescription = null, modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        if (item.isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (item.isDir) "Folder • ${item.via}" else "${formatSize(item.size)} • ${item.via}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (item.lastModified > 0) {
                        Text(formatDate(item.lastModified), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (selected) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Selected")
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(target: FileItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(target.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
            Button(
                onClick = { if (name.isNotBlank() && name != target.name) onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name != target.name
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Gumawa") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(item: FileItem, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text("Path: ${item.path}")
            Text("Uri: ${if (item.isDir) "Folder" else "File"}")
            if (!item.isDir) Text("Laki: ${formatSize(item.size)}")
            Text("Binago: ${formatDate(item.lastModified)}")
            Text("Binuksan via: ${item.via}")
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
