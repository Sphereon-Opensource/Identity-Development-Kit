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
 *
 */

package com.sphereon.credential.claims.mapper.impl.resolver

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.claims.mapper.api.error.ClaimMappingErrors
import com.sphereon.credential.claims.mapper.api.resolver.CredentialClaimResolver
import com.sphereon.openid.oid4vp.common.CredentialFormat
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.sdjwt.vc.ClaimPath
import com.sphereon.sdjwt.vc.ClaimPathLookupResult
import com.sphereon.sdjwt.vc.ClaimPathUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Claim resolver for SD-JWT-VC (Selective Disclosure JWT Verifiable Credential) credentials.
 *
 * This resolver uses the existing IDK SD-JWT infrastructure to parse the credential
 * and extract claims using ClaimPathUtils.
 *
 * Supported formats:
 * - SD_JWT_DC (dc+sd-jwt) - recommended format
 * - SD_JWT_VC (vc+sd-jwt) - older format
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<CredentialClaimResolver>())
class SdJwtClaimResolver : CredentialClaimResolver {
    override val supportedFormats: Set<CredentialFormat> =
        setOf(
            CredentialFormat.SD_JWT_DC,
            CredentialFormat.SD_JWT_VC,
        )

    override suspend fun extractAllClaims(
        credential: String,
        format: CredentialFormat,
        disclosedClaims: JsonObject?,
    ): IdkResult<Map<String, JsonElement>, IdkError> {
        // Use pre-decoded claims if provided, otherwise parse the SD-JWT
        val payload =
            disclosedClaims ?: run {
                val parseResult = parsePayload(credential)
                if (parseResult.isErr) {
                    return Err(parseResult.error).asResult()
                }
                parseResult.value
            }

        // Extract all claim paths from the payload
        val claimPaths = ClaimPathUtils.extractPaths(payload, includeArrayIndices = false)

        // Build the claims map
        val claims = mutableMapOf<String, JsonElement>()
        for (path in claimPaths) {
            val result = ClaimPathUtils.lookup(payload, path)
            if (result is ClaimPathLookupResult.Found) {
                val pathString = claimPathToString(path)
                claims[pathString] = result.value
            }
        }

        return Ok(claims).asResult()
    }

    override suspend fun extractClaims(
        credential: String,
        format: CredentialFormat,
        claimPaths: List<List<String>>,
        disclosedClaims: JsonObject?,
    ): IdkResult<Map<String, JsonElement>, IdkError> {
        // Use pre-decoded claims if provided, otherwise parse the SD-JWT
        val payload =
            disclosedClaims ?: run {
                val parseResult = parsePayload(credential)
                if (parseResult.isErr) {
                    return Err(parseResult.error).asResult()
                }
                parseResult.value
            }

        val claims = mutableMapOf<String, JsonElement>()
        for (pathSegments in claimPaths) {
            val path = ClaimPath(pathSegments)
            val result = ClaimPathUtils.lookup(payload, path)
            if (result is ClaimPathLookupResult.Found) {
                val pathString = pathSegments.joinToString(".")
                claims[pathString] = result.value
            }
        }

        return Ok(claims).asResult()
    }

    override suspend fun extractClaim(
        credential: String,
        format: CredentialFormat,
        claimPath: List<String>,
        disclosedClaims: JsonObject?,
    ): IdkResult<JsonElement?, IdkError> {
        // Use pre-decoded claims if provided, otherwise parse the SD-JWT
        val payload =
            disclosedClaims ?: run {
                val parseResult = parsePayload(credential)
                if (parseResult.isErr) {
                    return Err(parseResult.error).asResult()
                }
                parseResult.value
            }

        val path = ClaimPath(claimPath)
        return when (val result = ClaimPathUtils.lookup(payload, path)) {
            is ClaimPathLookupResult.Found -> {
                Ok(result.value).asResult()
            }

            is ClaimPathLookupResult.NotFound -> {
                Ok(null).asResult()
            }

            is ClaimPathLookupResult.Error -> {
                Err(
                    ClaimMappingErrors.claimExtractionFailed(
                        credentialId = "unknown",
                        reason = result.message,
                    ),
                ).asResult()
            }
        }
    }

    /**
     * Parse an SD-JWT string and return the full payload (with disclosures resolved).
     */
    private fun parsePayload(credential: String): IdkResult<JsonObject, IdkError> {
        val parseResult = SdJwtCodec.parse(credential)
        return parseResult.fold(
            success = { sdJwt ->
                Ok(sdJwt.payload.fullPayload).asResult()
            },
            failure = { error ->
                Err(
                    ClaimMappingErrors.claimExtractionFailed(
                        credentialId = "unknown",
                        reason = "Failed to parse SD-JWT: ${error.message.defaultMessage}",
                        cause = error.exception,
                    ),
                ).asResult()
            },
        )
    }

    /**
     * Convert a ClaimPath to a string representation.
     *
     * Uses dot notation for nested properties:
     * - ["given_name"] -> "given_name"
     * - ["address", "street"] -> "address.street"
     */
    private fun claimPathToString(path: ClaimPath): String = path.segments.filterNotNull().joinToString(".")

    companion object {
        /**
         * Convert a string path to a list of path segments.
         *
         * Supports:
         * - "given_name" -> ["given_name"]
         * - "address.street" -> ["address", "street"]
         */
        fun parseClaimPath(pathString: String): List<String> = pathString.split(".")
    }
}
