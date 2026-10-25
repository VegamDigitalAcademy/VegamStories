package com.vegamstories.app.domain.model

/**
 * One shelf of the bookshelf: a series (category) with the stories finished in it, in episode
 * order. [total] is how many episodes the series has in the app's story language.
 */
data class SeriesShelf(
    val categoryId: String,
    val name: String,
    val finished: List<Story>,
    val total: Int,
) {
    /** How many of the series' episodes have been read (finished stories in other languages count too). */
    val readCount: Int get() = finished.size.coerceAtMost(total)

    /** Every episode of the series read: the shelf earns its sticker. */
    val isComplete: Boolean get() = total > 0 && finished.size >= total
}

/**
 * The bookshelf: a shelf for each series with at least one finished story, in the order Home shows
 * the series. [finished] are the stories read to the end; [catalogue] every story in the app's
 * story language, to count each series' episodes; [categories] give the series' names and order.
 */
fun buildBookshelf(finished: List<Story>, catalogue: List<Story>, categories: List<Category>): List<SeriesShelf> {
    val read = finished.filter { it.isRead }.distinctBy { it.id }
    if (read.isEmpty()) return emptyList()
    val order = categories.withIndex().associate { (i, category) -> category.id to i }
    val names = categories.associate { it.id to it.name }
    val episodes = catalogue.groupBy { it.categoryId }
    return read.groupBy { it.categoryId }
        .map { (categoryId, stories) ->
            val inCatalogue = episodes[categoryId].orEmpty().map { it.id }.toSet()
            SeriesShelf(
                categoryId = categoryId,
                name = names[categoryId] ?: stories.first().categoryName,
                finished = stories.sortedBy { it.episodeNumber },
                // Finished stories not in this language's catalogue (read in another) still count.
                total = inCatalogue.size + stories.count { it.id !in inCatalogue },
            )
        }
        .sortedWith(compareBy({ order[it.categoryId] ?: Int.MAX_VALUE }, { it.name }))
}
