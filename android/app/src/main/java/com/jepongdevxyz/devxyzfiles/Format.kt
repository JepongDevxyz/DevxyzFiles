package com.jepongdevxyz.devxyzfiles

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

fun formatDate(millis: Long): String {
    if (millis <= 0) return "—"
    return try {
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(millis))
    } catch (e: Exception) {
        "—"
    }
}

/**
 * 2-decimal GB for the Phone card (v1.2.0): "70.94 GB Available/512 GB".
 * Decimal GB (1 GB = 1e9 bytes), matching how phones advertise storage.
 */
fun formatGb2(bytes: Long): String {
    if (bytes < 0) return "—"
    return String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
}

/**
 * Honor Files relative date (v1.2.0) — matches the reference frames
 * exactly: "37 min ago" / "13 min ago" inside the last hour, "3:48 PM"
 * for earlier today, "18/10/2026" (DD/MM/YYYY) for older dates.
 * "Just now" (< 1 min) is inferred — no frame shows it.
 */
fun formatDateRelative(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (millis <= 0) return "—"
    val diff = nowMillis - millis
    if (diff < 0) return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
    val minutes = diff / 60_000
    if (minutes < 1) return "Just now"
    if (minutes < 60) return "$minutes min ago"
    val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val calThen = Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
        calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)
    if (sameDay) {
        return SimpleDateFormat("h:mm a", Locale.US).format(Date(millis))
    }
    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
}

/** Normalise /sdcard and /storage/self/primary to the canonical path. */
fun normalisePath(path: String): String {
    var p = path.trim()
    if (p.startsWith("/sdcard/")) p = "/storage/emulated/0/" + p.removePrefix("/sdcard/")
    if (p == "/sdcard") p = "/storage/emulated/0"
    if (p.startsWith("/storage/self/primary/")) {
        p = "/storage/emulated/0/" + p.removePrefix("/storage/self/primary/")
    }
    if (p == "/storage/self/primary") p = "/storage/emulated/0"
    while (p.length > 1 && p.endsWith("/")) p = p.dropLast(1)
    return p
}

fun joinPath(parent: String, name: String): String {
    val p = normalisePath(parent)
    return if (p == "/") "/$name" else "$p/$name"
}

fun parentOf(path: String): String? {
    val p = normalisePath(path)
    if (p == "/" || p == "/storage/emulated/0") return null
    val idx = p.lastIndexOf('/')
    if (idx <= 0) return "/"
    return p.substring(0, idx)
}

fun fileNameOf(path: String): String {
    val p = normalisePath(path)
    val idx = p.lastIndexOf('/')
    return if (idx < 0) p else p.substring(idx + 1)
}
