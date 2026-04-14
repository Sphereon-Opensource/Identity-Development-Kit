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

package com.sphereon.openid.oid4vci.common.dsl

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.openid.oid4vci.common.model.CredentialRequestProofs

/**
 * An HTTP endpoint with a bearer access token.
 * Eliminates repeated endpoint + accessToken field pairs across command args.
 */
@JsExportCompat
data class AuthenticatedEndpoint(
    val url: String,
    val accessToken: String,
)

/**
 * PKCE challenge pair per RFC 7636.
 */
@JsExportCompat
data class PkceChallenge(
    val codeChallenge: String,
    val codeChallengeMethod: String = "S256",
)

/**
 * Retry/polling configuration.
 */
@JsExportCompat
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialIntervalSeconds: Int = 5,
    val backoffMultiplier: Double = 2.0,
)

/**
 * Target credential — either by configuration ID or by credential identifier.
 * Per OID4VCI 1.1 Section 9.2 these are mutually exclusive.
 */
sealed class CredentialTarget {
    /** Use when token response did NOT include credential_identifiers. */
    data class ByConfigurationId(
        val credentialConfigurationId: String,
    ) : CredentialTarget()

    /** Use when token response included authorization_details with credential_identifiers. */
    data class ByIdentifier(
        val credentialIdentifier: String,
    ) : CredentialTarget()
}

/**
 * Typed IAE interaction types per OID4VCI 1.1 Section 6.
 * The string constants already exist in IaeInteractionTypes; this provides type safety.
 */
@JsExportCompat
enum class IaeInteractionType(
    val urn: String,
) {
    OPENID4VP_PRESENTATION("urn:openid:dcp:iae:openid4vp_presentation"),
    REDIRECT_TO_WEB("urn:openid:dcp:iae:redirect_to_web"),
    ;

    companion object {
        fun fromUrn(urn: String): IaeInteractionType? = entries.find { it.urn == urn }
    }
}
