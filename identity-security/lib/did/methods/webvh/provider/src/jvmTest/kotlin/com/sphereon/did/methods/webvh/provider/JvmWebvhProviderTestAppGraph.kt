/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.methods.webvh.provider

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/**
 * JVM test app graph for `did:webvh` provider E2E testing. Merges DI contributions from:
 * - lib-did-methods-webvh-provider (lifecycle commands)
 * - lib-did-methods-webvh-resolver (resolver, replayer)
 * - lib-did-methods-webvh-public (capabilities, models)
 * - lib-crypto-data-integrity-proof-impl (AddProof / VerifyProof)
 * - lib-crypto-data-integrity-proof-eddsa-jcs-2022 (cryptosuite creator/verifier)
 * - lib-crypto-kms-provider-software (software KMS)
 * - lib-did-methods-key (did:key, used by witnesses + verification method lookup)
 * - lib-did-resolver-impl (DidResolverRegistry)
 */
@DependencyGraph(AppScope::class)
abstract class JvmWebvhProviderTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): JvmWebvhProviderTestAppGraph
    }
}

fun createJvmWebvhProviderTestAppGraph(
    application: Any,
    appId: String = "webvh-provider-test",
    profile: String = "test",
    version: String = "0.13.0-test",
): JvmWebvhProviderTestAppGraph {
    val graph =
        createGraphFactory<JvmWebvhProviderTestAppGraph.Factory>().create(
            application = application,
            appId = appId,
            profile = profile,
            version = version,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
