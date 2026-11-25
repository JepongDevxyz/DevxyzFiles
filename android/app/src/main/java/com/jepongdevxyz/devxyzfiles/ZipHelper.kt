package com.jepongdevxyz.devxyzfiles

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Real ZIP compression (v1.2.0) — the Compress action in the More menu.
 * Works across layers: it reads through [FileRepository.accessFor] per
 * path, so SAF-granted folders zip exactly like Direct ones. The zip is
 * written next to the original as "<name>.zip" (the caller computes the
 * destination path). Streams in 256 KB chunks, never holds a whole file
 * in memory. Returns false on any failure; a partial zip is deleted.
 *
 * NOTE: [FileAccess.openOutputStream] on the SAF layer deletes a
 * same-named file first, so re-compressing replaces the old zip instead
 * of creating "<name> (1).zip".
 */
suspend fun zipPath(
    context: Context,
    repository: FileRepository,
    sourcePath: String,
    destZipPath: String
): Boolean = withContext(Dispatchers.IO) {
    val src = normalisePath(sourcePath)
    val dest = normalisePath(destZipPath)
    val destParent = parentOf(dest) ?: return@withContext false
    try {
        // Make sure the destination folder exists on its own layer.
        val destAccess = repository.accessFor(destParent)
        destAccess.mkdir(destParent)
        val out = destAccess.openOutputStream(dest) ?: return@withContext false
        var ok = true
        try {
            ZipOutputStream(out).use { zip ->
                ok = addToZip(repository, src, fileNameOf(src), zip)
            }
        } catch (e: Exception) {
            ok = false
        }
        if (!ok) {
            try {
                repository.accessFor(dest).delete(dest)
            } catch (e: Exception) {
                // Best effort — a partial zip is a cosmetic leftover.
            }
        }
        ok
    } catch (e: Exception) {
        false
    }
}

/**
 * Adds one node (file or whole folder tree) to the zip. Directories are
 * detected the same way [FileAccess] does it elsewhere: list() succeeds
 * on a directory and throws on a file. Empty directories still get an
 * entry (trailing "/") so they survive the round-trip.
 */
private suspend fun addToZip(
    repository: FileRepository,
    path: String,
    entryName: String,
    zip: ZipOutputStream
): Boolean {
    val access = repository.accessFor(path)
    val children = try {
        access.list(path)
    } catch (e: Exception) {
        null
    }
    if (children != null) {
        zip.putNextEntry(ZipEntry("$entryName/"))
        zip.closeEntry()
        var ok = true
        for (child in children) {
            if (!addToZip(repository, child.path, "$entryName/${child.name}", zip)) ok = false
        }
        return ok
    }
    val input = try {
        access.openInputStream(path)
    } catch (e: Exception) {
        null
    } ?: return false
    return try {
        zip.putNextEntry(ZipEntry(entryName))
        input.use { it.copyTo(zip, 256 * 1024) }
        zip.closeEntry()
        true
    } catch (e: Exception) {
        false
    }
}
