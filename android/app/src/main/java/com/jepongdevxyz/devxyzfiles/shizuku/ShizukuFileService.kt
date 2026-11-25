package com.jepongdevxyz.devxyzfiles.shizuku

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Runs inside the Shizuku server process as shell (UID 2000) on a non-root
 * phone — never in the app's own process. Plain java.io.File calls here can
 * list /storage/emulated/0/Android/data and /Android/obb, which the app
 * process itself is denied by scoped storage.
 *
 * Started only by Shizuku.bindUserService(); the app never starts it
 * directly. Every method is defensive: any exception becomes a safe
 * false / null / empty result, never a crash in the privileged process.
 */
class ShizukuFileService : Service() {

    // Copy progress for large files (MLBB OBBs are GBs). Written by the
    // copy thread, read by the app through getCopyProgress()/getCopyTotal().
    @Volatile
    private var copyProgressBytes: Long = 0L

    @Volatile
    private var copyTotalBytes: Long = 0L

    private val binder = object : IDevxyzFilesService.Stub() {

        override fun list(path: String): Array<String>? {
            return try {
                val dir = File(path)
                if (!dir.isDirectory || !dir.canRead()) return null
                val children = dir.listFiles() ?: return emptyArray()
                children
                    .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                    .map { encode(it) }
                    .toTypedArray()
            } catch (e: Exception) {
                null
            }
        }

        override fun stat(path: String): String {
            return try {
                val f = File(path)
                if (!f.exists()) "" else encode(f)
            } catch (e: Exception) {
                ""
            }
        }

        override fun exists(path: String): Boolean {
            return try {
                File(path).exists()
            } catch (e: Exception) {
                false
            }
        }

        override fun canRead(path: String): Boolean {
            return try {
                File(path).canRead()
            } catch (e: Exception) {
                false
            }
        }

        override fun mkdir(path: String): Boolean {
            return try {
                File(path).mkdir()
            } catch (e: Exception) {
                false
            }
        }

        override fun mkdirs(path: String): Boolean {
            return try {
                File(path).mkdirs()
            } catch (e: Exception) {
                false
            }
        }

        override fun delete(path: String): Boolean {
            return try {
                deleteRecursive(File(path))
            } catch (e: Exception) {
                false
            }
        }

        override fun rename(oldPath: String, newPath: String): Boolean {
            return try {
                val src = File(oldPath)
                val dest = File(newPath)
                if (!src.exists() || dest.exists()) return false
                if (src.renameTo(dest)) return true
                // Cross-filesystem fallback: copy then delete.
                if (copyRecursive(src, dest)) deleteRecursive(src) else false
            } catch (e: Exception) {
                false
            }
        }

        override fun copy(srcPath: String, destPath: String): Boolean {
            return try {
                val src = File(srcPath)
                val dest = File(destPath)
                if (!src.exists()) return false
                copyProgressBytes = 0L
                copyTotalBytes = sizeRecursive(src)
                copyRecursive(src, dest)
            } catch (e: Exception) {
                false
            }
        }

        override fun getCopyProgress(): Long = copyProgressBytes

        override fun getCopyTotal(): Long = copyTotalBytes

        override fun move(srcPath: String, destPath: String): Boolean {
            return try {
                if (rename(srcPath, destPath)) true
                else if (copy(srcPath, destPath)) delete(srcPath) else false
            } catch (e: Exception) {
                false
            }
        }

        override fun getUid(): Int {
            return try {
                android.os.Process.myUid()
            } catch (e: Exception) {
                -1
            }
        }

        private fun encode(f: File): String {
            // name|isDir|size|lastModified — names cannot contain '|' on
            // Android filesystems in practice; a '|' is replaced defensively.
            val name = f.name.replace('|', '_')
            return "$name|${f.isDirectory}|${if (f.isDirectory) 0L else f.length()}|${f.lastModified()}"
        }

        private fun deleteRecursive(f: File): Boolean {
            if (f.isDirectory) {
                val children = f.listFiles() ?: return false
                var ok = true
                for (child in children) {
                    if (!deleteRecursive(child)) ok = false
                }
                if (!f.delete()) ok = false
                return ok
            }
            return f.delete()
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

        private fun copyRecursive(src: File, dest: File): Boolean {
            if (src.isDirectory) {
                if (!dest.exists() && !dest.mkdirs()) return false
                val children = src.listFiles() ?: return false
                var ok = true
                for (child in children) {
                    if (!copyRecursive(child, File(dest, child.name))) ok = false
                }
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
                            // Streaming: only 256 KB is ever in memory, even
                            // for multi-GB MLBB OBB files.
                            copyProgressBytes += read
                        }
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
