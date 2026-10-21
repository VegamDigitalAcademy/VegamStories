package com.vegamstories.app.presentation.shortcuts

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.vegamstories.app.R
import com.vegamstories.app.di.ApplicationScope
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.usecase.GetCategoriesUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the app-icon long-press menu in sync with the story categories.
 * Categories come from the server, so these are dynamic shortcuts: when a category is
 * added, renamed or removed on the server, the menu follows after the next refresh.
 */
@Singleton
class AppShortcutsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getCategories: GetCategoriesUseCase,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    fun start() {
        appScope.launch {
            getCategories()
                .filter { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { publish(it) }
        }
    }

    /** Call when the user opens a shortcut; launchers use this to rank shortcuts. */
    fun reportUsed(target: ShortcutTarget) {
        val id = target.shortcutIdOrNull() ?: return
        ShortcutManagerCompat.reportShortcutUsed(context, id)
    }

    private fun publish(categories: List<Category>) {
        // Launchers show about 4 entries; keep 3 categories + Search.
        val categoryShortcuts = categories
            .sortedBy { it.sortOrder }
            .take(MAX_CATEGORY_SHORTCUTS)
            .mapIndexed { index, category ->
                ShortcutInfoCompat.Builder(context, ShortcutTarget.Category(category.id).shortcutId())
                    .setShortLabel(category.name)
                    .setLongLabel(category.name)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_category))
                    .setIntent(ShortcutTarget.Category(category.id).toIntent(context))
                    .setRank(index)
                    .build()
            }

        val search = ShortcutInfoCompat.Builder(context, ShortcutTarget.Search.shortcutId())
            .setShortLabel(context.getString(R.string.shortcut_search_short))
            .setLongLabel(context.getString(R.string.shortcut_search_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_search))
            .setIntent(ShortcutTarget.Search.toIntent(context))
            .setRank(categoryShortcuts.size)
            .build()

        // Replaces the whole set, so removed categories disappear from the menu too.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, categoryShortcuts + search) }
    }

    private companion object {
        const val MAX_CATEGORY_SHORTCUTS = 3
    }
}

private fun ShortcutTarget.Category.shortcutId(): String = "category_$categoryId"

private fun ShortcutTarget.Search.shortcutId(): String = "search"

/** null for widget taps, which aren't launcher shortcuts. */
private fun ShortcutTarget.shortcutIdOrNull(): String? = when (this) {
    is ShortcutTarget.Category -> shortcutId()
    ShortcutTarget.Search -> ShortcutTarget.Search.shortcutId()
    is ShortcutTarget.Story -> null
}
