package com.jepongdevxyz.devxyzfiles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Honor Files icons (v1.2.0) — drawn as code, no raster assets.
 * Shapes are traced from the reference frames; colors come from
 * [HonorColors]. Where the reference matches a standard Material icon
 * exactly (share, trash, lock, …) the Material icon is re-exported
 * here so Agent 2 imports icons from exactly one place.
 */
object HonorIcons {

    // ---- Re-exported Material icons (reference matches them exactly) ----
    val Share: ImageVector get() = Icons.Filled.Share
    val Delete: ImageVector get() = Icons.Filled.Delete
    val Lock: ImageVector get() = Icons.Filled.Lock
    val Basket: ImageVector get() = Icons.Filled.ShoppingBasket
    val Clock: ImageVector get() = Icons.Filled.AccessTime
    val Doc: ImageVector get() = Icons.Filled.Description
    val Search: ImageVector get() = Icons.Filled.Search
    val Add: ImageVector get() = Icons.Filled.Add
    val MoreVert: ImageVector get() = Icons.Filled.MoreVert
    val Close: ImageVector get() = Icons.Filled.Close
    val ChevronLeft: ImageVector get() = Icons.Filled.ChevronLeft
    val ChevronRight: ImageVector get() = Icons.Filled.ChevronRight
    val FolderPlus: ImageVector get() = Icons.Filled.CreateNewFolder
    val Star: ImageVector get() = Icons.Filled.Star

    // ---- Custom vectors ----

    // PathBuilder (the receiver inside ImageVector `path { }` blocks) has
    // NO addOval/addRect — those live on graphics Path (Canvas), not on
    // the vector builder. These two helpers give the builder the same
    // shapes using only moveTo/lineTo/arcTo/close, so every call site
    // below compiles unchanged. (CI Run #6: 19 unresolved-reference
    // errors were all these names; the `rotate` one was the missing
    // drawscope import above, not a wrong call.)
    private fun PathBuilder.addOval(oval: Rect) {
        val cx = oval.center.x
        val cy = oval.center.y
        moveTo(cx + oval.width / 2f, cy)
        arcTo(oval, 0f, 180f, false)
        arcTo(oval, 180f, 180f, false)
        close()
    }

    private fun PathBuilder.addRect(rect: Rect) {
        moveTo(rect.left, rect.top)
        lineTo(rect.right, rect.top)
        lineTo(rect.right, rect.bottom)
        lineTo(rect.left, rect.bottom)
        close()
    }

    /** Yellow folder with tab — the list-row folder in every frame. */
    fun folderYellow(): ImageVector = folder(HonorColors.FolderYellow)

    /** Same folder in accent blue — the active Browse bottom-nav tab. */
    fun folderBlue(): ImageVector = folder(HonorColors.AccentBlue)

    private fun folder(fill: Color): ImageVector =
        ImageVector.Builder(
            name = "Folder",
            defaultWidth = 48.dp, defaultHeight = 48.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(fill)) {
                moveTo(3f, 7f)
                quadTo(3f, 5f, 5f, 5f)
                lineTo(8.5f, 5f)
                lineTo(10.5f, 7f)
                lineTo(19f, 7f)
                quadTo(21f, 7f, 21f, 9f)
                lineTo(21f, 18f)
                quadTo(21f, 20f, 19f, 20f)
                lineTo(5f, 20f)
                quadTo(3f, 20f, 3f, 18f)
                lineTo(3f, 7f)
                close()
            }
        }.build()

    /** Blue filled circle + white check — selected row in selection mode. */
    fun checkCircleBlue(): ImageVector =
        ImageVector.Builder(
            name = "CheckCircleBlue",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(HonorColors.AccentBlue)) {
                addOval(Rect(1.5f, 1.5f, 22.5f, 22.5f))
            }
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 12.5f)
                lineTo(10.5f, 16f)
                lineTo(17f, 8f)
            }
        }.build()

    /** Empty grey ring — unselected row in selection mode. */
    fun radioRing(): ImageVector =
        ImageVector.Builder(
            name = "RadioRing",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(HonorColors.RadioRing),
                strokeLineWidth = 1.8f
            ) {
                addOval(Rect(3f, 3f, 21f, 21f))
            }
        }.build()

    /**
     * Select-all glyph (top-right in selection mode): 2x2 grid of small
     * circles. When [checked] is true (all visible selected) a white
     * check overlays the grid and the circles turn blue.
     */
    fun selectAllGlyph(checked: Boolean): ImageVector =
        ImageVector.Builder(
            name = if (checked) "SelectAllChecked" else "SelectAll",
            defaultWidth = 28.dp, defaultHeight = 28.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            val fill = SolidColor(if (checked) HonorColors.AccentBlue else Color.White)
            path(fill = fill) {
                addOval(Rect(3f, 3f, 9.5f, 9.5f))
                addOval(Rect(14.5f, 3f, 21f, 9.5f))
                addOval(Rect(3f, 14.5f, 9.5f, 21f))
                addOval(Rect(14.5f, 14.5f, 21f, 21f))
            }
            if (checked) {
                path(
                    fill = null,
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 2.4f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                ) {
                    moveTo(5f, 12.5f)
                    lineTo(10f, 17.5f)
                    lineTo(19.5f, 6.5f)
                }
            }
        }.build()

    /** Bookmark outline — the Tags action in the selection bottom bar. */
    fun bookmarkOutline(): ImageVector =
        ImageVector.Builder(
            name = "BookmarkOutline",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 3f)
                lineTo(17f, 3f)
                lineTo(17f, 21f)
                lineTo(12f, 16.5f)
                lineTo(7f, 21f)
                close()
            }
        }.build()

    /** Funnel outline — the sort/filter button (list top bar). */
    fun funnel(): ImageVector =
        ImageVector.Builder(
            name = "Funnel",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(14f, 12.5f)
                lineTo(14f, 19f)
                lineTo(10f, 21f)
                lineTo(10f, 12.5f)
                close()
            }
        }.build()

    /** Phone outline — the Phone card icon on Browse home. */
    fun phoneOutline(): ImageVector =
        ImageVector.Builder(
            name = "PhoneOutline",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(8f, 3f)
                quadTo(8f, 2f, 9f, 2f)
                lineTo(15f, 2f)
                quadTo(16f, 2f, 16f, 3f)
                lineTo(16f, 21f)
                quadTo(16f, 22f, 15f, 22f)
                lineTo(9f, 22f)
                quadTo(8f, 22f, 8f, 21f)
                lineTo(8f, 3f)
                close()
                moveTo(11f, 18.5f)
                lineTo(13f, 18.5f)
            }
        }.build()

    /** Folder outline + right arrow — the Move action in the bottom bar. */
    fun moveIcon(): ImageVector =
        ImageVector.Builder(
            name = "MoveIcon",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2.5f, 8f)
                quadTo(2.5f, 6f, 4.5f, 6f)
                lineTo(7f, 6f)
                lineTo(8.5f, 8f)
                lineTo(14f, 8f)
                quadTo(16f, 8f, 16f, 10f)
                lineTo(16f, 17f)
                quadTo(16f, 19f, 14f, 19f)
                lineTo(4.5f, 19f)
                quadTo(2.5f, 19f, 2.5f, 17f)
                lineTo(2.5f, 8f)
                close()
            }
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 13.5f)
                lineTo(21f, 13.5f)
                moveTo(18f, 10.5f)
                lineTo(21f, 13.5f)
                lineTo(18f, 16.5f)
            }
        }.build()

    // ---- File-type doc icons (Browse categories + file rows) ----

    enum class DocType { IMAGE, VIDEO, DOCUMENT, AUDIO, APP, ARCHIVE, TEXT, DEFAULT }

    /** Extension → doc type. ".bin"/".dat" and unknown fall to DEFAULT (grey doc). */
    fun docTypeFor(fileName: String): DocType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif" -> DocType.IMAGE
            "mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv" -> DocType.VIDEO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "md", "csv" -> DocType.DOCUMENT
            "mp3", "wav", "flac", "ogg", "m4a", "aac", "opus" -> DocType.AUDIO
            "apk", "xapk" -> DocType.APP
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso" -> DocType.ARCHIVE
            "txt", "log" -> DocType.TEXT
            else -> DocType.DEFAULT
        }
    }

    private val docIconCache = mutableMapOf<DocType, ImageVector>()

    /** Colored doc icon with a white glyph, per type. Cached — call freely per row. */
    fun docIcon(type: DocType): ImageVector = docIconCache.getOrPut(type) { buildDocIcon(type) }

    private fun docFill(type: DocType): Color = when (type) {
        DocType.IMAGE -> HonorColors.ImagesBlue
        DocType.VIDEO -> HonorColors.VideosOrange
        DocType.DOCUMENT -> HonorColors.DocumentsGreen
        DocType.AUDIO -> HonorColors.AudioPurple
        DocType.APP -> HonorColors.AppsTeal
        DocType.ARCHIVE -> HonorColors.ArchivesOrangeRed
        DocType.TEXT -> HonorColors.AppsTeal
        DocType.DEFAULT -> Color(0xFF8E8E93)
    }

    private fun buildDocIcon(type: DocType): ImageVector =
        ImageVector.Builder(
            name = "Doc${type.name}",
            defaultWidth = 48.dp, defaultHeight = 48.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            // Doc body with folded top-right corner.
            path(fill = SolidColor(docFill(type))) {
                moveTo(8f, 2f)
                lineTo(14f, 2f)
                lineTo(19f, 7f)
                lineTo(19f, 21f)
                quadTo(19f, 22f, 18f, 22f)
                lineTo(7f, 22f)
                quadTo(6f, 22f, 6f, 21f)
                lineTo(6f, 3f)
                quadTo(6f, 2f, 7f, 2f)
                close()
            }
            path(fill = SolidColor(Color.Black), fillAlpha = 0.25f) {
                moveTo(14f, 2f)
                lineTo(19f, 7f)
                lineTo(14f, 7f)
                close()
            }
            // White glyph per type.
            when (type) {
                DocType.IMAGE -> {
                    path(fill = SolidColor(Color.White)) {
                        moveTo(8f, 17f)
                        lineTo(11f, 12f)
                        lineTo(13.5f, 15.5f)
                        lineTo(15f, 13.5f)
                        lineTo(17.5f, 17f)
                        close()
                        addOval(Rect(14f, 9.5f, 16.4f, 11.9f))
                    }
                }
                DocType.VIDEO -> {
                    path(fill = SolidColor(Color.White)) {
                        moveTo(10f, 10f)
                        lineTo(17f, 14f)
                        lineTo(10f, 18f)
                        close()
                    }
                }
                DocType.DOCUMENT -> {
                    path(fill = SolidColor(Color.White)) {
                        addRect(Rect(9f, 10f, 16f, 11.6f))
                        addRect(Rect(9f, 13f, 16f, 14.6f))
                        addRect(Rect(9f, 16f, 13.5f, 17.6f))
                    }
                }
                DocType.AUDIO -> {
                    path(fill = SolidColor(Color.White)) {
                        addOval(Rect(8.8f, 15.6f, 12.4f, 19.2f))
                        addRect(Rect(11.9f, 8.5f, 13.1f, 17f))
                        moveTo(13.1f, 8.5f)
                        lineTo(17f, 10.5f)
                        lineTo(13.1f, 12.5f)
                        close()
                    }
                }
                DocType.APP -> {
                    // Tiny robot head: white rounded rect + teal eyes + antennae.
                    path(fill = SolidColor(Color.White)) {
                        moveTo(9.5f, 10.5f)
                        quadTo(9.5f, 9.5f, 10.5f, 9.5f)
                        lineTo(14.5f, 9.5f)
                        quadTo(15.5f, 9.5f, 15.5f, 10.5f)
                        lineTo(15.5f, 15f)
                        quadTo(15.5f, 16f, 14.5f, 16f)
                        lineTo(10.5f, 16f)
                        quadTo(9.5f, 16f, 9.5f, 15f)
                        close()
                    }
                    path(fill = SolidColor(docFill(type))) {
                        addOval(Rect(10.8f, 12f, 12f, 13.2f))
                        addOval(Rect(13f, 12f, 14.2f, 13.2f))
                    }
                    path(
                        fill = null,
                        stroke = SolidColor(Color.White),
                        strokeLineWidth = 1.1f,
                        strokeLineCap = StrokeCap.Round
                    ) {
                        moveTo(11f, 9.5f)
                        lineTo(10f, 7.5f)
                        moveTo(14f, 9.5f)
                        lineTo(15f, 7.5f)
                    }
                }
                DocType.ARCHIVE -> {
                    path(fill = SolidColor(Color.White)) {
                        addRect(Rect(7.5f, 8.5f, 17.5f, 11f))
                        addRect(Rect(8.5f, 12f, 16.5f, 18f))
                    }
                }
                DocType.TEXT -> {
                    path(fill = SolidColor(Color.White)) {
                        addRect(Rect(8.5f, 9f, 16.5f, 11f))
                        addRect(Rect(11.5f, 9f, 13.5f, 18f))
                    }
                }
                DocType.DEFAULT -> {
                    // Grey doc, no glyph — matches the .bin/.dat rows in the frames.
                }
            }
        }.build()
}

/**
 * Gear badge overlaid on the bottom-right of a folder icon — the
 * reference marks the folder literally named "android" this way
 * (seen in Phone > … > com.mobile.legends > files > dragon2017).
 * Agent 2: show it only when [FileItem.name] equals "android"
 * (case-insensitive); never on other folders.
 */
@Composable
fun GearBadge(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val c = center
        val r = size.minDimension / 2f
        for (i in 0 until 8) {
            rotate(degrees = i * 45f, pivot = c) {
                drawRoundRect(
                    color = Color(0xFF5B7C99),
                    topLeft = Offset(c.x - r * 0.14f, c.y - r),
                    size = Size(r * 0.28f, r * 0.5f),
                    cornerRadius = CornerRadius(r * 0.1f, r * 0.1f)
                )
            }
        }
        drawCircle(color = Color(0xFFD7DEE8), radius = r * 0.66f, center = c)
        drawCircle(color = Color(0xFF2E7CF6), radius = r * 0.26f, center = c)
    }
}
