/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.vault.portability

import com.sphereon.data.store.vault.VaultDigest
import com.sphereon.data.store.vault.VaultExportRecipient
import kotlinx.serialization.Serializable

@Serializable
enum class TdfProviderConformance {
    STANDARD_COMPLIANT,
    TEST_ONLY,
}

@Serializable
data class TdfEnvelopeCapabilities(
    val conformance: TdfProviderConformance,
    val specVersion: String,
    val mediaType: String,
    val streamingProtect: Boolean,
    val streamingOpen: Boolean,
) {
    init {
        require(specVersion.isNotBlank()) { "specVersion must not be blank" }
        require(mediaType.isNotBlank()) { "mediaType must not be blank" }
    }
}

@Serializable
data class TdfProtectRequest(
    val recipient: VaultExportRecipient,
    val policyBinding: String,
    val plaintextDigest: VaultDigest,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(policyBinding.isNotBlank()) { "policyBinding must not be blank" }
    }
}

@Serializable
data class TdfOpenRequest(
    val recipientRef: String,
    val unwrappingKeyRef: String,
) {
    init {
        require(recipientRef.isNotBlank()) { "recipientRef must not be blank" }
        require(unwrappingKeyRef.isNotBlank()) { "unwrappingKeyRef must not be blank" }
    }
}

@Serializable
data class TdfEnvelopeDescriptor(
    val providerId: String,
    val specVersion: String,
    val mediaType: String,
    val recipientRef: String,
    val plaintextDigest: VaultDigest,
    val protectedSizeBytes: Long,
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(recipientRef.isNotBlank()) { "recipientRef must not be blank" }
        require(protectedSizeBytes >= 0) { "protectedSizeBytes must be non-negative" }
    }
}

data class TdfOpenedEnvelope(
    val descriptor: TdfEnvelopeDescriptor,
    val plaintext: VaultByteSource,
)

/**
 * Provider SPI for real recipient-scoped OpenTDF envelopes. Production registration is accepted
 * only when [capabilities] declares [TdfProviderConformance.STANDARD_COMPLIANT].
 */
interface RecipientScopedTdfEnvelopeProvider {
    val providerId: String
    val capabilities: TdfEnvelopeCapabilities

    suspend fun protect(
        request: TdfProtectRequest,
        plaintext: VaultByteProducer,
        output: VaultByteSink,
    ): TdfEnvelopeDescriptor

    suspend fun open(
        request: TdfOpenRequest,
        envelope: VaultByteSource,
    ): TdfOpenedEnvelope
}

class TdfEnvelopeProviderRegistry(
    providers: List<RecipientScopedTdfEnvelopeProvider>,
) {
    private val byId = providers.associateBy { it.providerId }

    init {
        require(byId.size == providers.size) { "Duplicate TDF provider id" }
    }

    fun requireStandard(providerId: String): RecipientScopedTdfEnvelopeProvider {
        val provider = byId[providerId] ?: throw VaultPortabilityError.TdfProviderRequired()
        if (provider.capabilities.conformance != TdfProviderConformance.STANDARD_COMPLIANT) {
            throw VaultPortabilityError.TdfProviderRequired()
        }
        return provider
    }
}

/** Connects deterministic logical BagIt generation to explicit archive and TDF providers. */
class VaultPortableExportPipeline(
    private val bagItBuilder: BagItPackageBuilder,
    private val archiveProvider: BagItArchiveProvider,
    private val tdfProviders: TdfEnvelopeProviderRegistry,
) {
    init {
        require(archiveProvider.deterministic) { "BagIt archive provider must guarantee deterministic output" }
    }

    suspend fun export(
        payloads: List<BagItPayload>,
        bagInfo: Map<String, String>,
        tdfProviderId: String,
        request: TdfProtectRequest,
        output: VaultByteSink,
    ): TdfEnvelopeDescriptor {
        val packageSource = bagItBuilder.build(payloads, bagInfo)
        val archive = archiveProvider.archive(packageSource)
        return tdfProviders.requireStandard(tdfProviderId).protect(request, archive, output)
    }
}
