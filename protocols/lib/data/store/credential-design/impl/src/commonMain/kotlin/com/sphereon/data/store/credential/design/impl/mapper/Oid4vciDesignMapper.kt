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

package com.sphereon.data.store.credential.design.impl.mapper

import com.sphereon.data.store.credential.design.impl.resolution.Oid4vciCredentialConfigDesignProvider
import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.EntityLocaleDesign
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import com.sphereon.data.store.credential.design.resolution.CredentialDesignLayerResult
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.ImageProperties
import com.sphereon.openid.oid4vc.common.LogoProperties
import com.sphereon.openid.oid4vci.common.model.ClaimDisplay
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialMetadataClaim

/**
 * Bidirectional mapper between the canonical [ResolvedCredentialDesign] and
 * the OID4VCI [CredentialConfigurationSupported] wire format.
 *
 * Direction 1 (outbound): Design â†’ OID4VCI, for metadata generation.
 * Direction 2 (inbound):  OID4VCI â†’ Design, for importing external configurations.
 */
object Oid4vciDesignMapper {
    private val PROVIDER = Oid4vciCredentialConfigDesignProvider()

    // -------------------------------------------------------------------------
    // Direction 1: Design â†’ OID4VCI  (outbound, for metadata generation)
    // -------------------------------------------------------------------------

    /**
     * Converts a [ResolvedCredentialDesign] to a [CredentialConfigurationSupported] for the
     * given OID4VCI [format].
     *
     * - Display entries are built from [ResolvedCredentialDesign.design.displays], enriched
     *   with colors / logo / backgroundImage from the [RenderVariantRecord] whose
     *   `localeApplicability` contains that display's locale, falling back to the first
     *   variant when none match.
     * - Claims are converted via [Oid4vciClaimPathMapper.toOid4vciPath].
     * - Format-specific fields (`vct`, `doctype`, `credentialDefinition`) are derived from
     *   the first [DesignBinding] that carries the relevant value.
     */
    fun toCredentialConfiguration(
        design: ResolvedCredentialDesign,
        format: String,
    ): CredentialConfigurationSupported {
        val record = design.design

        // Map displays â€” each picks the render variant whose localeApplicability contains
        // that display's locale, falling back to the first variant when none match.
        val displays =
            record.displays
                .map { localDisplay ->
                    val selectedVariant = selectRenderVariant(design.renderVariants, localDisplay.locale)
                    buildDisplayProperties(localDisplay, selectedVariant)
                }.takeIf { it.isNotEmpty() }

        // Map claims â†’ OID4VCI 1.1 credential_metadata.claims (path-based)
        val metadataClaims =
            record.claims
                .map { claim ->
                    buildCredentialMetadataClaim(claim)
                }.takeIf { it.isNotEmpty() }

        val credentialMetadata =
            if (displays != null || metadataClaims != null) {
                CredentialMetadata(display = displays, claims = metadataClaims)
            } else {
                null
            }

        // Resolve format-specific fields from the first matching binding
        val binding = record.bindings.firstOrNull()

        val vct: String? =
            when (format) {
                CredentialFormat.SD_JWT_VC.value -> binding?.vct
                else -> null
            }

        val doctype: String? =
            when (format) {
                CredentialFormat.MSO_MDOC.value -> binding?.docType
                else -> null
            }

        val credentialDefinition: CredentialDefinition? =
            when (format) {
                CredentialFormat.W3C_VC_SD_JWT.value,
                CredentialFormat.JWT_VC_JSON.value,
                "ldp_vc",
                "jwt_vc_json-ld",
                -> {
                    val types =
                        buildList {
                            add("VerifiableCredential")
                            binding?.type?.let { add(it) }
                        }
                    CredentialDefinition(
                        type = types,
                        context = binding?.context?.let(::listOf),
                    )
                }

                else -> {
                    null
                }
            }

        return CredentialConfigurationSupported(
            format = format,
            vct = vct,
            doctype = doctype,
            credentialDefinition = credentialDefinition,
            credentialMetadata = credentialMetadata,
        )
    }

    // -------------------------------------------------------------------------
    // Direction 2: OID4VCI â†’ Design  (inbound, for import)
    // -------------------------------------------------------------------------

    /**
     * Imports an OID4VCI [CredentialConfigurationSupported] into the canonical
     * [CredentialDesignLayerResult].
     *
     * Delegates to [Oid4vciCredentialConfigDesignProvider] mapping logic, which already
     * handles both OID4VCI 1.1 (credential_metadata) and 1.0 (top-level) formats.
     *
     * @param configId The credential configuration ID used as a binding key.
     * @param config   The OID4VCI configuration to import.
     * @return A [CredentialDesignLayerResult] with displays, claims, and providedFields.
     */
    fun fromCredentialConfiguration(
        configId: String,
        config: CredentialConfigurationSupported,
    ): CredentialDesignLayerResult {
        // Delegate to the provider's mapping logic to avoid duplication.
        return if (config.credentialMetadata != null) {
            PROVIDER.resolveFrom11CredentialMetadata(config)
        } else {
            PROVIDER.resolveFrom10TopLevel(config)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers â€” outbound
    // -------------------------------------------------------------------------

    /**
     * Builds the OID4VCI **top-level issuer** `display` list from an issuer/entity design's
     * per-locale [EntityLocaleDesign] entries enriched with the matching [RenderVariantRecord]
     * (logo / background / colors), mirroring the credential-level [buildDisplayProperties] +
     * [selectRenderVariant] policy.
     *
     * Entries whose [EntityLocaleDesign.displayName] is null are skipped, because the OID4VCI
     * [DisplayProperties.name] is required.
     *
     * Logo / background URIs are passed through verbatim (they are stored RELATIVE and made
     * absolute per-tenant at serve time â€” see
     * [com.sphereon.data.store.credential.design.PublicDesignAssetPaths.toAbsolute]).
     */
    fun buildIssuerDisplayProperties(
        displays: List<EntityLocaleDesign>,
        renderVariants: List<RenderVariantRecord>,
    ): List<DisplayProperties> =
        displays.mapNotNull { entity ->
            val name = entity.displayName ?: return@mapNotNull null
            val variant = selectRenderVariant(renderVariants, entity.locale)
            DisplayProperties(
                name = name,
                locale = entity.locale.takeIf { it.isNotEmpty() },
                description = entity.description,
                backgroundColor = variant?.backgroundColor,
                textColor = variant?.textColor,
                logo =
                    variant?.logo?.let { ref ->
                        LogoProperties(uri = ref.uri, altText = ref.altText)
                    },
                backgroundImage =
                    variant?.backgroundImage?.let { ref ->
                        ImageProperties(uri = ref.uri)
                    },
            )
        }

    private fun buildDisplayProperties(
        localDisplay: LocalizedCredentialDisplay,
        variant: RenderVariantRecord?,
    ): DisplayProperties =
        DisplayProperties(
            name = localDisplay.name,
            locale = localDisplay.locale.takeIf { it.isNotEmpty() },
            description = localDisplay.description,
            backgroundColor = variant?.backgroundColor,
            textColor = variant?.textColor,
            logo =
                variant?.logo?.let { ref ->
                    LogoProperties(uri = ref.uri, altText = ref.altText)
                },
            backgroundImage =
                variant?.backgroundImage?.let { ref ->
                    ImageProperties(uri = ref.uri)
                },
        )

    private fun buildCredentialMetadataClaim(claim: ClaimPresentation): CredentialMetadataClaim {
        val oid4vciPath = Oid4vciClaimPathMapper.toOid4vciPath(claim.path)
        val displayEntries =
            claim.labels
                .map { label ->
                    ClaimDisplay(
                        name = label.label,
                        locale = label.locale.takeIf { it.isNotEmpty() },
                    )
                }.takeIf { it.isNotEmpty() }

        return CredentialMetadataClaim(
            path = oid4vciPath,
            mandatory = claim.mandatory.takeIf { it },
            display = displayEntries,
        )
    }

    // -------------------------------------------------------------------------
    // SD policy conversion helpers (design â†” issuer-format)
    // -------------------------------------------------------------------------

    /**
     * Converts a design-model [SdPolicy] to a dot-path string for use in
     * [com.sphereon.openid.oid4vci.issuer.format.SdPolicy] claim maps.
     *
     * Callers in the issuer layer should do the enum mapping themselves; this helper
     * provides the path string for a [ClaimPresentation].
     */
    fun claimPathString(claim: ClaimPresentation): String =
        claim.path.joinToString(".") { segment ->
            when (segment) {
                is ClaimPathSegment.Property -> segment.name
                is ClaimPathSegment.Index -> segment.index.toString()
                ClaimPathSegment.AnyArrayElement -> "*"
            }
        }
}
