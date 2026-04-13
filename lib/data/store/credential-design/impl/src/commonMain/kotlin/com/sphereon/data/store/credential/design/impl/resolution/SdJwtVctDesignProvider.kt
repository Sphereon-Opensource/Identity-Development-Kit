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

package com.sphereon.data.store.credential.design.impl.resolution

import com.sphereon.data.store.credential.design.impl.mapper.SdJwtVctDesignMapper
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.resolution.CredentialDesignLayerResult
import com.sphereon.data.store.credential.design.resolution.DesignLayerProvider
import com.sphereon.sdjwt.vc.SdJwtVcTypeMetadata
import kotlinx.serialization.json.Json

class SdJwtVctDesignProvider(
    private val mapper: SdJwtVctDesignMapper = SdJwtVctDesignMapper(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DesignLayerProvider {
    override val sourceType = DesignSourceType.SD_JWT_VCT_METADATA
    override val authoritative = false

    override suspend fun resolveCredentialLayer(
        tenantId: String,
        input: ResolveCredentialDesignInput,
    ): CredentialDesignLayerResult? {
        val metadataJson = input.externalMetadata?.sdJwtVctMetadata ?: return null

        val metadata =
            try {
                json.decodeFromJsonElement(SdJwtVcTypeMetadata.serializer(), metadataJson)
            } catch (_: Exception) {
                // Ignored: SD-JWT VCT metadata could not be deserialized
                return null
            }

        val canonical = mapper.toCanonical(metadata, emptyList())

        return CredentialDesignLayerResult(
            displays = canonical.displays,
            claims = canonical.claims,
            providedFields =
                buildSet {
                    canonical.displays.forEach { add("display:${it.locale}") }
                    canonical.claims.forEach { add("claim:${it.path}") }
                },
        )
    }
}
