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

package com.sphereon.openid.oid4vci.issuer.format

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import kotlinx.serialization.json.JsonElement

/**
 * SPI for format-specific credential issuance.
 *
 * Implementations registered via `@ContributesIntoSet` for each supported format.
 */
@JsExportCompat
interface CredentialFormatHandler {
    val supportedFormat: String

    suspend fun canHandle(
        request: CredentialRequest,
        configuration: CredentialConfigurationSupported,
    ): Boolean

    suspend fun issueCredential(
        request: CredentialRequest,
        context: IssuanceContext,
    ): IdkResult<CredentialEnvelope, IdkError>
}

/**
 * Context passed to format handlers during credential issuance.
 */
@JsExportCompat
data class IssuanceContext(
    val subject: String,
    val clientId: String,
    val issuerIdentifier: String,
    val credentialConfigurationId: String,
    val credentialConfiguration: CredentialConfigurationSupported,
    val holderBindingKey: JsonElement?,
    val holderIdentifier: String? = null,
    val holderKeyId: String? = null,
    @JsExportIgnoreCompat
    val attributes: Map<String, JsonElement>,
    @JsExportIgnoreCompat
    val sdPolicies: Map<String, SdPolicy> = emptyMap(),
    @JsExportIgnoreCompat
    val mandatoryClaims: Set<String> = emptySet(),
    /** Key alias in the KMS for credential signing. Defaults to credentialConfigurationId. */
    val signingKeyAlias: String? = null,
    /** Key reference mode for the signing key identifier in the JWT protected header. */
    val signingKeyMode: SigningKeyMode = SigningKeyMode.None,
    /** Optional PEM file path for X.509 certificate chain (fallback when KMS key has no x5c). */
    val signingCertChainPath: String? = null,
    /**
     * Issuer-wide clock-skew tolerance, in seconds. `iat` on issued credentials is shifted
     * backward by this many seconds so wallets whose clocks are slightly ahead still accept
     * the credential as valid on receipt. Defaults to 60.
     * YAML: `sphereon.oid4vci.issuer.issuance-clock-skew-in-seconds`.
     */
    val issuanceClockSkewInSeconds: Long = 60L,
    /**
     * Per-credential validity window in days. When non-null, the handler emits an `exp` claim
     * at `iat + days × 86400`. When null, no `exp` claim is emitted — the SD-JWT VC / W3C VC
     * specs both treat `exp` as OPTIONAL, so absence means the credential has no expiration.
     * YAML: `sphereon.oid4vci.issuer.credentials.[<id>].expiration-in-days`.
     */
    val expirationInDays: Int? = null,
)

/**
 * Selective disclosure policy for a claim.
 */
@JsExportCompat
enum class SdPolicy {
    ALWAYS_DISCLOSED,
    SELECTIVELY_DISCLOSABLE,
    NEVER_DISCLOSED,
}

/**
 * Wrapper for an issued credential.
 */
@JsExportCompat
data class CredentialEnvelope(
    val credential: JsonElement,
    val format: String,
    val notificationId: String? = null,
    val deferred: Boolean = false,
)
