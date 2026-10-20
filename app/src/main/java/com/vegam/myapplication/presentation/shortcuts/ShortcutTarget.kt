package com.vegam.myapplication.presentation.shortcuts

import android.content.Context
import android.content.Intent
import com.vegam.myapplication.MainActivity

/** Where a launcher shortcut (long-press on the app icon) should take the user. */
sealed interface ShortcutTarget {
    data class Category(val categoryId: String) : ShortcutTarget
    data object Search : ShortcutTarget

    /** Intent stored inside the launcher shortcut. Extras must be plain strings. */
    fun toIntent(context: Context): Intent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW) // launcher shortcuts require an action
        when (this) {
            is Category -> intent
                .putExtra(EXTRA_TYPE, TYPE_CATEGORY)
                .putExtra(EXTRA_CATEGORY_ID, categoryId)
            Search -> intent.putExtra(EXTRA_TYPE, TYPE_SEARCH)
        }
        return intent
    }

    companion object {
        private const val EXTRA_TYPE = "vegam.shortcut.type"
        private const val EXTRA_CATEGORY_ID = "vegam.shortcut.category_id"
        private const val TYPE_CATEGORY = "category"
        private const val TYPE_SEARCH = "search"

        /** Reads a shortcut out of a launch intent; null for a normal app launch. */
        fun from(intent: Intent?): ShortcutTarget? = when (intent?.getStringExtra(EXTRA_TYPE)) {
            TYPE_CATEGORY -> intent?.getStringExtra(EXTRA_CATEGORY_ID)?.let { Category(it) }
            TYPE_SEARCH -> Search
            else -> null
        }
    }
}
