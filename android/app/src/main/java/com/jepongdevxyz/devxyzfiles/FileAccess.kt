package com.jepongdevxyz.devxyzfiles

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.jepongdevxyz.devxyzfiles.shizuku.IDevxyzFilesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** One access implementation. All calls are suspend and run off the UI thread. */
interface FileAccess {
    val mode: AccessMode
    suspend fun list(path: String): List<FileItem>
    suspend fun exists(path: String): Boolean
    suspend fun mkdir(path: String): Boolean
    suspend fun delete(path: String): Boolean
    suspend fun rename(oldPath: String, newPath: String): Boolean
    suspend fun copy(srcPath: String, destPath: String): Boolean
    suspend fun move(srcPath: String, destPath: String): Boolean

    /**
     * Copy with live progress (copiedBytes, totalBytes). Default: run the
     * plain copy and report only completion — SAF overrides stay on this.
     * Direct and Shizuku stream in 256 KB chunks and report real progress,
     * which matters for multi-GB MLBB OBB files.
     */
    suspend fun copyWithProgress(
        srcPath: String,
        destPath: String,
        onProgress: (copied: Long, total: Long) -> Unit
    ): Boolean {
        val ok = copy(srcPath, destPath)
        if (ok) onProgress(1L, 1L)
        return ok
    }
}

/** Plain java.io.File access. Works for normal storage; Android blocks it
 *  for other apps' Android/data and Android/obb on Android 11+. */
class DirectFileAccess : FileAccess {
    override val mode: AccessMode = AccessMode.DIRECT

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(normalisePath(path))
        if (!dir.isDirectory) throw IOException("Not a directory: $path")
        val children = dir.listFiles() ?: throw IOException("Cannot list: $path")
        children
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { toItem(it, AccessMode.DIRECT) }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(normalisePath(path)).exists()
    }

    override suspend fun mkdir(path: String): Boolean = withContext(Dispatchers.IO) {
        File(normalisePath(path)).mkdirs()
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        deleteRecursive(File(normalisePath(path)))
    }

    override suspend fun rename(oldPath: String, newPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val src = File(normalisePath(oldPath))
            val dest = File(normalisePath(newPath))
            if (!src.exists() || dest.exists()) return@withContext false
            if (src.renameTo(dest)) return@withContext true
            if (copyRecursive(src, dest)) deleteRecursive(src) else false
        }

    override suspend fun copy(srcPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            copyRecursive(File(normalisePath(srcPath)), File(normalisePath(destPath)))
        }

    override suspend fun copyWithProgress(
        srcPath: String,
        destPath: String,
        onProgress: (copied: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val src = File(normalisePath(srcPath))
        val dest = File(normalisePath(destPath))
        val total = sizeRecursive(src)
        var copied = 0L
        onProgress(0L, total)
        val ok = copyRecursive(src, dest) { bytes ->
            copied += bytes
            onProgress(copied, total)
        }
        if (ok) onProgress(total, total)
        ok
    }

    override suspend fun move(srcPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            if (rename(srcPath, destPath)) true
            else if (copy(srcPath, destPath)) delete(srcPath) else false
        }

    private fun toItem(f: File, via: AccessMode): FileItem {
        return FileItem(
            name = f.name,
            path = f.absolutePath,
            isDir = f.isDirectory,
            size = if (f.isDirectory) 0L else f.length(),
            lastModified = f.lastModified(),
            via = via
        )
    }

    private fun deleteRecursive(f: File): Boolean {
        if (f.isDirectory) {
            val children = f.listFiles() ?: return false
            var ok = true
            for (child in children) if (!deleteRecursive(child)) ok = false
            if (!f.delete()) ok = false
            return ok
        }
        return f.delete()
    }

    private fun copyRecursive(src: File, dest: File): Boolean {
        return copyRecursive(src, dest, null)
    }

    private fun sizeRecursive(f: File): Long {
        return try {
            if (f.isFile) return f.length()
            var total = 0L
            val children = f.listFiles() ?: return 0L
            for (child in children) total += sizeRecursive(child)
            total
        } catch (e: Exception) {
            0L
        }
    }

    private fun copyRecursive(src: File, dest: File, onChunk: ((bytes: Long) -> Unit)?): Boolean {
        if (src.isDirectory) {
            if (!dest.exists() && !dest.mkdirs()) return false
            val children = src.listFiles() ?: return false
            var ok = true
            for (child in children) if (!copyRecursive(child, File(dest, child.name), onChunk)) ok = false
            return ok
        }
        dest.parentFile?.let { if (!it.exists()) it.mkdirs() }
        return try {
            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        onChunk?.invoke(read.toLong())
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** Access through the Shizuku user-service (shell UID 2000). Every call is a
 *  Binder transaction to the privileged process; the file work itself runs
 *  there, so no file bytes cross the Binder. */
class ShizukuFileAccess(private val service: IDevxyzFilesService) : FileAccess {
    override val mode: AccessMode = AccessMode.SHIZUKU

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val rows = service.list(normalisePath(path))
            ?: throw IOException("Shizuku cannot list: $path")
        rows.mapNotNull { decode(it, normalisePath(path)) }
            .sortedWith(compareByDescending<FileItem> { it.isDir }.thenBy { it.name.lowercase() })
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        service.exists(normalisePath(path))
    }

    override suspend fun mkdir(path: String): Boolean = withContext(Dispatchers.IO) {
        service.mkdirs(normalisePath(path))
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        service.delete(normalisePath(path))
    }

    override suspend fun rename(oldPath: String, newPath: String): Boolean =
        withContext(Dispatchers.IO) {
            service.rename(normalisePath(oldPath), normalisePath(newPath))
        }

    override suspend fun copy(srcPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            service.copy(normalisePath(srcPath), normalisePath(destPath))
        }

    override suspend fun copyWithProgress(
        srcPath: String,
        destPath: String,
        onProgress: (copied: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        // The copy itself runs inside the privileged process; this thread
        // polls its progress counters so the UI gets a real progress bar
        // for multi-GB MLBB OBB files.
        val copyJob = kotlinx.coroutines.async {
            service.copy(normalisePath(srcPath), normalisePath(destPath))
        }
        var lastTotal = 0L
        while (copyJob.isActive) {
            try {
                val total = service.copyTotal
                val copied = service.copyProgress
                if (total > 0) {
                    lastTotal = total
                    onProgress(copied.coerceAtMost(total), total)
                }
            } catch (e: Exception) {
                // Binder hiccup while polling — the copy job still decides.
            }
            kotlinx.coroutines.delay(200)
        }
        val ok = copyJob.await()
        if (ok) {
            val total = try {
                service.copyTotal
            } catch (e: Exception) {
                lastTotal
            }
            onProgress(total, total)
        }
        ok
    }

    override suspend fun move(srcPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            service.move(normalisePath(srcPath), normalisePath(destPath))
        }

    private fun decode(row: String, parent: String): FileItem? {
        // name|isDir|size|lastModified
        val parts = row.split('|')
        if (parts.size != 4) return null
        return FileItem(
            name = parts[0],
            path = joinPath(parent, parts[0]),
            isDir = parts[1].toBooleanStrictOrNull() ?: false,
            size = parts[2].toLongOrNull() ?: 0L,
            lastModified = parts[3].toLongOrNull() ?: 0L,
            via = AccessMode.SHIZUKU
        )
    }
}

/** Storage Access Framework access for folders the user granted via the
 *  system folder picker. Works folder-by-folder only, and on Android 13+
 *  the picker refuses Android/data and Android/obb unless the Files app
 *  updates were removed (see the in-app help sheet). */
class SafFileAccess(private val context: Context) : FileAccess {
    override val mode: AccessMode = AccessMode.SAF

    private fun rootFor(path: String): DocumentFile? {
        val target = normalisePath(path)
        return SafHelper.persistedRoots(context)
            .firstOrNull { (_, rootPath) -> target == rootPath || target.startsWith("$rootPath/") }
            ?.let { (uri, _) -> DocumentFile.fromTreeUri(context, uri) }
            ?.let { root -> SafHelper.resolve(context, root, target) }
    }

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = rootFor(path) ?: throw IOException("No SAF grant covers: $path")
        if (!dir.isDirectory) throw IOException("Not a directory: $path")
        dir.listFiles()
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() ?: "" })
            .map {
                FileItem(
                    name = it.name ?: "?",
                    path = joinPath(normalisePath(path), it.name ?: "?"),
                    isDir = it.isDirectory,
                    size = if (it.isDirectory) 0L else it.length(),
                    lastModified = it.lastModified(),
                    via = AccessMode.SAF
                )
            }
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        rootFor(path)?.exists() == true
    }

    override suspend fun mkdir(path: String): Boolean = withContext(Dispatchers.IO) {
        val parent = parentOf(path) ?: return@withContext false
        val dir = rootFor(parent) ?: return@withContext false
        dir.createDirectory(fileNameOf(path)) != null
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        rootFor(path)?.delete() == true
    }

    override suspend fun rename(oldPath: String, newPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = rootFor(oldPath) ?: return@withContext false
            if (parentOf(oldPath) != parentOf(newPath)) return@withContext false
            file.renameTo(fileNameOf(newPath))
        }

    override suspend fun copy(srcPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            copyViaSaf(srcPath, destPath)
        }

    override suspend fun move(srcPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            if (copyViaSaf(srcPath, destPath)) delete(srcPath) else false
        }

    private fun copyViaSaf(srcPath: String, destPath: String): Boolean {
        val src = rootFor(srcPath) ?: return false
        val destParentPath = parentOf(destPath) ?: return false
        val destParent = rootFor(destParentPath) ?: return false
        return try {
            if (src.isDirectory) {
                val created = destParent.createDirectory(fileNameOf(destPath)) ?: return false
                var ok = true
                for (child in src.listFiles()) {
                    if (!copyInto(child, created)) ok = false
                }
                ok
            } else {
                val mime = "application/octet-stream"
                val out = destParent.createFile(mime, fileNameOf(destPath)) ?: return false
                context.contentResolver.openInputStream(src.uri)?.use { input ->
                    context.contentResolver.openOutputStream(out.uri)?.use { output ->
                        input.copyTo(output)
                    } ?: return false
                } ?: return false
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun copyInto(src: DocumentFile, destDir: DocumentFile): Boolean {
        return try {
            if (src.isDirectory) {
                val created = destDir.createDirectory(src.name ?: return false) ?: return false
                var ok = true
                for (child in src.listFiles()) if (!copyInto(child, created)) ok = false
                ok
            } else {
                val out = destDir.createFile("application/octet-stream", src.name ?: return false)
                    ?: return false
                context.contentResolver.openInputStream(src.uri)?.use { input ->
                    context.contentResolver.openOutputStream(out.uri)?.use { output ->
                        input.copyTo(output)
                    } ?: return false
                } ?: return false
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Chooses the access implementation per path and probes the restricted
 * folders so the UI can show a live, honest status.
 */
class FileRepository(
    private val context: Context
) {
    private val direct = DirectFileAccess()

    fun isRestricted(path: String): Boolean {
        val p = normalisePath(path)
        return p == RESTRICTED_DATA || p.startsWith("$RESTRICTED_DATA/") ||
            p == RESTRICTED_OBB || p.startsWith("$RESTRICTED_OBB/")
    }

    fun accessFor(path: String): FileAccess {
        val p = normalisePath(path)
        if (isRestricted(p) && ShizukuManager.isReady()) {
            val svc = ShizukuManager.service
            if (svc != null) return ShizukuFileAccess(svc)
        }
        if (SafHelper.covers(context, p)) return SafFileAccess(context)
        return direct
    }

    suspend fun list(path: String): List<FileItem> {
        val p = normalisePath(path)
        val primary = accessFor(p)
        return try {
            primary.list(p)
        } catch (e: Exception) {
            // Fallback: a restricted folder that Shizuku just lost gets one
            // SAF/Direct attempt before the UI reports it as blocked.
            if (primary.mode != AccessMode.DIRECT) {
                try {
                    direct.list(p)
                } catch (e2: Exception) {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    suspend fun probe(path: String): ProbeResult {
        val p = normalisePath(path)
        val access = accessFor(p)
        return try {
            val items = access.list(p)
            ProbeResult(p, true, items.size, access.mode)
        } catch (e: Exception) {
            ProbeResult(p, false, 0, access.mode, e.message)
        }
    }

    suspend fun probeRestricted(): RestrictedStatus {
        return RestrictedStatus(probe(RESTRICTED_DATA), probe(RESTRICTED_OBB))
    }

    suspend fun exists(path: String): Boolean = accessFor(path).exists(path)
    suspend fun mkdir(path: String): Boolean = accessFor(path).mkdir(path)
    suspend fun delete(path: String): Boolean = accessFor(path).delete(path)
    suspend fun rename(oldPath: String, newPath: String): Boolean =
        accessFor(oldPath).rename(oldPath, newPath)

    suspend fun copy(srcPath: String, destPath: String): Boolean {
        // Copy uses the SOURCE's access layer; the privileged process can
        // write the destination too when both are restricted.
        return accessFor(srcPath).copy(srcPath, destPath)
    }

    suspend fun copyWithProgress(
        srcPath: String,
        destPath: String,
        onProgress: (copied: Long, total: Long) -> Unit
    ): Boolean {
        return accessFor(srcPath).copyWithProgress(srcPath, destPath, onProgress)
    }

    suspend fun move(srcPath: String, destPath: String): Boolean {
        return accessFor(srcPath).move(srcPath, destPath)
    }

    /**
     * MLBB first-class test: probes both Shizuku-reachable MLBB folders.
     * A missing folder while the parent IS listable means "not installed",
     * not "blocked" — the probe error text says which.
     */
    suspend fun probeMlbb(): MlbbStatus {
        val dataProbe = probe(MLBB_DATA)
        val obbProbe = probe(MLBB_OBB)
        val parentOk = dataProbe.accessible || obbProbe.accessible ||
            probe(RESTRICTED_DATA).accessible || probe(RESTRICTED_OBB).accessible
        val installed = dataProbe.accessible || obbProbe.accessible ||
            exists(MLBB_DATA) || exists(MLBB_OBB)
        val data = if (!dataProbe.accessible && parentOk && !installed) {
            dataProbe.copy(error = "Folder missing — MLBB data not installed yet")
        } else {
            dataProbe
        }
        val obb = if (!obbProbe.accessible && parentOk && !installed) {
            obbProbe.copy(error = "Folder missing — MLBB OBB not installed yet")
        } else {
            obbProbe
        }
        return MlbbStatus(data = data, obb = obb, installed = installed)
    }

    companion object {
        const val STORAGE_ROOT = "/storage/emulated/0"
        const val RESTRICTED_DATA = "/storage/emulated/0/Android/data"
        const val RESTRICTED_OBB = "/storage/emulated/0/Android/obb"

        // Mobile Legends: Bang Bang — first-class test case (her order).
        const val MLBB_PACKAGE = "com.mobile.legends"
        const val MLBB_DATA = "/storage/emulated/0/Android/data/com.mobile.legends"
        const val MLBB_OBB = "/storage/emulated/0/Android/obb/com.mobile.legends"
        // HONEST LIMIT: reachable only with root. Shizuku (shell UID 2000)
        // cannot read /data/data on Android 16. Never claim otherwise.
        const val MLBB_PRIVATE_DATA = "/data/data/com.mobile.legends"
    }
}
