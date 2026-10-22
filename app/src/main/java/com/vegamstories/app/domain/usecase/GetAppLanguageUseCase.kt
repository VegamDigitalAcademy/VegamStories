package com.vegamstories.app.domain.usecase

import com.vegamstories.app.domain.model.AppLanguage
import com.vegamstories.app.domain.repository.LanguageRepository
import javax.inject.Inject

class GetAppLanguageUseCase @Inject constructor(
    private val repository: LanguageRepository,
) {
    operator fun invoke(): AppLanguage = repository.getLanguage()
}
