package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.repository.ContentLanguageRepository
import javax.inject.Inject

class SetContentLanguageUseCase @Inject constructor(
    private val repository: ContentLanguageRepository,
) {
    suspend operator fun invoke(code: String) = repository.setContentLanguage(code)
}
