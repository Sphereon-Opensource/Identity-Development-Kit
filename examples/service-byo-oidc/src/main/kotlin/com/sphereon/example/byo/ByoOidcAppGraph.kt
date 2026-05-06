/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.example.byo

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.oauth2.jwt.validation.IdpConfig
import com.sphereon.oauth2.jwt.validation.JwtValidationConfig
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

/**
 * AppGraph for the BYO OIDC example.
 *
 * Metro merges every `@ContributesBinding` implementation on the runtime
 * classpath into this graph. That includes IDK's real JWT validation stack:
 * `DefaultJwtValidationService` (SessionScope) wraps `VerifyJwtCommandImpl`
 * (SessionScope) which delegates to `JwtServiceImpl` (SessionScope) and its
 * transitive crypto command chain. The `DefaultIdpRegistry` (AppScope)
 * consumes the [JwtValidationConfig] supplied here at graph-build time.
 *
 * There is nothing BYO-specific in this graph. What is "bring your own" about
 * the demo is the external IdP we point the validator at - a Keycloak
 * testcontainer in the E2E test, but any OIDC-compliant provider would work.
 * The validator is 100 % IDK.
 */
@DependencyGraph(AppScope::class)
abstract class ByoOidcAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
            // Supplied to DefaultIdpRegistry at AppScope construction time.
            @Provides @SingleIn(AppScope::class) jwtValidationConfig: JwtValidationConfig,
        ): ByoOidcAppGraph
    }
}

/**
 * Build a [ByoOidcAppGraph] wired to the given IdP.
 *
 * The [JwtValidationConfig] is built from the supplied [IdpConfig] and
 * handed to [DefaultIdpRegistry] via DI. `strictIssuerMatching` is left at
 * its safe default (true) so tokens from any other issuer fail closed.
 */
fun createByoOidcAppGraph(
    idpConfig: IdpConfig,
    application: Any = "byo-oidc-example",
    appId: String = "byo-oidc-example",
    profile: String = "production",
    version: String = "1.0.0",
): AppGraph {
    val jwtValidationConfig =
        JwtValidationConfig(
            enabled = true,
            defaultIdp = idpConfig,
        )
    val graph =
        createGraphFactory<ByoOidcAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
            jwtValidationConfig = jwtValidationConfig,
        )
    graph.initRootScopeProvider()
    return graph
}
