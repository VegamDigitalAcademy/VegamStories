package com.vegamstories.app.data.mapper

import com.vegamstories.app.data.local.entity.CategoryEntity
import com.vegamstories.app.data.local.entity.LanguageEntity
import com.vegamstories.app.data.local.entity.StoryEntity
import com.vegamstories.app.data.local.entity.StoryPageEntity
import com.vegamstories.app.data.local.entity.StoryTextEntity
import com.vegamstories.app.data.local.entity.StoryTextRow
import com.vegamstories.app.data.local.entity.StoryWithStateRow
import com.vegamstories.app.data.remote.dto.CategoryDto
import com.vegamstories.app.data.remote.dto.LanguageDto
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.ReadingProgress
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryPage
import com.vegamstories.app.domain.model.StoryText
import com.vegamstories.app.domain.model.StoryVideo

// ---------- Remote (DTO) -> Local (Entity) ----------

fun CategoryDto.toEntity() = CategoryEntity(id = id, name = name, sortOrder = sortOrder)

fun StoryDto.toEntity() = StoryEntity(
    id = id,
    categoryId = categoryId,
    episodeNumber = episode,
    title = title,
    summary = summary,
    coverImageUrl = coverUrl,
    pageCount = pages.size,
    publishedAt = publishedAt,
    videoTitle = video?.title,
    videoDescription = video?.description,
    videoThumbnailUrl = video?.thumbnailUrl,
    videoUrl = video?.url,
    likeCount = likeCount,
)

fun StoryDto.toPageEntities(): List<StoryPageEntity> = pages.mapIndexed { index, page ->
    StoryPageEntity(
        storyId = id,
        pageIndex = index,
        imageUrl = page.imageUrl,
    )
}

fun LanguageDto.toEntity() = LanguageEntity(
    code = code.trim().lowercase(),
    name = name,
    nativeName = nativeName,
    sortOrder = sortOrder,
)

/** Empty texts and a second text in the same language are skipped. Any language code is kept. */
fun StoryDto.toTextEntities(): List<StoryTextEntity> = texts
    .filter { it.language.isNotBlank() && it.body.isNotBlank() }
    .distinctBy { it.language }
    .map { StoryTextEntity(storyId = id, language = it.language, body = it.body) }

// ---------- Local (Entity) -> Domain ----------

fun CategoryEntity.toDomain() = Category(id = id, name = name, sortOrder = sortOrder)

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
    val video = if (story.videoUrl != null && story.videoThumbnailUrl != null) {
        StoryVideo(
            title = story.videoTitle.orEmpty(),
            description = story.videoDescription.orEmpty(),
            thumbnailUrl = story.videoThumbnailUrl,
            videoUrl = story.videoUrl,
        )
    } else {
        null
    }
    return Story(
        id = story.id,
        categoryId = story.categoryId,
        categoryName = categoryName.orEmpty(),
        episodeNumber = story.episodeNumber,
        title = story.title,
        summary = story.summary,
        coverImageUrl = story.coverImageUrl,
        pageCount = story.pageCount,
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

/** A language the app hasn't loaded yet is shown by its code, e.g. "OR". */
fun StoryTextRow.toDomain() = StoryText(
    languageCode = text.language,
    languageName = nativeName ?: text.language.uppercase(),
    body = text.body,
)
