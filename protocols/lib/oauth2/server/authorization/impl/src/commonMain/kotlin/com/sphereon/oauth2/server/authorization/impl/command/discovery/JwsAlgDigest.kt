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

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.validation.SUPPORTED_ID_TOKEN_SIGNING_ALGS
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError

// `jwsAlgToDigest` + `SUPPORTED_ID_TOKEN_SIGNING_ALGS` now live in
// com.sphereon.oauth2.common.validation so RP-side (common-impl) and OP-side (here) share one
// source of truth. This file retains only the OP-specific metadata consistency check below.

/**
 * Accepted `subject_types_supported` values. Pairwise pseudonymous subject computation is not
 * implemented — advertising `pairwise` would lie to RPs that compute user-specific identifiers
 * from the `sub` claim. Extending this set requires first implementing per-sector `sub` hashing.
 */
public val SUPPORTED_SUBJECT_TYPES: Set<String> = setOf("public")

/**
 * Validate discovery-metadata consistency against runtime capability. Runs on every metadata
 * build (cheap — just set / string operations) so misconfigurations surface on the first
 * OIDF discovery probe rather than days into a conformance run.
 *
 * Fails with [AuthorizationServerError.ServerError] on:
 *  - unsupported / unknown `idTokenSigningAlgValuesSupported` entries
 *  - `subjectTypesSupported` containing values other than `public`
 */
public fun validateServerMetadataConsistency(config: OAuth2ServerInstanceConfig): IdkResult<Unit, AuthorizationServerError> {
    val declaredAlgs = config.idTokenSigningAlgValuesSupported.orEmpty()
    val unsupportedAlgs = declaredAlgs.filterNot { it in SUPPORTED_ID_TOKEN_SIGNING_ALGS }
    if (unsupportedAlgs.isNotEmpty()) {
        return Err(
            AuthorizationServerError.ServerError(
                details =
                    "id_token_signing_alg_values_supported contains unsupported values: " +
                        "${unsupportedAlgs.joinToString(", ")} — supported: " +
                        SUPPORTED_ID_TOKEN_SIGNING_ALGS.joinToString(", "),
                exception = null,
            ),
        )
    }

    val unsupportedSubjectTypes = config.subjectTypesSupported.filterNot { it in SUPPORTED_SUBJECT_TYPES }
    if (unsupportedSubjectTypes.isNotEmpty()) {
        return Err(
            AuthorizationServerError.ServerError(
                details =
                    "subject_types_supported contains values IDK cannot produce: " +
                        "${unsupportedSubjectTypes.joinToString(", ")} — supported: " +
                        SUPPORTED_SUBJECT_TYPES.joinToString(", "),
                exception = null,
            ),
        )
    }

    return Ok(Unit)
}
