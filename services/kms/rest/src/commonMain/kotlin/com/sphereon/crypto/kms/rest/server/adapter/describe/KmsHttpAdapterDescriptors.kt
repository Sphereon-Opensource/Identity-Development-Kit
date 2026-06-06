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
                        operationId = "storeKey",
                        commandId = "kms.keys.store",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/keys/generate",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "generateKey",
                        commandId = "kms.keys.generate",
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
                        operationId = "providerStoreKey",
                        commandId = "providerStoreKey",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/providers/{providerId}/keys/generate",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "providerGenerateKey",
                        commandId = "providerGenerateKey",
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
