package com.vegam.myapplication.data.mapper

import com.vegam.myapplication.data.local.entity.CategoryEntity
import com.vegam.myapplication.data.local.entity.StoryEntity
import com.vegam.myapplication.data.local.entity.StoryPageEntity
import com.vegam.myapplication.data.local.entity.StoryWithStateRow
import com.vegam.myapplication.data.remote.dto.CategoryDto
import com.vegam.myapplication.data.remote.dto.StoryDto
import com.vegam.myapplication.domain.model.Category
import com.vegam.myapplication.domain.model.ReadingProgress
import com.vegam.myapplication.domain.model.Story
import com.vegam.myapplication.domain.model.StoryPage
import com.vegam.myapplication.domain.model.StoryVideo

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
