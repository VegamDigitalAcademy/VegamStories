package com.vegamstories.app.data.mapper

import com.vegamstories.app.data.local.entity.CategoryEntity
import com.vegamstories.app.data.local.entity.CategoryNameEntity
import com.vegamstories.app.data.local.entity.CategoryRow
import com.vegamstories.app.data.local.entity.LanguageEntity
import com.vegamstories.app.data.local.entity.StoryEntity
import com.vegamstories.app.data.local.entity.StoryLanguageRow
import com.vegamstories.app.data.local.entity.StoryPageEntity
import com.vegamstories.app.data.local.entity.StoryTranslationEntity
import com.vegamstories.app.data.local.entity.StoryWithStateRow
import com.vegamstories.app.data.remote.dto.CategoryDto
import com.vegamstories.app.data.remote.dto.LanguageDto
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.data.remote.dto.StoryTranslationDto
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.Language
import com.vegamstories.app.domain.model.ReadingProgress
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.domain.model.StoryVideo

// ---------- Remote (DTO) -> Local (Entity) ----------

fun CategoryDto.toEntity() = CategoryEntity(id = id, sortOrder = sortOrder)

/** Blank names are skipped. */
fun CategoryDto.toNameEntities(): List<CategoryNameEntity> = names
    .filter { (language, name) -> language.isNotBlank() && name.isNotBlank() }
    .map { (language, name) -> CategoryNameEntity(categoryId = id, language = language, name = name) }

fun StoryDto.toEntity() = StoryEntity(
    id = id,
    categoryId = categoryId,
    episodeNumber = episode,
    publishedAt = publishedAt,
    likeCount = likeCount,
)

/** The language versions worth showing: a language and a title; a second version in the same language is skipped. */
private fun StoryDto.validTranslations(): List<StoryTranslationDto> = translations
    .filter { it.language.isNotBlank() && it.title.isNotBlank() }
    .distinctBy { it.language }

fun StoryDto.toTranslationEntities(): List<StoryTranslationEntity> = validTranslations().map { t ->
    StoryTranslationEntity(
        storyId = id,
        language = t.language,
        title = t.title,
        summary = t.summary,
        coverImageUrl = t.coverUrl,
        pageCount = t.pages.size,
        body = t.text?.takeIf { it.isNotBlank() },
        videoTitle = t.video?.title,
        videoDescription = t.video?.description,
        videoThumbnailUrl = t.video?.thumbnailUrl,
        videoUrl = t.video?.url,
    )
}

fun StoryDto.toPageEntities(): List<StoryPageEntity> = validTranslations().flatMap { t ->
    t.pages.mapIndexed { index, page ->
        StoryPageEntity(storyId = id, language = t.language, pageIndex = index, imageUrl = page.imageUrl)
    }
}

fun LanguageDto.toEntity() = LanguageEntity(
    code = code.trim().lowercase(),
    name = name,
    nativeName = nativeName,
    sortOrder = sortOrder,
)

// ---------- Local (Entity) -> Domain ----------

/** A category without a name in any language shows its id. */
fun CategoryRow.toDomain() = Category(id = id, name = name ?: id, sortOrder = sortOrder)

fun StoryWithStateRow.toDomain(): Story {
    val progress = if (progressLastPageIndex != null && progressTotalPages != null) {
        ReadingProgress(
            storyId = story.id,
            lastPageIndex = progressLastPageIndex,
            totalPages = progressTotalPages,
            isCompleted = progressIsCompleted == true,
            updatedAt = progressUpdatedAt ?: 0L,
        )
    } else {
        null
    }
    val video = if (videoUrl != null && videoThumbnailUrl != null) {
        StoryVideo(
            title = videoTitle.orEmpty(),
            description = videoDescription.orEmpty(),
            thumbnailUrl = videoThumbnailUrl,
            videoUrl = videoUrl,
        )
    } else {
        null
    }
    return Story(
        id = story.id,
        language = language,
        categoryId = story.categoryId,
        categoryName = categoryName.orEmpty(),
        episodeNumber = story.episodeNumber,
        title = title,
        summary = summary,
        coverImageUrl = coverImageUrl,
        pageCount = pageCount,
        publishedAt = story.publishedAt,
        video = video,
        hasText = hasText,
        isFavorite = isFavorite,
        likeCount = story.likeCount,
        isDownloaded = isDownloaded,
        progress = progress,
    )
}

fun StoryPageEntity.toDomain() = StoryPage(index = pageIndex, imageUrl = imageUrl)

fun LanguageEntity.toDomain() = Language(code = code, name = name, nativeName = nativeName)

/** A language not in the saved list yet is shown by its code, e.g. "OR". */
fun StoryLanguageRow.toDomain() = Language(code = code, name = nativeName ?: code.uppercase(), nativeName = nativeName ?: code.uppercase())
