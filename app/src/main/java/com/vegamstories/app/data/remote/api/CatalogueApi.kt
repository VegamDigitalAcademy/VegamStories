package com.vegamstories.app.data.remote.api

import com.vegamstories.app.BuildConfig
import com.vegamstories.app.data.remote.dto.CataloguePointerDto
import com.vegamstories.app.data.remote.dto.SupabaseCatalogueDto
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * The story catalogue in R2 (media.vegamstories.com), written by the admin site (vegam-admin)
 * whenever stories change: a small pointer file, and the catalogue versions it points to.
 * No Supabase key on these requests (see NetworkModule).
 */
interface CatalogueApi {
    /** Which catalogue version is current, and where it is. */
    @GET
    suspend fun getPointer(@Url url: String = BuildConfig.CATALOGUE_URL): CataloguePointerDto

    /** One catalogue version: the published stories in every language, the categories and the languages. */
    @GET
    suspend fun getCatalogue(@Url url: String): SupabaseCatalogueDto
}
