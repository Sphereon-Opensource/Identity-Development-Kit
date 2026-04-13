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

import com.sphereon.data.store.credential.design.impl.mapper.Oid4vciClaimPathMapper
import com.sphereon.data.store.credential.design.model.ClaimLabel
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.resolution.CredentialDesignLayerResult
import com.sphereon.data.store.credential.design.resolution.DesignLayerProvider
import com.sphereon.openid.oid4vci.common.model.ClaimMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialMetadataClaim
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Design layer provider that imports OID4VCI credential configuration metadata
 * ([CredentialConfigurationSupported]) into canonical credential design displays and claims.
 *
 * Supports both:
 * - **OID4VCI 1.1**: `credential_metadata.display` + `credential_metadata.claims` (path-based)
 * - **OID4VCI 1.0 compat**: top-level `display` + `claims` (map-based, flat dotted keys)
 *
 * [authoritative] is `false` — local overrides always win over OID4VCI metadata.
 */
class Oid4vciCredentialConfigDesignProvider(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DesignLayerProvider {
    override val sourceType = DesignSourceType.OID4VCI_CREDENTIAL_CONFIGURATION
    override val authoritative = false

    override suspend fun resolveCredentialLayer(
        tenantId: String,
        input: ResolveCredentialDesignInput,
    ): CredentialDesignLayerResult? {
        val configJson = input.externalMetadata?.oid4vciCredentialConfiguration ?: return null

        val config =
            try {
                json.decodeFromJsonElement(CredentialConfigurationSupported.serializer(), configJson)
            } catch (_: Exception) {
                // Ignored: OID4VCI credential configuration could not be deserialized
                return null
            }

        // Prefer OID4VCI 1.1 credential_metadata; fall back to 1.0 top-level fields.
        return if (config.credentialMetadata != null) {
            resolveFrom11CredentialMetadata(config)
        } else {
            resolveFrom10TopLevel(config)
        }
    }

    // ---- OID4VCI 1.1 path-based mapping ----

    internal fun resolveFrom11CredentialMetadata(config: CredentialConfigurationSupported): CredentialDesignLayerResult {
        val metadata = config.credentialMetadata!!

        val displays =
            metadata.display?.map { dp ->
                LocalizedCredentialDisplay(
                    locale = dp.locale ?: "",
                    name = dp.name,
                    description = dp.description,
                )
            } ?: emptyList()

        val claims =
            metadata.claims?.mapIndexed { index, claim ->
                mapMetadataClaim(claim, index)
            } ?: emptyList()

        return CredentialDesignLayerResult(
            displays = displays,
            claims = claims,
            providedFields = buildProvidedFields(displays, claims),
        )
    }

    private fun mapMetadataClaim(
        claim: CredentialMetadataClaim,
        order: Int,
    ): ClaimPresentation {
        val path = Oid4vciClaimPathMapper.toDesignPath(claim.path)
        val labels =
            claim.display?.map { d ->
                ClaimLabel(
                    locale = d.locale ?: "",
                    label = d.name,
                )
            } ?: emptyList()
        return ClaimPresentation(
            path = path,
            labels = labels,
            mandatory = claim.mandatory ?: false,
            order = order,
        )
    }

    // ---- OID4VCI 1.0 map-based compatibility mapping ----

    internal fun resolveFrom10TopLevel(config: CredentialConfigurationSupported): CredentialDesignLayerResult {
        val displays =
            config.display?.map { dp ->
                LocalizedCredentialDisplay(
                    locale = dp.locale ?: "",
                    name = dp.name,
                    description = dp.description,
                )
            } ?: emptyList()

        // OID4VCI 1.0 claims: Map<String, ClaimMetadata> where key is the claim name.
        // credentialDefinition.credentialSubject may also carry claim display info.
        val claims =
            buildList {
                val topClaims = config.claims
                if (topClaims != null) {
                    addAll(mapClaimsMap(topClaims))
                }

                // Also handle VC-SD-JWT / JWT-VC claim subject if present.
                val subjectClaims = config.credentialDefinition?.credentialSubject
                if (subjectClaims != null && topClaims == null) {
                    addAll(mapClaimsMap(subjectClaims))
                }
            }

        return CredentialDesignLayerResult(
            displays = displays,
            claims = claims,
            providedFields = buildProvidedFields(displays, claims),
        )
    }

    /**
     * Maps a flat OID4VCI 1.0 claims map (claim name → [ClaimMetadata]) to [ClaimPresentation]
     * list. The map key becomes a single-segment [DesignClaimPath].
     */
    private fun mapClaimsMap(claimsMap: Map<String, ClaimMetadata>): List<ClaimPresentation> =
        claimsMap.entries.mapIndexed { index, (name, meta) ->
            val path = Oid4vciClaimPathMapper.toDesignPath(listOf(JsonPrimitive(name)))
            val labels =
                meta.display?.map { d ->
                    ClaimLabel(
                        locale = d.locale ?: "",
                        label = d.name,
                    )
                } ?: emptyList()
            ClaimPresentation(
                path = path,
                labels = labels,
                mandatory = meta.mandatory ?: false,
                order = index,
            )
        }

    // ---- Helpers ----

    private fun buildProvidedFields(
        displays: List<LocalizedCredentialDisplay>,
        claims: List<ClaimPresentation>,
    ): Set<String> =
        buildSet {
            displays.forEach { add("display:${it.locale}") }
            claims.forEach { add("claim:${it.path}") }
        }
}
