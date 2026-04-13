package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * JS platform: server identifier is not available (key resolution uses runBlocking on JVM/native).
 * OAuth2 authorization server is a server-side component; JS/wasmJs targets provide a null stub
 * so the DI graph is complete for test compilation.
 */
@ContributesTo(SessionScope::class)
interface JsOAuth2ServerIdentifierModule {

    @Provides
    @SingleIn(SessionScope::class)
    @Named("oauth2.serverIdentifier")
    fun provideServerIdentifier(): ManagedIdentifierOptsOrResult? = null
}
