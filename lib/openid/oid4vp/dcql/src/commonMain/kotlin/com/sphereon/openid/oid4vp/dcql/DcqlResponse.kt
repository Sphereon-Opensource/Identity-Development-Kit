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

package com.sphereon.openid.oid4vp.dcql

import kotlinx.serialization.Serializable

/**
 * DCQL Response
 *
 * Describes how a DCQL query was satisfied by the holder's presented credentials.
 *
 * OpenID4VP 1.0 Section 6.5:
 * "The DCQL response is included in the presentation submission to inform the verifier
 * which credentials and claims were used to satisfy each part of the query. This allows
 * the verifier to validate that the query was properly satisfied."
 *
 * The response structure mirrors the query structure:
 * - If the query had `credentials`, the response has `credential_matches`
 * - If the query had `credential_sets`, the response has `credential_set_matches`
 *
 * Example:
 * ```json
 * {
 *   "credential_matches": [
 *     {
 *       "credential_id": "my_credential",
 *       "claims_satisfied": ["last_name", "first_name"]
 *     }
 *   ],
 *   "credential_set_matches": [
 *     {
 *       "credential_set_id": "0",
 *       "credential_id": "passport"
 *     }
 *   ]
 * }
 * ```
 *
 * @property credential_matches List of credential matches (corresponds to query's `credentials`)
 * @property credential_set_matches List of credential set matches (corresponds to query's `credential_sets`)
 *
 * @see DcqlCredentialMatch
 * @see DcqlCredentialSetMatch
 * @see DcqlQuery
 */
@Serializable
data class DcqlResponse(
    val credential_matches: List<DcqlCredentialMatch>? = null,
    val credential_set_matches: List<DcqlCredentialSetMatch>? = null,
)

/**
 * DCQL Credential Match
 *
 * Describes how a single credential query was satisfied.
 *
 * OpenID4VP 1.0 Section 6.5.1:
 * "A credential match indicates which credential from the presentation satisfies a specific
 * credential query from the DCQL request. It includes the credential ID from the query and
 * optionally lists which specific claims were satisfied."
 *
 * Example:
 * ```json
 * {
 *   "credential_id": "identity_credential",
 *   "claims_satisfied": ["first_name", "last_name", "birth_date"]
 * }
 * ```
 *
 * This indicates:
 * - The credential query with id "identity_credential" was satisfied
 * - The claims "first_name", "last_name", and "birth_date" were disclosed
 *
 * @property credential_id The ID from the credential query that was satisfied
 * @property claims_satisfied Optional list of claim paths that were satisfied (as JSON pointer strings)
 *
 * @see DcqlCredentialQuery.id
 * @see DcqlResponse
 */
@Serializable
data class DcqlCredentialMatch(
    val credential_id: String,
    val claims_satisfied: List<String>? = null,
)

/**
 * DCQL Credential Set Match
 *
 * Describes which option from a credential set query was satisfied.
 *
 * OpenID4VP 1.0 Section 6.5.2:
 * "A credential set match indicates which alternative from a credential set query was
 * chosen by the holder to satisfy the requirement. It references the credential set by
 * an index or ID and specifies which credential(s) were presented."
 *
 * Example:
 * ```json
 * {
 *   "credential_set_id": "0",
 *   "credential_id": "passport"
 * }
 * ```
 *
 * This indicates:
 * - The first credential set query (index 0) was satisfied
 * - The holder chose to present the "passport" option
 *
 * For credential sets with multiple credential IDs in the chosen option, the response
 * would include multiple credential_set_match entries with the same credential_set_id.
 *
 * @property credential_set_id Index or ID of the credential set query that was satisfied
 * @property credential_id The credential ID from the chosen option
 *
 * @see DcqlCredentialSetQuery
 * @see DcqlCredentialSetOption
 * @see DcqlResponse
 */
@Serializable
data class DcqlCredentialSetMatch(
    val credential_set_id: String,
    val credential_id: String,
)
