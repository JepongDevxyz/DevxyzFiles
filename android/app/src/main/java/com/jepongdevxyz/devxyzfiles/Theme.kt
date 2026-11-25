package com.jepongdevxyz.devxyzfiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Honor Files black theme (v1.2.0) — colors sampled frame by frame from
 * Jepong's two reference videos of Honor's Files app (886x1920 frames).
 * Dark-only, always: the reference app has no light mode, and forcing
 * dark keeps every screen pixel-identical to it. Do NOT re-introduce a
 * light theme or follow the system theme — that would break the match.
 */
object HonorColors {
    /** App background — pure black, every screen. */
    val Background = Color(0xFF000000)

    /** Cards: Browse category grid, SOURCE card, Phone card, LOCATION card. */
    val Card = Color(0xFF201F22)

    /** Search pill fill (Browse home + list screens). */
    val SearchPill = Color(0xFF191919)

    /** More popup menu card. */
    val MenuCard = Color(0xFF404040)

    /** Folder icon yellow. */
    val FolderYellow = Color(0xFFF6D66E)

    /** Blue accent: breadcrumbs, check circles, "Clean up", active tab. */
    val AccentBlue = Color(0xFF2E7CF6)

    /** Bottom-nav inactive icon + label. */
    val InactiveGrey = Color(0xFF757477)

    /** Secondary text (row sub-lines, section labels, hints). */
    val Muted = Color(0xFF8E8E93)

    /** Primary text — white. */
    val OnSurface = Color(0xFFFFFFFF)

    /** Hairline divider between list rows. */
    val Divider = Color(0xFF262628)

    /** Unselected row name in selection mode (dimmed). */
    val DimmedText = Color(0xFF757477)

    /** Empty radio ring (unselected row in selection mode). */
    val RadioRing = Color(0xFF8E8E93)

    /** Category doc-icon fills (Browse home grid). */
    val ImagesBlue = Color(0xFF2E7CF6)
    val VideosOrange = Color(0xFFF59E0B)
    val DocumentsGreen = Color(0xFF22C55E)
    val AudioPurple = Color(0xFF8B5CF6)
    val AppsTeal = Color(0xFF14B8A6)
    val ArchivesOrangeRed = Color(0xFFF97316)
}

private val HonorDark = darkColorScheme(
    primary = HonorColors.AccentBlue,
    secondary = HonorColors.AccentBlue,
    background = HonorColors.Background,
    surface = HonorColors.Card,
    surfaceVariant = HonorColors.SearchPill,
    onBackground = HonorColors.OnSurface,
    onSurface = HonorColors.OnSurface,
    onSurfaceVariant = HonorColors.Muted,
    outlineVariant = HonorColors.Divider
)

/** Same signature as v1.0.0/v1.1.0 — Agent 2 keeps calling it unchanged. */
@Composable
fun DevxyzFilesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HonorDark, content = content)
}
