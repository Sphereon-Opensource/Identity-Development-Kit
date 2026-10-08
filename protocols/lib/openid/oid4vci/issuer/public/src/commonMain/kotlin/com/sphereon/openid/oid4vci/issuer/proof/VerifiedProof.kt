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

package com.sphereon.openid.oid4vci.issuer.proof

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.json.JsonElement

/**
 * Wallet Unit key-attestation evidence extracted during credential-request proof
 * verification. The raw KA JWT is intentionally not carried here; PID revocation
 * recording only needs the resolved status reference and the enforced evidence summary.
 */
@JsExportCompat
data class VerifiedKeyAttestation(
    val evidenceId: String? = null,
    val walletUnitId: String? = null,
    val walletAccountId: String? = null,
    val issuer: String? = null,
    val subject: String? = null,
    val jwtId: String? = null,
    val expiresAtEpochSeconds: Long? = null,
    val keyStorage: List<String> = emptyList(),
    val userAuthentication: List<String> = emptyList(),
    val keyStorageStatusListUri: String? = null,
    val keyStorageStatusIndex: String? = null,
    val keyStorageStatusSubjectRef: String? = null,
    val keyStorageStatusMaintenanceExpiresAtEpochSeconds: Long? = null,
    val attestedKeyCount: Int = 0,
    @JsExportIgnoreCompat
    val evidence: Map<String, String> = emptyMap(),
)

/**
 * Result of a successful proof of possession verification.
 */
@JsExportCompat
data class VerifiedProof(
    val holderBindingKey: JsonElement,
    val holderIdentifier: String? = null,
    val keyId: String? = null,
    val algorithm: String? = null,
    val keyAttestation: VerifiedKeyAttestation? = null,
)
