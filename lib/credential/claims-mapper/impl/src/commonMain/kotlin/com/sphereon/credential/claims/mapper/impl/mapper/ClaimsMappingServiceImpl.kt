/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.credential.claims.mapper.impl.mapper

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.error.ClaimMappingErrors
import com.sphereon.credential.claims.mapper.api.mapper.ClaimsMappingService
import com.sphereon.credential.claims.mapper.api.resolver.CredentialClaimResolver
import com.sphereon.credential.claims.mapper.api.model.ClaimMappingConfiguration
import com.sphereon.credential.claims.mapper.api.model.ClaimTransformation
import com.sphereon.credential.claims.mapper.api.model.CredentialMapping
import com.sphereon.credential.claims.mapper.api.model.DateInputFormat
import com.sphereon.credential.claims.mapper.api.model.CredentialWithId
import com.sphereon.credential.claims.mapper.api.model.MappedClaimsResult
import com.sphereon.credential.claims.mapper.impl.resolver.SdJwtClaimResolver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.collections.iterator

/**
 * Implementation of the low-level [ClaimsMappingService].
 *
 * This is a pure mapping service with no knowledge of persistence, storage, or DCQL.
 * It maps claims from verifiable credentials to a unified claims map based on
 * [ClaimMappingConfiguration]. It supports:
 * - Multiple credentials per configuration
 * - Priority-based claim merging
 * - Claim transformations
 * - Optional credentials with default values
 *
 * @param resolvers Set of credential resolvers for different credential formats (injected via multibinding)
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ClaimsMappingService>())
class ClaimsMappingServiceImpl(
    private val resolvers: Set<CredentialClaimResolver>
) : ClaimsMappingService {

    override suspend fun mapClaimsWithConfig(
        credentials: List<CredentialWithId>,
        config: ClaimMappingConfiguration
    ): IdkResult<MappedClaimsResult, IdkError> {
        // Index credentials by their ID for quick lookup
        val credentialsById = credentials.associateBy { it.credentialId }

        // Track metadata for the result
        val sourceCredentialIds = mutableSetOf<String>()
        val skippedOptionalCredentials = mutableSetOf<String>()
        val appliedDefaults = mutableSetOf<String>()

        // Collect mapped claims with their priorities
        // Map of targetClaimPath (as string) -> list of (value, priority) pairs
        val claimCandidates = mutableMapOf<String, MutableList<Pair<JsonElement, Int>>>()

        // Process each credential mapping in the configuration
        for (credentialMapping in config.credentialMappings) {
            val credential = credentialsById[credentialMapping.credentialId]

            if (credential == null) {
                if (credentialMapping.optional) {
                    skippedOptionalCredentials.add(credentialMapping.credentialId)
                    continue
                } else {
                    return Err(
                        ClaimMappingErrors.requiredCredentialMissing(credentialMapping.credentialId)
                    ).asResult()
                }
            }

            // Get resolver for this credential format
            val resolver = resolvers.find { it.supports(credential.format) }
                ?: return Err(
                    ClaimMappingErrors.unsupportedCredentialFormat(credential.format.value)
                ).asResult()

            // Process each claim mapping
            val mappingResult = processCredentialMapping(
                credential = credential,
                credentialMapping = credentialMapping,
                resolver = resolver,
                claimCandidates = claimCandidates,
                appliedDefaults = appliedDefaults
            )

            if (mappingResult.isErr) {
                return Err(mappingResult.error).asResult()
            }

            sourceCredentialIds.add(credential.credentialId)
        }

        // Merge claims by priority (highest priority wins)
        val mergedClaims = mutableMapOf<String, JsonElement>()
        for ((targetClaim, candidates) in claimCandidates) {
            // Sort by priority descending and take the first one
            val winner = candidates.maxByOrNull { it.second }
            if (winner != null) {
                mergedClaims[targetClaim] = winner.first
            }
        }

        return Ok(
            MappedClaimsResult(
                claims = mergedClaims,
                sourceCredentialIds = sourceCredentialIds,
                appliedDefaults = appliedDefaults,
                skippedOptionalCredentials = skippedOptionalCredentials
            )
        ).asResult()
    }

    override suspend fun extractAllClaims(
        credential: CredentialWithId
    ): IdkResult<Map<String, JsonElement>, IdkError> {
        val resolver = resolvers.find { it.supports(credential.format) }
            ?: return Err(
                ClaimMappingErrors.unsupportedCredentialFormat(credential.format.value)
            ).asResult()

        return resolver.extractAllClaims(
            credential = credential.payload,
            format = credential.format,
            disclosedClaims = credential.disclosedClaims
        )
    }

    /**
     * Process a single credential mapping and collect claim candidates.
     */
    private suspend fun processCredentialMapping(
        credential: CredentialWithId,
        credentialMapping: CredentialMapping,
        resolver: CredentialClaimResolver,
        claimCandidates: MutableMap<String, MutableList<Pair<JsonElement, Int>>>,
        appliedDefaults: MutableSet<String>
    ): IdkResult<Unit, IdkError> {
        for (claimMapping in credentialMapping.claimMappings) {
            val extractResult = resolver.extractClaim(
                credential = credential.payload,
                format = credential.format,
                claimPath = claimMapping.sourceClaimPath,
                disclosedClaims = credential.disclosedClaims
            )

            if (extractResult.isErr) {
                return Err(extractResult.error).asResult()
            }

            val extractedValue: JsonElement = extractResult.value
                ?: claimMapping.defaultValue?.also {
                    appliedDefaults.add(claimMapping.targetPathAsString())
                }
                ?: if (claimMapping.required) {
                    return Err(
                        ClaimMappingErrors.claimNotFound(
                            credentialId = credential.credentialId,
                            claimPath = claimMapping.sourcePathAsString()
                        )
                    ).asResult()
                } else {
                    continue
                }

            // Apply transformation if specified
            val transformedValue = applyTransformation(
                value = extractedValue,
                transformation = claimMapping.transformation,
                targetClaim = claimMapping.targetPathAsString(),
                credential = credential,
                resolver = resolver
            )

            if (transformedValue.isErr) {
                return Err(transformedValue.error).asResult()
            }

            // Add to candidates
            val candidates = claimCandidates.getOrPut(claimMapping.targetPathAsString()) { mutableListOf() }
            candidates.add(transformedValue.value to claimMapping.priority)
        }

        return Ok(Unit).asResult()
    }

    /**
     * Apply a transformation to a claim value.
     */
    private suspend fun applyTransformation(
        value: JsonElement,
        transformation: ClaimTransformation?,
        targetClaim: String,
        credential: CredentialWithId,
        resolver: CredentialClaimResolver
    ): IdkResult<JsonElement, IdkError> {
        if (transformation == null) {
            return Ok(value).asResult()
        }

        return try {
            val result = when (transformation) {
                is ClaimTransformation.Canonical -> value

                is ClaimTransformation.ToString -> {
                    val stringValue = when (value) {
                        is JsonPrimitive -> value.content
                        else -> value.toString()
                    }
                    JsonPrimitive(stringValue)
                }

                is ClaimTransformation.Concatenate -> {
                    val parts = mutableListOf<String>()

                    // Add prefix values
                    for (prefixPath in transformation.prefixPaths) {
                        val prefixResult = resolver.extractClaim(
                            credential = credential.payload,
                            format = credential.format,
                            claimPath = prefixPath,
                            disclosedClaims = credential.disclosedClaims
                        )
                        val prefixValue = prefixResult.getOrNull()
                        if (prefixValue != null) {
                            parts.add(prefixValue.toStringValue())
                        }
                    }

                    // Add primary value
                    parts.add(value.toStringValue())

                    // Add suffix values
                    for (suffixPath in transformation.suffixPaths) {
                        val suffixResult = resolver.extractClaim(
                            credential = credential.payload,
                            format = credential.format,
                            claimPath = suffixPath,
                            disclosedClaims = credential.disclosedClaims
                        )
                        val suffixValue = suffixResult.getOrNull()
                        if (suffixValue != null) {
                            parts.add(suffixValue.toStringValue())
                        }
                    }

                    JsonPrimitive(parts.joinToString(transformation.separator))
                }

                is ClaimTransformation.ToIsoDate -> {
                    val stringValue = value.toStringValue()
                    JsonPrimitive(convertToIsoDate(stringValue, transformation.inputFormat))
                }

                is ClaimTransformation.Substring -> {
                    val stringValue = value.toStringValue()
                    val endIndex = transformation.endIndex ?: stringValue.length
                    val substring = stringValue.substring(
                        transformation.startIndex.coerceAtMost(stringValue.length),
                        endIndex.coerceAtMost(stringValue.length)
                    )
                    JsonPrimitive(substring)
                }

                is ClaimTransformation.RegexReplace -> {
                    val stringValue = value.toStringValue()
                    val regex = Regex(transformation.pattern)
                    val replaced = regex.replace(stringValue, transformation.replacement)
                    JsonPrimitive(replaced)
                }

                is ClaimTransformation.Uppercase -> {
                    JsonPrimitive(value.toStringValue().uppercase())
                }

                is ClaimTransformation.Lowercase -> {
                    JsonPrimitive(value.toStringValue().lowercase())
                }
            }

            Ok(result).asResult()
        } catch (e: Exception) {
            Err(
                ClaimMappingErrors.transformationFailed(
                    targetClaim = targetClaim,
                    reason = e.message ?: "Unknown error",
                    cause = e
                )
            ).asResult()
        }
    }

    /**
     * Convert a date string to ISO 8601 format based on the specified input format.
     */
    private fun convertToIsoDate(value: String, format: DateInputFormat): String {
        return when (format) {
            DateInputFormat.EPOCH_SECONDS -> Instant.fromEpochSeconds(value.toLong()).toString()
            DateInputFormat.EPOCH_MILLIS -> Instant.fromEpochMilliseconds(value.toLong()).toString()
            DateInputFormat.ISO_8601 -> Instant.parse(value).toString()
            DateInputFormat.ISO_8601_DATE -> LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC).toString()
            DateInputFormat.AUTO -> autoDetectAndConvert(value)
        }
    }

    /**
     * Auto-detect the date format and convert to ISO 8601.
     * Tries ISO 8601 instant, then ISO 8601 date, then epoch numeric, passthrough on failure.
     */
    private fun autoDetectAndConvert(value: String): String {
        // Try ISO 8601 instant
        try {
            return Instant.parse(value).toString()
        } catch (_: Exception) { /* not ISO instant */ }

        // Try ISO 8601 date-only
        try {
            return LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC).toString()
        } catch (_: Exception) { /* not ISO date */ }

        // Try epoch (numeric string)
        val numeric = value.toLongOrNull()
        if (numeric != null) {
            // Heuristic: values > 1e12 are likely millis, otherwise seconds
            return if (numeric > 1_000_000_000_000L) {
                Instant.fromEpochMilliseconds(numeric).toString()
            } else {
                Instant.fromEpochSeconds(numeric).toString()
            }
        }

        // Passthrough — cannot detect format
        return value
    }

    /**
     * Convert a JsonElement to a string value.
     */
    private fun JsonElement.toStringValue(): String {
        return when (this) {
            is JsonPrimitive -> this.content
            is JsonArray -> this.joinToString(", ") { it.toStringValue() }
            is JsonObject -> this.toString()
        }
    }

    companion object {
        /**
         * Create a ClaimsMappingServiceImpl with default resolvers.
         */
        fun withDefaults(): ClaimsMappingServiceImpl {
            return ClaimsMappingServiceImpl(
                resolvers = setOf(
                    SdJwtClaimResolver()
                )
            )
        }
    }
}
