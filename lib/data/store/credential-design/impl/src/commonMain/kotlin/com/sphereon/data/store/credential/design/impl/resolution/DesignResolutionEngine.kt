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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.credential.design.model.AppliedDesignLayer
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.DerivedRenderHintsRecord
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.IssuerDesignRecord
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.model.ResolveEntityDesignInput
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import com.sphereon.data.store.credential.design.model.ResolvedIssuerDesign
import com.sphereon.data.store.credential.design.model.ResolvedVerifierDesign
import com.sphereon.data.store.credential.design.model.VerifierDesignRecord
import com.sphereon.data.store.credential.design.resolution.DesignLayerProvider
import kotlin.time.Clock

class DesignResolutionEngine(
    private val providers: List<DesignLayerProvider>,
) {
    suspend fun resolveCredential(
        tenantId: String,
        input: ResolveCredentialDesignInput,
        baseDesign: CredentialDesignRecord,
    ): IdkResult<ResolvedCredentialDesign, IdkError> {
        var displays = baseDesign.displays.toMutableList()
        var claims = baseDesign.claims.toMutableList()
        val renderVariants = mutableListOf<RenderVariantRecord>()
        var derivedHints: DerivedRenderHintsRecord? = null
        val appliedLayers = mutableListOf<AppliedDesignLayer>()
        val lockedFields = mutableMapOf<String, DesignSourceType>()

        for ((priority, provider) in providers.withIndex()) {
            val layerResult = provider.resolveCredentialLayer(tenantId, input) ?: continue

            appliedLayers.add(
                AppliedDesignLayer(
                    sourceType = provider.sourceType,
                    priority = priority,
                    authoritative = provider.authoritative,
                ),
            )

            // Merge displays by locale
            mergeDisplays(displays, layerResult.displays, lockedFields, provider) { it.locale }

            // Merge claims by path
            for (claim in layerResult.claims) {
                val pathKey = "claim:${claim.path}"
                if (pathKey in lockedFields) {
                    continue
                }
                val existingIdx = claims.indexOfFirst { it.path == claim.path }
                if (existingIdx >= 0) {
                    claims[existingIdx] = mergeClaim(claims[existingIdx], claim)
                } else {
                    claims.add(claim)
                }
                if (provider.authoritative) {
                    lockedFields[pathKey] = provider.sourceType
                }
            }

            // Merge render variants by kind + locale applicability
            mergeVariants(renderVariants, layerResult.renderVariants, lockedFields, provider)

            // Take derived hints from highest-priority provider that offers them
            if (layerResult.derivedRenderHints != null && derivedHints == null) {
                derivedHints = layerResult.derivedRenderHints
            }

            // Record provided field locks
            lockProvidedFields(layerResult.providedFields, lockedFields, provider)
        }

        return Ok(
            ResolvedCredentialDesign(
                design = baseDesign,
                renderVariants = renderVariants,
                derivedRenderHints = derivedHints,
                appliedLayers = appliedLayers,
                lockedFields = lockedFields,
                resolvedAt = Clock.System.now(),
            ),
        )
    }

    suspend fun resolveIssuer(
        tenantId: String,
        input: ResolveEntityDesignInput,
        baseDesign: IssuerDesignRecord,
    ): IdkResult<ResolvedIssuerDesign, IdkError> {
        val (displays, renderVariants, appliedLayers, lockedFields) =
            resolveEntity(
                baseDisplays = baseDesign.displays,
                localeOf = { it.locale },
            ) { provider, priority ->
                val layerResult = provider.resolveIssuerLayer(tenantId, input) ?: return@resolveEntity null
                EntityLayerResult(
                    displays = layerResult.displays,
                    renderVariants = layerResult.renderVariants,
                    providedFields = layerResult.providedFields,
                    sourceType = provider.sourceType,
                    priority = priority,
                    authoritative = provider.authoritative,
                )
            }

        return Ok(
            ResolvedIssuerDesign(
                design = baseDesign,
                renderVariants = renderVariants,
                appliedLayers = appliedLayers,
                lockedFields = lockedFields,
                resolvedAt = Clock.System.now(),
            ),
        )
    }

    suspend fun resolveVerifier(
        tenantId: String,
        input: ResolveEntityDesignInput,
        baseDesign: VerifierDesignRecord,
    ): IdkResult<ResolvedVerifierDesign, IdkError> {
        val (displays, renderVariants, appliedLayers, lockedFields) =
            resolveEntity(
                baseDisplays = baseDesign.displays,
                localeOf = { it.locale },
            ) { provider, priority ->
                val layerResult = provider.resolveVerifierLayer(tenantId, input) ?: return@resolveEntity null
                EntityLayerResult(
                    displays = layerResult.displays,
                    renderVariants = layerResult.renderVariants,
                    providedFields = layerResult.providedFields,
                    sourceType = provider.sourceType,
                    priority = priority,
                    authoritative = provider.authoritative,
                )
            }

        return Ok(
            ResolvedVerifierDesign(
                design = baseDesign,
                renderVariants = renderVariants,
                appliedLayers = appliedLayers,
                lockedFields = lockedFields,
                resolvedAt = Clock.System.now(),
            ),
        )
    }

    // ---- Shared helpers ----

    /**
     * Intermediate result from resolving a single entity layer.
     */
    private data class EntityLayerResult<D>(
        val displays: List<D>,
        val renderVariants: List<RenderVariantRecord>,
        val providedFields: Set<String>,
        val sourceType: DesignSourceType,
        val priority: Int,
        val authoritative: Boolean,
    )

    /**
     * Result of a full entity resolution pass.
     */
    private data class EntityResolutionResult<D>(
        val displays: List<D>,
        val renderVariants: List<RenderVariantRecord>,
        val appliedLayers: List<AppliedDesignLayer>,
        val lockedFields: Map<String, DesignSourceType>,
    )

    /**
     * Shared resolution logic for issuer and verifier designs.
     * Merges displays by locale, merges render variants with lockedFields guard,
     * and records field locks.
     */
    private suspend inline fun <D> resolveEntity(
        baseDisplays: List<D>,
        crossinline localeOf: (D) -> String,
        crossinline resolveLayer: suspend (DesignLayerProvider, Int) -> EntityLayerResult<D>?,
    ): EntityResolutionResult<D> {
        val displays = baseDisplays.toMutableList()
        val renderVariants = mutableListOf<RenderVariantRecord>()
        val appliedLayers = mutableListOf<AppliedDesignLayer>()
        val lockedFields = mutableMapOf<String, DesignSourceType>()

        for ((priority, provider) in providers.withIndex()) {
            val layerResult = resolveLayer(provider, priority) ?: continue

            appliedLayers.add(
                AppliedDesignLayer(
                    sourceType = layerResult.sourceType,
                    priority = layerResult.priority,
                    authoritative = layerResult.authoritative,
                ),
            )

            // Merge displays by locale
            mergeDisplays(displays, layerResult.displays, lockedFields, provider, localeOf)

            // Merge render variants with lockedFields guard
            mergeVariants(renderVariants, layerResult.renderVariants, lockedFields, provider)

            // Record provided field locks
            lockProvidedFields(layerResult.providedFields, lockedFields, provider)
        }

        return EntityResolutionResult(
            displays = displays,
            renderVariants = renderVariants,
            appliedLayers = appliedLayers,
            lockedFields = lockedFields,
        )
    }

    /**
     * Merge display items by locale into the target list, respecting lockedFields.
     */
    private inline fun <D> mergeDisplays(
        target: MutableList<D>,
        incoming: List<D>,
        lockedFields: MutableMap<String, DesignSourceType>,
        provider: DesignLayerProvider,
        localeOf: (D) -> String,
    ) {
        for (display in incoming) {
            val fieldKey = "display:${localeOf(display)}"
            if (fieldKey in lockedFields) {
                continue
            }
            val existingIdx = target.indexOfFirst { localeOf(it) == localeOf(display) }
            if (existingIdx >= 0) {
                target[existingIdx] = display
            } else {
                target.add(display)
            }
            if (provider.authoritative) {
                lockedFields[fieldKey] = provider.sourceType
            }
        }
    }

    /**
     * Merge render variants by kind + locale applicability, respecting lockedFields.
     */
    private fun mergeVariants(
        target: MutableList<RenderVariantRecord>,
        incoming: List<RenderVariantRecord>,
        lockedFields: MutableMap<String, DesignSourceType>,
        provider: DesignLayerProvider,
    ) {
        for (variant in incoming) {
            val variantKey = "variant:${variant.kind}:${variant.localeApplicability}"
            if (variantKey in lockedFields) {
                continue
            }
            target.add(variant)
            if (provider.authoritative) {
                lockedFields[variantKey] = provider.sourceType
            }
        }
    }

    /**
     * Record provided field locks from a layer.
     */
    private fun lockProvidedFields(
        providedFields: Set<String>,
        lockedFields: MutableMap<String, DesignSourceType>,
        provider: DesignLayerProvider,
    ) {
        for (field in providedFields) {
            if (provider.authoritative && field !in lockedFields) {
                lockedFields[field] = provider.sourceType
            }
        }
    }

    private fun mergeClaim(
        base: ClaimPresentation,
        override: ClaimPresentation,
    ): ClaimPresentation {
        // Merge labels by locale using a map for O(1) lookups
        val mergedLabels = base.labels.associateByTo(linkedMapOf()) { it.locale }
        for (label in override.labels) {
            mergedLabels[label.locale] = label
        }

        // Merge entry codes by union
        val mergedEntryCodes =
            if (override.entryCodes != null) {
                override.entryCodes // Higher priority provides complete list
            } else {
                base.entryCodes
            }

        return ClaimPresentation(
            path = base.path,
            labels = mergedLabels.values.toList(),
            mandatory = override.mandatory || base.mandatory,
            sdPolicy = override.sdPolicy,
            order = override.order,
            group = override.group ?: base.group,
            svgId = override.svgId ?: base.svgId,
            valueKind = override.valueKind ?: base.valueKind,
            widgetHint = override.widgetHint ?: base.widgetHint,
            markdownAllowed = override.markdownAllowed || base.markdownAllowed,
            entryCodes = mergedEntryCodes,
            unit = override.unit ?: base.unit,
        )
    }
}
