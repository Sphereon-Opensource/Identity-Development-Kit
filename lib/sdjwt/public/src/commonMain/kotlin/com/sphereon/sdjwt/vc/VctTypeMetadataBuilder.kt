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

package com.sphereon.sdjwt.vc

import com.sphereon.core.compat.JsExportCompat

/**
 * Source-agnostic description of a credential type's localized metadata, decoupled from where the
 * data originates. [buildSdJwtVcTypeMetadata] turns it into a wire-shaped [SdJwtVcTypeMetadata]
 * (SD-JWT VC type metadata, draft-ietf-oauth-sd-jwt-vc §6).
 *
 * The split is deliberate: the IDK config-driven issuer translates its `oid4vci.issuer` config into
 * this input, and the EDK/VDX semantic catalog/profile model can produce the very same input from a
 * richer source — both then share this one pure builder so the VCT shape is defined in a single
 * place. Keeping it a plain data class with no I/O means callers can unit-test the mapping and
 * compose it however they like.
 */
@JsExportCompat
data class VctTypeMetadataInput(
    val vct: String,
    /** Optional locale-neutral name/description; when null they default to the first display entry. */
    val name: String? = null,
    val description: String? = null,
    val displays: List<VctDisplayInput> = emptyList(),
    val claims: List<VctClaimInput> = emptyList(),
)

/** One per-locale credential display (maps to [DisplayInformation] + its `rendering.simple`). */
@JsExportCompat
data class VctDisplayInput(
    val locale: String,
    val name: String,
    val description: String? = null,
    val logoUri: String? = null,
    val logoAltText: String? = null,
    val backgroundImageUri: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
)

/**
 * One claim with its per-locale display labels (maps to [ClaimInformation]).
 *
 * [sd] defaults to [ClaimSdMetadata.ALWAYS]: SD-JWT VC claims are individually selectively
 * disclosable, and consumers (e.g. a verifier's DCQL claim picker) treat `sd == always` as a
 * holder-/verifier-toggleable claim and any other value as always-present. Mark a claim
 * [ClaimSdMetadata.ALLOWED] or [ClaimSdMetadata.NEVER] to make it required (always included).
 */
@JsExportCompat
data class VctClaimInput(
    val path: List<String?>,
    val mandatory: Boolean = false,
    val sd: ClaimSdMetadata = ClaimSdMetadata.ALWAYS,
    val displays: List<VctClaimDisplayInput> = emptyList(),
)

/** One per-locale claim display label/description (maps to [ClaimDisplayMetadata]). */
@JsExportCompat
data class VctClaimDisplayInput(
    val locale: String,
    val label: String,
    val description: String? = null,
)

/**
 * Pure mapping from a source-agnostic [VctTypeMetadataInput] to a wire-shaped [SdJwtVcTypeMetadata].
 *
 * No I/O, no platform dependencies: the same function backs the IDK config-driven issuer today and
 * any future EDK/VDX semantic-catalog-driven source. A display entry only carries a `rendering.simple`
 * block when at least one of its logo / background / colour fields is set, so empty branding does not
 * emit a hollow `rendering` object. Empty `display` / `claims` collapse to `null` rather than `[]`,
 * matching how hand-authored type metadata is written.
 */
fun buildSdJwtVcTypeMetadata(input: VctTypeMetadataInput): SdJwtVcTypeMetadata {
    val displays =
        input.displays.map { d ->
            val simple =
                if (d.logoUri != null || d.backgroundImageUri != null || d.backgroundColor != null || d.textColor != null) {
                    SimpleRenderingMethod(
                        logo = d.logoUri?.let { LogoMetadata(uri = it, altText = d.logoAltText) },
                        backgroundImage = d.backgroundImageUri?.let { BackgroundImageMetadata(uri = it) },
                        backgroundColor = d.backgroundColor,
                        textColor = d.textColor,
                    )
                } else {
                    null
                }
            DisplayInformation(
                locale = d.locale,
                name = d.name,
                description = d.description,
                rendering = simple?.let { RenderingMetadata(simple = it) },
            )
        }
    val claims =
        input.claims.map { c ->
            ClaimInformation(
                path = c.path,
                mandatory = c.mandatory,
                sd = c.sd,
                display =
                    c.displays
                        .map { ClaimDisplayMetadata(locale = it.locale, label = it.label, description = it.description) }
                        .ifEmpty { null },
            )
        }
    val first = input.displays.firstOrNull()
    return SdJwtVcTypeMetadata(
        vct = input.vct,
        name = input.name ?: first?.name,
        description = input.description ?: first?.description,
        display = displays.ifEmpty { null },
        claims = claims.ifEmpty { null },
    )
}
