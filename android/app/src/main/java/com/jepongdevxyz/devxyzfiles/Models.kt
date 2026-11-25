package com.jepongdevxyz.devxyzfiles

/** How the current folder was actually opened. Shown on the status pill. */
enum class AccessMode {
    DIRECT,
    SAF,
    UNAVAILABLE
}

/** One file or folder row. */
data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val via: AccessMode
)

/** Result of probing a restricted folder (Android/data or Android/obb). */
data class ProbeResult(
    val path: String,
    val accessible: Boolean,
    val count: Int,
    val via: AccessMode,
    val error: String? = null
)

/** Live status of the two restricted folders, shown on the home screen. */
data class RestrictedStatus(
    val data: ProbeResult,
    val obb: ProbeResult
)

/** What the user has copied or cut, waiting for Paste. */
data class Clipboard(
    val paths: List<String>,
    val cut: Boolean
)

/** Shortcuts on the home screen. */
data class Shortcut(
    val label: String,
    val path: String,
    val icon: String,
    /** Owning app package, when the shortcut is an app folder (e.g. MLBB). */
    val packageName: String? = null
)

/**
 * MLBB probe: the two SAF-reachable folders plus the honest fact that
 * /data/data/com.mobile.legends is NOT reachable without root.
 */
data class MlbbStatus(
    val data: ProbeResult,
    val obb: ProbeResult,
    /** True when at least one of the two folders exists (MLBB installed). */
    val installed: Boolean,
    /** Always true on a non-root phone: /data/data needs root, not SAF. */
    val privateDataBlocked: Boolean = true
)
