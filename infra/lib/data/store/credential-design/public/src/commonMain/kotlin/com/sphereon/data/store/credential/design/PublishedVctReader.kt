package com.sphereon.data.store.credential.design

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding

/** Returns a public immutable projection for the current request tenant, or null. */
interface PublishedVctReader {
    suspend fun read(vctId: String): String?
}

@ContributesTo(SessionScope::class)
interface OptionalPublishedVctReader {
    @OptionalBinding val publishedVctReader: PublishedVctReader? get() = null
}
