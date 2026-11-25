package com.jepongdevxyz.devxyzfiles

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences-backed stores (v1.2.0) — favourites, tags, sources,
 * trash records. Plain data only, no Android UI code. All paths are
 * stored canonicalised via [normalisePath] so a path matches regardless
 * of how the caller spelled it.
 */
private fun prefs(context: Context, name: String): SharedPreferences =
    context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

/** Starred folders/files — shown with a star badge and in Browse home. */
class FavouriteStore(context: Context) {
    private val prefs = prefs(context, "devxyzfiles_favourites")

    fun isFavourite(path: String): Boolean =
        prefs.getStringSet(KEY, emptySet())?.contains(normalisePath(path)) == true

    fun toggle(path: String): Boolean {
        val p = normalisePath(path)
        val set = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        // NOTE: Set.remove()/add() both return true on success — the new
        // state must be computed explicitly, not from their return value.
        val nowFav = if (set.contains(p)) {
            set.remove(p)
            false
        } else {
            set.add(p)
            true
        }
        prefs.edit().putStringSet(KEY, set).apply()
        return nowFav
    }

    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()) ?: emptySet()

    companion object {
        private const val KEY = "paths"
    }
}

/** Free-text tags per path. A tagged row shows a small tag mark; tapping a
 *  tag filters the current list to that tag (Agent 2 wires the filter). */
class TagStore(context: Context) {
    private val prefs = prefs(context, "devxyzfiles_tags")

    fun tagsFor(path: String): Set<String> =
        prefs.getStringSet(key(normalisePath(path)), emptySet()) ?: emptySet()

    fun setTags(path: String, tags: Set<String>) {
        val p = normalisePath(path)
        val clean = tags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val edit = prefs.edit()
        if (clean.isEmpty()) edit.remove(key(p)) else edit.putStringSet(key(p), clean)
        edit.apply()
    }

    fun addTag(path: String, tag: String) {
        val t = tag.trim()
        if (t.isEmpty()) return
        setTags(path, tagsFor(path) + t)
    }

    fun removeTag(path: String, tag: String) {
        setTags(path, tagsFor(path) - tag)
    }

    /** Every tagged path → its tags (for the tag filter view). */
    fun allTagged(): Map<String, Set<String>> {
        val out = mutableMapOf<String, Set<String>>()
        for ((k, v) in prefs.all) {
            if (k.startsWith(PREFIX) && v is Set<*>) {
                @Suppress("UNCHECKED_CAST")
                out[k.removePrefix(PREFIX)] = (v as Set<String>).toSet()
            }
        }
        return out
    }

    private fun key(path: String) = PREFIX + path

    companion object {
        private const val PREFIX = "tags:"
    }
}

/** Browse SOURCE list — folders the user added via "Set as source"
 *  (plus the built-in defaults the UI shows when the list is empty).
 *  Order is preserved (JSON array, not a set). */
class SourceStore(context: Context) {
    private val prefs = prefs(context, "devxyzfiles_sources")

    fun all(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getString(i) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(path: String): Boolean {
        val p = normalisePath(path)
        val list = all().toMutableList()
        if (list.contains(p)) return false
        list.add(p)
        save(list)
        return true
    }

    fun remove(path: String) {
        val list = all().toMutableList()
        if (list.remove(normalisePath(path))) save(list)
    }

    fun contains(path: String): Boolean = all().contains(normalisePath(path))

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "sources"
    }
}

/** One trashed item — Recently deleted. The file itself lives under
 *  [trashDir]; this record is how Restore knows where it came from. */
data class TrashRecord(
    val path: String,
    val name: String,
    val originalParent: String,
    val deletedAt: Long,
    val size: Long,
    val isDir: Boolean
)

/** Recently deleted (v1.2.0). Delete moves the item into [trashDir] and
 *  adds a record; Restore moves it back to [originalParent]; Delete
 *  forever removes both. Records older than 30 days are pruned by the
 *  caller (Agent 2, on app start) — the store itself never deletes. */
class TrashStore(context: Context) {
    private val prefs = prefs(context, "devxyzfiles_trash")
    private val appContext = context.applicationContext

    fun trashDir(): File = File(appContext.filesDir, "trash").apply { if (!exists()) mkdirs() }

    fun add(record: TrashRecord) {
        val list = all().toMutableList()
        list.removeAll { it.path == record.path }
        list.add(record)
        save(list)
    }

    fun all(): List<TrashRecord> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TrashRecord(
                    path = o.getString("path"),
                    name = o.getString("name"),
                    originalParent = o.getString("originalParent"),
                    deletedAt = o.getLong("deletedAt"),
                    size = o.getLong("size"),
                    isDir = o.getBoolean("isDir")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun remove(path: String) {
        val list = all().toMutableList()
        if (list.removeAll { it.path == path }) save(list)
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(list: List<TrashRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("path", r.path)
                    .put("name", r.name)
                    .put("originalParent", r.originalParent)
                    .put("deletedAt", r.deletedAt)
                    .put("size", r.size)
                    .put("isDir", r.isDir)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "records"
    }
}
