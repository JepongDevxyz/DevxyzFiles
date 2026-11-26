package com.jepongdevxyz.devxyzfiles

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.core.content.getSystemService

/**
 * "Create desktop shortcut" (v1.2.0) — pins a launcher shortcut that
 * opens DevxyzFiles straight into the folder. Uses an explicit intent
 * to [MainActivity] with the [OPEN_PATH_EXTRA] extra, so no manifest
 * change is needed; Agent 2 reads the extra in onCreate and navigates
 * to it (falling back to Browse home when the folder no longer exists
 * or has no SAF grant).
 *
 * Returns false when the launcher does not support pinned shortcuts
 * (some OEM launchers) — the caller shows "Your launcher doesn't
 * support this." instead of pretending it worked.
 */
const val OPEN_PATH_EXTRA = "devxyzfiles_open_path"

fun requestPinShortcut(context: Context, label: String, path: String): Boolean {
    val manager = context.getSystemService<ShortcutManager>() ?: return false
    if (!manager.isRequestPinShortcutSupported) return false
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(OPEN_PATH_EXTRA, normalisePath(path))
    }
    val info = ShortcutInfo.Builder(context, "shortcut_" + normalisePath(path).hashCode())
        .setShortLabel(label)
        .setLongLabel(label)
        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()
    return try {
        manager.requestPinShortcut(info, null)
    } catch (e: Exception) {
        false
    }
}
