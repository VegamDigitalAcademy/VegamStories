package com.vegam.myapplication.domain.usecase

import com.vegam.myapplication.domain.model.AppLanguage
import com.vegam.myapplication.domain.repository.LanguageRepository
import javax.inject.Inject

class GetAppLanguageUseCase @Inject constructor(
    private val repository: LanguageRepository,
) {
    operator fun invoke(): AppLanguage = repository.getLanguage()
}
