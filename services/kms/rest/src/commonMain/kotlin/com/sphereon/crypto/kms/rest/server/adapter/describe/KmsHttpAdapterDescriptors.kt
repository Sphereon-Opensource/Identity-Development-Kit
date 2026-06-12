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

package com.sphereon.crypto.kms.rest.server.adapter.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.crypto.kms.rest.server.adapter.CapabilitiesHttpAdapter
import com.sphereon.crypto.kms.rest.server.adapter.CertificatesHttpAdapter
import com.sphereon.crypto.kms.rest.server.adapter.EncryptionHttpAdapter
import com.sphereon.crypto.kms.rest.server.adapter.KeysHttpAdapter
import com.sphereon.crypto.kms.rest.server.adapter.ProvidersHttpAdapter
import com.sphereon.crypto.kms.rest.server.adapter.ResolversHttpAdapter
import com.sphereon.crypto.kms.rest.server.adapter.SignaturesHttpAdapter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * AppScope descriptor provider for KeysHttpAdapter.
 *
 * This provides metadata-only information about the adapter's endpoints,
 * allowing the HttpAdapterCatalog to be built at startup without instantiating
 * SessionScope adapters.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class KeysHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = KeysHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = "/keys",
                ),
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/keys/{aliasOrKid}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "getKey",
                        commandId = "kms.keys.get",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/keys/",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "listKeys",
                        commandId = "kms.keys.list",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/keys/",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "generateKey",
                        commandId = "kms.keys.generate",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/keys/import",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "importKey",
                        commandId = "kms.keys.import",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/keys/register",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "registerKeyReference",
                        commandId = "kms.keys.register",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.DELETE,
                        pathPattern = "/keys/{aliasOrKid}",
                        operationId = "deleteKey",
                        commandId = "kms.keys.delete",
                    ),
                ),
        )
}

/**
 * AppScope descriptor provider for ProvidersHttpAdapter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class ProvidersHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = ProvidersHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = "/providers",
                ),
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/providers/",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "listKeyProviders",
                        commandId = "listKeyProviders",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/providers/{providerId}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "getKeyProvider",
                        commandId = "getKeyProvider",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/providers/{providerId}/keys",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "providerListKeys",
                        commandId = "providerListKeys",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/providers/{providerId}/keys/{aliasOrKid}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "providerGetKey",
                        commandId = "providerGetKey",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/providers/{providerId}/keys",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "providerGenerateKey",
                        commandId = "providerGenerateKey",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/providers/{providerId}/keys/import",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "providerImportKey",
                        commandId = "providerImportKey",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.DELETE,
                        pathPattern = "/providers/{providerId}/keys/{aliasOrKid}",
                        operationId = "providerDeleteKey",
                        commandId = "providerDeleteKey",
                    ),
                ),
        )
}

/**
 * AppScope descriptor provider for CapabilitiesHttpAdapter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class CapabilitiesHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = CapabilitiesHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = ""),
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/capabilities",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "listCapabilities",
                        commandId = "kms.capabilities.list",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/providers/{providerId}/capabilities",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "getProviderCapabilities",
                        commandId = "kms.capabilities.get",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/providers/query",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "queryProviders",
                        commandId = "kms.providers.query",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/providers/query/best",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "queryBestProvider",
                        commandId = "kms.provider.query",
                    ),
                ),
        )
}

/**
 * AppScope descriptor provider for EncryptionHttpAdapter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class EncryptionHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = EncryptionHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = "/encryption"),
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/encryption/encrypt",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "encrypt",
                        commandId = "kms.encryption.encrypt",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/encryption/decrypt",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "decrypt",
                        commandId = "kms.encryption.decrypt",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/encryption/wrap",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "wrapKey",
                        commandId = "kms.encryption.wrap",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/encryption/unwrap",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "unwrapKey",
                        commandId = "kms.encryption.unwrap",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/encryption/key-agreement",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "performKeyAgreement",
                        commandId = "kms.encryption.agree",
                    ),
                ),
        )
}

/**
 * AppScope descriptor provider for CertificatesHttpAdapter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class CertificatesHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = CertificatesHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = HttpAdapterMount(serverPrefix = "", adapterBasePath = ""),
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/certificates/csr",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "generateCertificateSigningRequest",
                        commandId = "kms.certificates.csr",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/certificates/issue",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "issueCertificate",
                        commandId = "kms.certificates.issue",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/certificates/issue-from-csr",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "issueCertificateFromCsr",
                        commandId = "kms.certificates.issueFromCsr",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/certificates",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "listTrustedCertificateAliases",
                        commandId = "kms.certificates.list",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/certificates/{alias}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "getTrustedCertificate",
                        commandId = "kms.certificates.get",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/certificates/{alias}",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "storeTrustedCertificate",
                        commandId = "kms.certificates.store",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.DELETE,
                        pathPattern = "/certificates/{alias}",
                        operationId = "deleteTrustedCertificate",
                        commandId = "kms.certificates.delete",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/certificate-chains",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "listCertificateChainAliases",
                        commandId = "kms.certificateChains.list",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/certificate-chains/{alias}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "getCertificateChain",
                        commandId = "kms.certificateChains.get",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/certificate-chains/{alias}",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "storeCertificateChain",
                        commandId = "kms.certificateChains.store",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.DELETE,
                        pathPattern = "/certificate-chains/{alias}",
                        operationId = "deleteCertificateChain",
                        commandId = "kms.certificateChains.delete",
                    ),
                ),
        )
}

/**
 * AppScope descriptor provider for ResolversHttpAdapter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class ResolversHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = ResolversHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = "/resolvers",
                ),
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/resolvers/",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "listResolvers",
                        commandId = "kms.resolvers.list",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/resolvers/{resolverId}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "getResolver",
                        commandId = "kms.resolvers.get",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/resolvers/{resolverId}/resolve",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "resolvePublicKey",
                        commandId = "kms.resolvers.resolve",
                    ),
                ),
        )
}

/**
 * AppScope descriptor provider for SignaturesHttpAdapter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class SignaturesHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = SignaturesHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount =
                HttpAdapterMount(
                    serverPrefix = "",
                    adapterBasePath = "/signatures",
                ),
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/signatures/raw/create",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "createRawSignature",
                        commandId = "createRawSignature",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/signatures/raw/verify",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "verifyRawSignature",
                        commandId = "verifyRawSignature",
                    ),
                ),
        )
}
