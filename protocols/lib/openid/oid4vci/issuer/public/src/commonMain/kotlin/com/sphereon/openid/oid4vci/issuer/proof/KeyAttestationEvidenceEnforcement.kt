/*
 * (C) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.proof

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding
import kotlinx.serialization.json.JsonObject

/**
 * Optional production hook for deployments that must bind OID4VCI key attestations to persisted
 * Wallet Unit evidence. The generic issuer verifier keeps JOSE, nonce, and holder-key checks;
 * implementations validate status-list records, stored KA evidence, and trust evidence.
 */
interface KeyAttestationEvidenceEnforcer {
    suspend fun enforce(request: KeyAttestationEvidenceEnforcementRequest): IdkResult<VerifiedKeyAttestation, IdkError>
}

@ContributesTo(SessionScope::class)
interface KeyAttestationEvidenceEnforcerOptionalProvider {
    @OptionalBinding
    val optionalKeyAttestationEvidenceEnforcer: KeyAttestationEvidenceEnforcer? get() = null
}

data class KeyAttestationEvidenceEnforcementRequest(
    val keyAttestationJwt: String,
    val header: JsonObject,
    val claims: JsonObject,
    val evidence: VerifiedKeyAttestation,
)
