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

package com.sphereon.statuslist.spi

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.statuslist.CredentialStatusReference
import com.sphereon.statuslist.ResolvedStatus
import kotlinx.serialization.json.JsonObject

/**
 * One credential-status mechanism a verifier understands (IETF Token Status List, W3C Bitstring
 * Status List, ...). Implementations are contributed as a multibinding `Set`; a verifier injects the
 * set and, when it is non-empty, runs every member over each received credential.
 *
 * The split between [references] (cheap, synchronous shape detection) and [resolve] (the network +
 * crypto resolution of one reference) lets the verifier first decide whether a credential carries any
 * status this deployment can check, then resolve only what it recognizes.
 */
interface CredentialStatusVerifier {
    /** Stable mechanism id, e.g. `token_status_list` or `bitstring_status_list`. */
    val mechanism: String

    /**
     * Inspect a credential's claims (its decoded JSON payload) and return every status reference this
     * verifier recognizes. Empty when the credential carries no reference this mechanism understands.
     */
    fun references(credentialClaims: JsonObject): List<CredentialStatusReference>

    /** Resolve the live status of one [reference] this verifier produced. */
    suspend fun resolve(reference: CredentialStatusReference): IdkResult<ResolvedStatus, IdkError>
}
