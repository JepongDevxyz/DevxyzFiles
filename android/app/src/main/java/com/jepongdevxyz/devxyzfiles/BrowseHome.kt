package com.jepongdevxyz.devxyzfiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Browse home + supporting presentational screens (v1.2.0) — Honor Files
 * layout, frame by frame. PRESENTATIONAL ONLY: every screen takes data
 * in and reports taps out via lambdas. No repository, no navigation,
 * no SAF calls here — Agent 2 wires these into MainActivity after the
 * v1.1.0 push lands.
 *
 * Deliberate deviations from the reference (documented, not mistakes):
 * - SOURCE card shows REAL sources (Mobile Legends, Camera, Recorder,
 *   + user-added folders via "Set as source"). Honor Share and Wi-Fi
 *   Direct are omitted: they are Honor system services a third-party
 *   app cannot invoke, and no visible control may be dead.
 * - No "Safe" row (encrypted vault is out of scope for v1.2.0).
 * - The pinned MLBB card from v1.1.0 is gone; MLBB lives in the SOURCE
 *   card (one slot — tapping it opens [MlbbSourceSheet] with data/obb).
 * - /data/data honest note stays at the bottom of Browse home.
 */
enum class HomeTab { RECENT, MY_FILES, BROWSE }

enum class Category { IMAGES, VIDEOS, DOCUMENTS, AUDIO, APPS, ARCHIVES }

enum class SourceKind { APP, FOLDER, CAMERA, RECORDER }

data class SourceItem(
    val label: String,
    val path: String,
    val kind: SourceKind,
    /** App launcher icon when kind == APP (e.g. MLBB). Null otherwise. */
    val appIcon: ImageBitmap? = null
)

@Composable
fun BrowseHome(
    phoneLabel: String,
    recentlyDeletedCount: Int,
    favourites: List<FileItem>,
    sources: List<SourceItem>,
    selectedTab: HomeTab,
    onTabClick: (HomeTab) -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: (Category) -> Unit,
    onSourceClick: (SourceItem) -> Unit,
    onAllSourcesClick: () -> Unit,
    onPhoneClick: () -> Unit,
    onCleanUpClick: () -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onFavouriteClick: (FileItem) -> Unit,
    onAddClick: () -> Unit,
    onOverflowClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(HonorColors.Background)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            item {
                // Title row: big "Browse" + "+" + "⋮".
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Browse",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = HonorColors.OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onAddClick) {
                        Icon(HonorIcons.Add, contentDescription = "New folder", tint = HonorColors.OnSurface)
                    }
                    IconButton(onClick = onOverflowClick) {
                        Icon(HonorIcons.MoreVert, contentDescription = "More options", tint = HonorColors.OnSurface)
                    }
                }
                // Search pill.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(HonorColors.SearchPill)
                        .clickable(onClick = onSearchClick),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                        Icon(HonorIcons.Search, contentDescription = null, tint = HonorColors.Muted)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Search", fontSize = 16.sp, color = HonorColors.Muted)
                    }
                }
                // 3-col category grid.
                CategoryGrid(onCategoryClick = onCategoryClick)
                // SOURCE.
                SectionHeader(label = "SOURCE", actionLabel = "All ›", onActionClick = onAllSourcesClick)
                SourcesCard(sources = sources, onSourceClick = onSourceClick)
                // LOCAL STORAGE.
                SectionHeader(label = "LOCAL STORAGE", actionLabel = "Clean up", actionBlue = true, onActionClick = onCleanUpClick)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(HonorColors.Card)
                        .clickable(onClick = onPhoneClick)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(HonorIcons.phoneOutline(), contentDescription = null, tint = HonorColors.OnSurface, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Phone", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = HonorColors.OnSurface)
                            Text(phoneLabel, fontSize = 14.sp, color = HonorColors.Muted)
                        }
                        Icon(HonorIcons.ChevronRight, contentDescription = null, tint = HonorColors.Muted)
                    }
                }
                // FAVOURITES — only when non-empty.
                if (favourites.isNotEmpty()) {
                    SectionHeader(label = "FAVOURITES", actionLabel = null, onActionClick = {})
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(HonorColors.Card)
                    ) {
                        Column {
                            favourites.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onFavouriteClick(item) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isDir) HonorIcons.folderYellow()
                                        else HonorIcons.docIcon(HonorIcons.docTypeFor(item.name)),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        item.name,
                                        fontSize = 16.sp,
                                        color = HonorColors.OnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(HonorIcons.Star, contentDescription = "Favourite", tint = HonorColors.FolderYellow, modifier = Modifier.size(20.dp))
                                }
                                if (index < favourites.lastIndex) {
                                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(start = 60.dp).background(HonorColors.Divider))
                                }
                            }
                        }
                    }
                }
                // LOCATION — Recently deleted only (Safe is out of scope).
                SectionHeader(label = "LOCATION", actionLabel = null, onActionClick = {})
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(HonorColors.Card)
                        .clickable(onClick = onRecentlyDeletedClick)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(HonorIcons.Basket, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("Recently deleted", fontSize = 17.sp, color = HonorColors.OnSurface, modifier = Modifier.weight(1f))
                        Text("($recentlyDeletedCount)", fontSize = 15.sp, color = HonorColors.Muted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(HonorIcons.ChevronRight, contentDescription = null, tint = HonorColors.Muted)
                    }
                }
                // Honest /data/data note (kept from v1.1.0).
                Text(
                    "Paalala: /data/data/com.mobile.legends ay HINDI mabubuksan kahit via SAF — kailangan ng root doon. Android/data at Android/obb lang ang kaya ng DevxyzFiles.",
                    fontSize = 12.sp,
                    color = HonorColors.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        }
        HonorBottomNav(selectedTab = selectedTab, onTabClick = onTabClick)
    }
}

@Composable
private fun SectionHeader(label: String, actionLabel: String?, actionBlue: Boolean = false, onActionClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = HonorColors.Muted, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    actionLabel,
                    fontSize = 15.sp,
                    color = if (actionBlue) HonorColors.AccentBlue else HonorColors.OnSurface
                )
            }
        }
    }
}

@Composable
private fun CategoryGrid(onCategoryClick: (Category) -> Unit) {
    val cats = listOf(
        Triple(Category.IMAGES, "Images", HonorIcons.DocType.IMAGE),
        Triple(Category.VIDEOS, "Videos", HonorIcons.DocType.VIDEO),
        Triple(Category.DOCUMENTS, "Documents", HonorIcons.DocType.DOCUMENT),
        Triple(Category.AUDIO, "Audio", HonorIcons.DocType.AUDIO),
        Triple(Category.APPS, "Apps", HonorIcons.DocType.APP),
        Triple(Category.ARCHIVES, "Archives", HonorIcons.DocType.ARCHIVE)
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cats.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (cat, label, type) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(HonorColors.Card)
                            .clickable { onCategoryClick(cat) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = HonorIcons.docIcon(type),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(label, fontSize = 15.sp, color = HonorColors.OnSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesCard(sources: List<SourceItem>, onSourceClick: (SourceItem) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HonorColors.Card)
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            sources.forEach { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(76.dp)
                        .clickable { onSourceClick(item) }
                        .padding(vertical = 2.dp)
                ) {
                    when (item.kind) {
                        SourceKind.APP -> {
                            if (item.appIcon != null) {
                                Image(
                                    bitmap = item.appIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Icon(HonorIcons.folderYellow(), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(48.dp))
                            }
                        }
                        SourceKind.FOLDER -> Icon(HonorIcons.folderYellow(), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(48.dp))
                        SourceKind.CAMERA -> Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                        }
                        SourceKind.RECORDER -> Box(
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        item.label,
                        fontSize = 12.sp,
                        color = HonorColors.OnSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Bottom nav — Recent / My files / Browse. Only on top-level screens. */
@Composable
fun HonorBottomNav(selectedTab: HomeTab, onTabClick: (HomeTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HonorColors.Background)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomNavItem(
            icon = HonorIcons.Clock,
            label = "Recent",
            selected = selectedTab == HomeTab.RECENT,
            onClick = { onTabClick(HomeTab.RECENT) }
        )
        BottomNavItem(
            icon = HonorIcons.Doc,
            label = "My files",
            selected = selectedTab == HomeTab.MY_FILES,
            onClick = { onTabClick(HomeTab.MY_FILES) }
        )
        BottomNavItem(
            icon = HonorIcons.folderBlue(),
            label = "Browse",
            selected = selectedTab == HomeTab.BROWSE,
            onClick = { onTabClick(HomeTab.BROWSE) }
        )
    }
}

@Composable
private fun BottomNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) HonorColors.AccentBlue else HonorColors.InactiveGrey,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) HonorColors.AccentBlue else HonorColors.InactiveGrey
        )
    }
}

/** "All ›" — full list of persisted sources, with remove. */
@Composable
fun SourcesScreen(
    sources: List<SourceItem>,
    onSourceClick: (SourceItem) -> Unit,
    onRemoveSource: (SourceItem) -> Unit,
    onAddSourceClick: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(HonorColors.Background)) {
        HonorTopBar(title = "Sources", onBack = onBack)
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(sources, key = { it.path }) { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSourceClick(item) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(HonorIcons.folderYellow(), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, fontSize = 16.sp, color = HonorColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.path, fontSize = 12.sp, color = HonorColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onRemoveSource(item) }) {
                        Icon(HonorIcons.Close, contentDescription = "Remove source", tint = HonorColors.Muted)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(start = 60.dp).background(HonorColors.Divider))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedButton(onClick = onAddSourceClick, modifier = Modifier.fillMaxWidth()) {
                Text("Add a folder as source", color = HonorColors.AccentBlue)
            }
        }
    }
}

/** LOCATION > Recently deleted — restore or delete forever. */
@Composable
fun TrashScreen(
    records: List<TrashRecord>,
    onRestore: (TrashRecord) -> Unit,
    onDeleteForever: (TrashRecord) -> Unit,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(HonorColors.Background)) {
        HonorTopBar(title = "Recently deleted", onBack = onBack)
        if (records.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Walang laman — walang naka-delete.", fontSize = 15.sp, color = HonorColors.Muted)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEmptyTrash) {
                    Text("Empty trash", color = Color(0xFFEF4444), fontSize = 14.sp)
                }
            }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(records, key = { it.path }) { r ->
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(r.name, fontSize = 16.sp, color = HonorColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Deleted ${formatDateRelative(r.deletedAt)} • ${formatSize(r.size)}",
                            fontSize = 13.sp,
                            color = HonorColors.Muted
                        )
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onRestore(r) }) {
                                Text("Restore", color = HonorColors.AccentBlue)
                            }
                            OutlinedButton(onClick = { onDeleteForever(r) }) {
                                Text("Delete forever", color = Color(0xFFEF4444))
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HonorColors.Divider))
                }
            }
        }
    }
}

/** "Clean up" — largest files/folders on Phone, biggest first.
 *  [loading] shows the spinner while the scan runs; [partial] shows the
 *  "partial results" note when the scan hit its time cap (v1.2.0 wiring:
 *  the screen itself had no loading/partial state, so these params were
 *  added with defaults — no other caller changes). */
@Composable
fun CleanupScreen(
    largest: List<FileItem>,
    loading: Boolean = false,
    partial: Boolean = false,
    onDelete: (FileItem) -> Unit,
    onOpen: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(HonorColors.Background)) {
        HonorTopBar(title = "Clean up", onBack = onBack)
        Text(
            "Largest files on Phone — delete moves them to Recently deleted.",
            fontSize = 13.sp,
            color = HonorColors.Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (partial) {
            Text(
                "Scan stopped — showing partial results.",
                fontSize = 13.sp,
                color = HonorColors.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (loading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(color = HonorColors.AccentBlue)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading... Please wait.", fontSize = 15.sp, color = HonorColors.Muted)
                }
            }
        } else if (largest.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Walang malalaking file.", fontSize = 15.sp, color = HonorColors.Muted)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(largest, key = { it.path }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isDir) HonorIcons.folderYellow()
                            else HonorIcons.docIcon(HonorIcons.docTypeFor(item.name)),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontSize = 16.sp, color = HonorColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${formatSize(item.size)} • ${item.path}",
                                fontSize = 12.sp,
                                color = HonorColors.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(HonorIcons.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(start = 60.dp).background(HonorColors.Divider))
                }
            }
        }
    }
}

/** MLBB source slot — one tap in the SOURCE card opens this sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MlbbSourceSheet(
    dataLabel: String,
    obbLabel: String,
    onDataClick: () -> Unit,
    onObbClick: () -> Unit,
    onOpenInFiles: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = HonorColors.Card) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Mobile Legends", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HonorColors.OnSurface)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onDataClick, modifier = Modifier.fillMaxWidth()) {
                Text(dataLabel, color = HonorColors.OnSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onObbClick, modifier = Modifier.fillMaxWidth()) {
                Text(obbLabel, color = HonorColors.OnSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onOpenInFiles(FileRepository.MLBB_DATA) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buksan data sa Files app", color = HonorColors.OnSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onOpenInFiles(FileRepository.MLBB_OBB) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buksan obb sa Files app", color = HonorColors.OnSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Hindi ito grant — bubuksan lang ng Files app mismo yung folder para makita mo yung laman.",
                fontSize = 12.sp,
                color = HonorColors.Muted
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "/data/data/com.mobile.legends — hindi kaya (root lang).",
                fontSize = 12.sp,
                color = HonorColors.Muted
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Shared list-screen top bar: back chevron + title. */
@Composable
fun HonorTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(HonorIcons.ChevronLeft, contentDescription = "Back", tint = HonorColors.OnSurface, modifier = Modifier.size(28.dp))
        }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = HonorColors.OnSurface)
    }
}
