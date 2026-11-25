package com.jepongdevxyz.devxyzfiles

import java.text.SimpleDateFormat
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
