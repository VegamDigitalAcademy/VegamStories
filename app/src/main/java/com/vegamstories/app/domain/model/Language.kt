package com.vegamstories.app.domain.model

/**
 * A language stories can be in, managed on the server (admin site): e.g. code "or",
 * [name] "Odia", [nativeName] "ଓଡ଼ିଆ" (what the app shows).
 */
data class Language(
    val code: String,
    val name: String,
    val nativeName: String,
)
