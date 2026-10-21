package com.vegamstories.app.data.mapper

import com.vegamstories.app.data.local.entity.CategoryEntity
import com.vegamstories.app.data.local.entity.StoryEntity
import com.vegamstories.app.data.local.entity.StoryPageEntity
import com.vegamstories.app.data.local.entity.StoryWithStateRow
import com.vegamstories.app.data.remote.dto.CategoryDto
import com.vegamstories.app.data.remote.dto.StoryDto
import com.vegamstories.app.domain.model.Category
import com.vegamstories.app.domain.model.ReadingProgress
import com.vegamstories.app.domain.model.Story
import com.vegamstories.app.domain.model.StoryPage
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
        isFavorite = isFavorite,
        likeCount = story.likeCount,
        isDownloaded = isDownloaded,
        progress = progress,
    )
}

fun StoryPageEntity.toDomain() = StoryPage(index = pageIndex, imageUrl = imageUrl)
