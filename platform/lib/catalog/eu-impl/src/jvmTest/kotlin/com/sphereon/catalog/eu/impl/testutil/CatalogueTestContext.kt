/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.catalog.eu.impl.testutil

import com.sphereon.catalog.eu.impl.digest.CatalogueEntryDigestVerifier
import com.sphereon.catalog.eu.impl.publish.CatalogueXmlPublisher
import com.sphereon.catalog.eu.impl.publish.CatalogueXmlSigner
import com.sphereon.catalog.eu.impl.signature.CatalogueSignatureVerifier
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.resolver.TrustListResolver
import dev.whyoleg.cryptography.CryptographyProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@ContributesTo(SessionScope::class)
interface CatalogueVerifiersGraph {
    val catalogueSignatureVerifier: CatalogueSignatureVerifier
    val catalogueEntryDigestVerifier: CatalogueEntryDigestVerifier
    val catalogueXmlSigner: CatalogueXmlSigner
    val catalogueXmlPublisher: CatalogueXmlPublisher
}

@DependencyGraph(AppScope::class)
abstract class CatalogueTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
            @Provides trustListResolvers: Set<TrustListResolver>,
        ): CatalogueTestAppGraph
    }
}

/**
 * Builds the real DI graph (software KMS, XAdES validator) the catalogue verifiers run in.
 */
class CatalogueTestContext(
    sessionId: String,
    testInstance: Any,
) {
    val app: AppGraph =
        createGraphFactory<CatalogueTestAppGraph.Factory>()
            .create(
                application = testInstance,
                appId = testInstance.javaClass.name,
                profile = "console-log-profile",
                version = "version-example",
                rootScopeProvider = DefaultRootScopeProvider(),
                trustListResolvers = emptySet(),
            ).also { it.initRootScopeProvider() }

    private val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId, principalType = PrincipalType.USER)

    val signatureVerifier: CatalogueSignatureVerifier
        get() = (session.graph as CatalogueVerifiersGraph).catalogueSignatureVerifier

    val digestVerifier: CatalogueEntryDigestVerifier
        get() = (session.graph as CatalogueVerifiersGraph).catalogueEntryDigestVerifier

    val signer: CatalogueXmlSigner
        get() = (session.graph as CatalogueVerifiersGraph).catalogueXmlSigner

    val publisher: CatalogueXmlPublisher
        get() = (session.graph as CatalogueVerifiersGraph).catalogueXmlPublisher

    init {
        val keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        val config = SoftwareKmsProviderConfig(id = "$sessionId-software-provider", cryptographyProvider = CryptographyProvider.Default.name)
        val provider =
            (app as SoftwareKmsProviderFactoryImpl.Graph)
                .softwareKmsProvider
                .create(config, session.asCoreApiServiceGraph().serviceExecution)
        keyManagerService.registerProvider(provider, makeDefaultKms = true)
    }
}
