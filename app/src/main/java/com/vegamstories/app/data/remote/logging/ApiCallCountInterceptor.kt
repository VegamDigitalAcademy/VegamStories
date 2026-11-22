package com.vegamstories.app.data.remote.logging

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation

/**
 * Debug builds only: hands every call to the server to [record] (ApiCallCounter, shown in
 * Settings → API calls), named after the Retrofit function that made it ("getStories"). Counted
 * when the call starts, so one that fails counts too.
 */
class ApiCallCountInterceptor(private val record: (apiName: String) -> Unit) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        record(request.apiName())
        return chain.proceed(request)
    }
}

/** The Retrofit function behind the call ("getStories"), else its method and last path part ("GET v1.json"). */
internal fun Request.apiName(): String =
    tag(Invocation::class.java)?.method()?.name
        ?: "$method ${url.encodedPath.substringAfterLast('/')}"
