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

import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.EntityLocaleDesign
import com.sphereon.data.store.credential.design.model.ResolveEntityDesignInput
import com.sphereon.data.store.credential.design.resolution.DesignLayerProvider
import com.sphereon.data.store.credential.design.resolution.IssuerDesignLayerResult
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import kotlinx.serialization.json.Json

/**
 * Design layer provider that imports OID4VCI issuer-level display metadata
 * ([CredentialIssuerMetadata.display]) into canonical issuer design displays.
 *
 * Maps each `display[].name` + `display[].locale` entry to an [EntityLocaleDesign].
 * The `description` field on issuer display is not part of the OID4VCI spec, so it is
 * left null unless the incoming [display] carries it.
 *
 * [authoritative] is `false` — local overrides always win over OID4VCI metadata.
 */
class Oid4vciIssuerMetadataDesignProvider(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DesignLayerProvider {
    override val sourceType = DesignSourceType.OID4VCI_ISSUER_METADATA
    override val authoritative = false

    override suspend fun resolveIssuerLayer(
        tenantId: String,
        input: ResolveEntityDesignInput,
    ): IssuerDesignLayerResult? {
        val metadataJson = input.externalMetadata?.oid4vciIssuerMetadata ?: return null

        val metadata =
            try {
                json.decodeFromJsonElement(CredentialIssuerMetadata.serializer(), metadataJson)
            } catch (_: Exception) {
                // Ignored: OID4VCI issuer metadata could not be deserialized
                return null
            }

        val displays =
            metadata.display?.map { dp ->
                EntityLocaleDesign(
                    locale = dp.locale ?: "",
                    displayName = dp.name,
                    description = dp.description,
                )
            } ?: return null

        if (displays.isEmpty()) {
            return null
        }

        return IssuerDesignLayerResult(
            displays = displays,
            providedFields = displays.map { "display:${it.locale}" }.toSet(),
        )
    }
}
