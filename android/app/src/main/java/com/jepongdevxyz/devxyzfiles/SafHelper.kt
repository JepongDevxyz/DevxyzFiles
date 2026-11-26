package com.jepongdevxyz.devxyzfiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * Storage Access Framework helpers: persisted folder grants, and the
 * initial-URI picker for Android/data and Android/obb.
 *
 * Honest note: on Android 13+ the system picker refuses Android/data and
 * Android/obb ("Can't use this folder"). On Honor (MagicOS, Files v16)
 * the picker also ignores the deep link into a single app folder and
 * there is no Uninstall-updates menu, so no SAF grant for Android/data
 * or Android/obb is possible there at all (proven on her Honor 400,
 * Nov 26, 2026 — tree URI and document URI both open at the storage
 * root). The honest fallback is openFolderInFilesApp below: ask the
 * system Files app itself to show the folder, no grant involved.
 */
object SafHelper {

    /** Persisted tree grants as (tree Uri, canonical root path). */
    fun persistedRoots(context: Context): List<Pair<Uri, String>> {
        return context.contentResolver.persistedUriPermissions.mapNotNull { perm ->
            val uri = perm.uri
            val path = treePath(uri) ?: return@mapNotNull null
            uri to path
        }
    }

    fun covers(context: Context, path: String): Boolean {
        val p = normalisePath(path)
        return persistedRoots(context).any { (_, root) ->
            p == root || p.startsWith("$root/")
        }
    }

    /** "primary:Android/data" -> "/storage/emulated/0/Android/data". */
    fun treePath(treeUri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val relative = docId.substringAfter(":", "")
            if (relative.isEmpty()) return null
            if (!docId.startsWith("primary:")) return null
            "/storage/emulated/0/$relative"
        } catch (e: Exception) {
            null
        }
    }

    /** Walks from a granted root DocumentFile down to the target path. */
    fun resolve(context: Context, root: DocumentFile, targetPath: String): DocumentFile? {
        val rootPath = treePath(root.uri) ?: return null
        val target = normalisePath(targetPath)
        if (target == rootPath) return root
        if (!target.startsWith("$rootPath/")) return null
        var current = root
        val segments = target.removePrefix("$rootPath/").split('/').filter { it.isNotEmpty() }
        for (segment in segments) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    /** Picker intent that opens at the requested folder when the system allows it. */
    fun requestIntent(path: String): Intent {
        val target = normalisePath(path)
        val relative = target.removePrefix("/storage/emulated/0/")
        val initial = Uri.parse(
            "content://com.android.externalstorage.documents/tree/primary%3A" +
                Uri.encode(relative).replace("%2F", "%2F")
        )
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
    }

    fun takePermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Grant already held, or the picker denied it — the probe will show it.
        }
    }

    /** Deep-link to the system Files app info page (for Uninstall updates). */
    fun filesAppSettingsIntent(context: Context): Intent {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:com.google.android.documentsui")
        }
        // Fall back to the AOSP DocumentsUI package if the Google one is absent.
        val resolve = context.packageManager.resolveActivity(intent, 0)
        return if (resolve != null) {
            intent
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:com.android.documentsui")
            }
        }
    }

    /** Ask the system Files app to open AT a folder (view only — NOT a grant).
     *
     * Plain ACTION_VIEW of the folder's document URI. DocumentsUI (the
     * system Files app) opens at that location when it can. This gives
     * DevxyzFiles NO access — the user just sees the folder in the Files
     * app and can copy files there by hand. On phones whose picker blocks
     * the SAF grant, this is the honest fallback: look, don't grant.
     */
    fun openFolderInFilesAppIntent(path: String): Intent {
        val target = normalisePath(path)
        val relative = target.removePrefix("/storage/emulated/0/")
        val document = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3A" +
                Uri.encode(relative)
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(document, "vnd.android.document/directory")
        }
    }

    /** Launches the intent above; returns false when no app handles it. */
    fun openFolderInFilesApp(context: Context, path: String): Boolean {
        return try {
            context.startActivity(openFolderInFilesAppIntent(path))
            true
        } catch (e: Exception) {
            false
        }
    }
}
