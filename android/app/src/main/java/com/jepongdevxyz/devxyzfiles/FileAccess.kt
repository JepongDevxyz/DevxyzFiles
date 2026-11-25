package com.jepongdevxyz.devxyzfiles

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

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
     * Copy with live progress (copiedBytes, totalBytes). Direct streams in
     * 256 KB chunks and reports real progress; SAF does the same via the
     * shared streamCopy helper — which matters for multi-GB MLBB OBB files.
     */
    suspend fun copyWithProgress(
        srcPath: String,
        destPath: String,
        onProgress: (copied: Long, total: Long) -> Unit
    ): Boolean {
        return streamCopy(this, this, srcPath, destPath, onProgress)
    }

    /** Total bytes under a path (the file itself, or the whole tree for a dir). */
    suspend fun totalSize(path: String): Long

    /** Open a file for reading, or null when it cannot be opened. */
    suspend fun openInputStream(path: String): InputStream?

    /** Create (or replace) a file for writing, or null when it cannot be created. */
    suspend fun openOutputStream(path: String): OutputStream?
}

/**
 * Streaming copy that works across ANY layer combination (Direct<->SAF,
 * SAF grant A <-> SAF grant B, SAF<->Download). Streams in 256 KB chunks,
 * never holds a whole file in memory, and reports real copied/total bytes.
 * Directory copies recurse; an empty directory just creates the dest dir.
 */
private suspend fun streamCopy(
    srcAccess: FileAccess,
    destAccess: FileAccess,
    srcPath: String,
    destPath: String,
    onProgress: (copied: Long, total: Long) -> Unit
): Boolean {
    val total = try {
        srcAccess.totalSize(srcPath)
    } catch (e: Exception) {
        0L
    }
    var copied = 0L
    onProgress(0L, total)
    val ok = copyNode(srcAccess, destAccess, normalisePath(srcPath), normalisePath(destPath)) { bytes ->
        copied += bytes
        onProgress(copied.coerceAtMost(total), total)
    }
    if (ok) onProgress(total, total)
    return ok
}

private suspend fun copyNode(
    srcAccess: FileAccess,
    destAccess: FileAccess,
    srcPath: String,
    destPath: String,
    onChunk: (bytes: Long) -> Unit
): Boolean {
    // A directory lists; a file throws "Not a directory" — that is the
    // isDir test, because neither layer exposes isDir() on its own.
    val children = try {
        srcAccess.list(srcPath)
    } catch (e: Exception) {
        null
    }
    if (children != null) {
        val made = try {
            destAccess.mkdir(destPath)
        } catch (e: Exception) {
            false
        }
        if (!made) {
            // mkdir returns false when the dir already exists — only fail
            // when the dest still cannot be listed as a directory.
            val existsAsDir = try {
                destAccess.list(destPath)
                true
            } catch (e: Exception) {
                false
            }
            if (!existsAsDir) return false
        }
        var ok = true
        for (child in children) {
            if (!copyNode(srcAccess, destAccess, child.path, joinPath(destPath, child.name), onChunk)) ok = false
        }
        return ok
    }
    // File: stream it in 256 KB chunks.
    val input = try {
        srcAccess.openInputStream(srcPath)
    } catch (e: Exception) {
        null
    } ?: return false
    val output = try {
        destAccess.openOutputStream(destPath)
    } catch (e: Exception) {
        null
    }
    if (output == null) {
        try {
            input.close()
        } catch (e: Exception) {
            // Nothing to clean up.
        }
        return false
    }
    return try {
        input.use { ins ->
            output.use { outs ->
                val buffer = ByteArray(256 * 1024)
                var read: Int
                while (ins.read(buffer).also { read = it } != -1) {
                    outs.write(buffer, 0, read)
                    onChunk(read.toLong())
                }
            }
        }
        true
    } catch (e: Exception) {
        false
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

    override suspend fun totalSize(path: String): Long = withContext(Dispatchers.IO) {
        sizeRecursive(File(normalisePath(path)))
    }

    override suspend fun openInputStream(path: String): InputStream? = withContext(Dispatchers.IO) {
        try {
            FileInputStream(File(normalisePath(path)))
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun openOutputStream(path: String): OutputStream? = withContext(Dispatchers.IO) {
        try {
            val file = File(normalisePath(path))
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileOutputStream(file)
        } catch (e: Exception) {
            null
        }
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

/** Storage Access Framework access for folders the user granted via the
 *  system folder picker. Works folder-by-folder only, and on Android 13+
 *  the picker refuses Android/data and Android/obb unless the Files app
 *  updates were removed (see the in-app help sheet). A single app-folder
 *  grant (e.g. Android/data/com.mobile.legends) works directly. */
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
        try {
            dir.createDirectory(fileNameOf(path)) != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val doc = rootFor(path) ?: return@withContext false
        deleteRecursive(doc)
    }

    override suspend fun rename(oldPath: String, newPath: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = rootFor(oldPath) ?: return@withContext false
            // renameTo only works inside the same parent — a cross-parent
            // rename must fail cleanly, never silently corrupt.
            if (parentOf(oldPath) != parentOf(newPath)) return@withContext false
            try {
                file.renameTo(fileNameOf(newPath))
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun copy(srcPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            // NOTE: plain `this` here is the withContext CoroutineScope, not
            // this SafFileAccess — it must be qualified or the build fails
            // (CI Run #4: argument type mismatch at these two calls).
            streamCopy(this@SafFileAccess, this@SafFileAccess, srcPath, destPath) { _, _ -> }
        }

    override suspend fun copyWithProgress(
        srcPath: String,
        destPath: String,
        onProgress: (copied: Long, total: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        // Real 256 KB-chunk streaming with live copied/total — not the old
        // completion-only default. Same helper the cross-layer copy uses.
        // (Qualified `this` — see copy() above: plain `this` is the scope.)
        streamCopy(this@SafFileAccess, this@SafFileAccess, srcPath, destPath, onProgress)
    }

    override suspend fun move(srcPath: String, destPath: String): Boolean =
        withContext(Dispatchers.IO) {
            if (copy(srcPath, destPath)) delete(srcPath) else false
        }

    override suspend fun totalSize(path: String): Long = withContext(Dispatchers.IO) {
        val doc = rootFor(path) ?: return@withContext 0L
        sizeRecursive(doc)
    }

    override suspend fun openInputStream(path: String): InputStream? = withContext(Dispatchers.IO) {
        try {
            val doc = rootFor(path) ?: return@withContext null
            if (doc.isDirectory) return@withContext null
            context.contentResolver.openInputStream(doc.uri)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun openOutputStream(path: String): OutputStream? = withContext(Dispatchers.IO) {
        try {
            val parentPath = parentOf(path) ?: return@withContext null
            val parent = rootFor(parentPath) ?: return@withContext null
            val name = fileNameOf(path)
            // createFile() on some providers makes a second file when the
            // name already exists instead of overwriting — remove the old
            // one first so Paste never creates "name (1)" duplicates.
            parent.findFile(name)?.delete()
            val created = parent.createFile("application/octet-stream", name)
                ?: return@withContext null
            context.contentResolver.openOutputStream(created.uri)
        } catch (e: Exception) {
            null
        }
    }

    private fun sizeRecursive(doc: DocumentFile): Long {
        return try {
            if (doc.isFile) return doc.length()
            var total = 0L
            for (child in doc.listFiles()) total += sizeRecursive(child)
            total
        } catch (e: Exception) {
            0L
        }
    }

    private fun deleteRecursive(doc: DocumentFile): Boolean {
        return try {
            // DocumentFile.delete() on a directory is provider-defined:
            // some providers delete recursively, others refuse non-empty
            // dirs. Delete children first so this works on every provider.
            if (doc.isDirectory) {
                var ok = true
                for (child in doc.listFiles()) if (!deleteRecursive(child)) ok = false
                if (!doc.delete()) ok = false
                ok
            } else {
                doc.delete()
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
        // SAF-only: a restricted path opens through a SAF grant when one
        // covers it, otherwise Direct — which fails honestly with the
        // probe error the UI shows.
        if (SafHelper.covers(context, p)) return SafFileAccess(context)
        return direct
    }

    suspend fun list(path: String): List<FileItem> {
        val p = normalisePath(path)
        val primary = accessFor(p)
        return try {
            primary.list(p)
        } catch (e: Exception) {
            // Fallback: a SAF grant that was just revoked in system settings
            // gets one Direct attempt before the UI reports it as blocked.
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
        return copyWithProgress(srcPath, destPath) { _, _ -> }
    }

    suspend fun copyWithProgress(
        srcPath: String,
        destPath: String,
        onProgress: (copied: Long, total: Long) -> Unit
    ): Boolean {
        // Source and destination layers are resolved INDEPENDENTLY, so
        // SAF<->Download and SAF-grant-A<->SAF-grant-B copies stream across
        // layers instead of failing inside one layer.
        val srcAccess = accessFor(srcPath)
        val destAccess = accessFor(destPath)
        return streamCopy(srcAccess, destAccess, srcPath, destPath, onProgress)
    }

    suspend fun move(srcPath: String, destPath: String): Boolean {
        val srcAccess = accessFor(srcPath)
        val destAccess = accessFor(destPath)
        // Same layer: use the layer's own move (Direct gets the fast
        // rename path). Cross-layer: stream-copy, then delete the source.
        if (srcAccess.mode == destAccess.mode) return srcAccess.move(srcPath, destPath)
        return if (copyWithProgress(srcPath, destPath) { _, _ -> }) delete(srcPath) else false
    }

    /**
     * MLBB first-class test: probes both SAF-reachable MLBB folders.
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
        // HONEST LIMIT: reachable only with root. SAF cannot read /data/data
        // on Android 16 — no app-level access can. Never claim otherwise.
        const val MLBB_PRIVATE_DATA = "/data/data/com.mobile.legends"
    }
}
