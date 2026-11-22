package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.ContentLanguages
import com.vegamstories.app.domain.repository.ContentLanguageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContentLanguagesUseCase @Inject constructor(
    private val repository: ContentLanguageRepository,
) {
    operator fun invoke(): Flow<ContentLanguages> = repository.contentLanguages
}
