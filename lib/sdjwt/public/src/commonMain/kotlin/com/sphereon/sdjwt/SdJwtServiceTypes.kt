/*
 * Copyright (c) 2026 Sphereon B.V.
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
package com.sphereon.sdjwt

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.sdjwt.command.IssueSdJwtCommand
import com.sphereon.sdjwt.command.PresentSdJwtCommand
import com.sphereon.sdjwt.command.VerifySdJwtCommand
import com.sphereon.sdjwt.dsl.SdJwtPayload
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Main service interface for SD-JWT operations.
 *
 * This service provides a unified interface for:
 * - Issuing SD-JWTs with selective disclosure
 * - Verifying SD-JWTs
 * - Creating presentations
 *
 * It follows the same pattern as [JwtService], using command objects internally
 * while providing a clean service interface.
 *
 * Not exported to JS as it has suspend functions.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SdJwtService", exact = true)
interface SdJwtService :
    IssueSdJwtCommandService,
    VerifySdJwtCommandService,
    PresentSdJwtCommandService {
    /**
     * Provides access to the underlying commands for advanced usage scenarios.
     */
    val commands: Commands

    /**
     * Container for all SD-JWT commands.
     */
    interface Commands {
        val issueSdJwt: IssueSdJwtCommand
        val verifySdJwt: VerifySdJwtCommand
        val presentSdJwt: PresentSdJwtCommand
    }
}

// ============================================================================
// Issuance Types
// ============================================================================

/**
 * Arguments for issuing an SD-JWT.
 *
 * @property payload The SD-JWT payload built using the `sdJwtPayload` DSL
 * @property issuer The managed identifier for signing
 * @property spec Optional SD-JWT specification (algorithm, decoy config)
 * @property opts Optional JWS creation options
 */
@JsExportCompat
data class IssueSdJwtArgs(
    val payload: SdJwtPayload,
    val issuer: ManagedIdentifierOptsOrResult,
    val spec: SdJwtSpec = SdJwtSpec.Default,
    val opts: CreateJwsOpts = CreateJwsOpts(),
)

/**
 * Result of SD-JWT issuance.
 *
 * @property sdJwt The complete SD-JWT (JWT~disclosure1~disclosure2~...)
 * @property jwt The signed JWT graph
 * @property disclosures The disclosures for selectively disclosable claims
 */
@JsExportCompat
data class IssueSdJwtResult(
    val sdJwt: String,
    val jwt: String,
    val disclosures: List<Disclosure>,
)

/**
 * Service interface for issuing SD-JWTs.
 */
@JsExportCompat
interface IssueSdJwtCommandService {
    suspend fun issueSdJwt(args: IssueSdJwtArgs): IdkResult<IssueSdJwtResult, IdkError>
}

// ============================================================================
// Verification Types
// ============================================================================

/**
 * Arguments for verifying an SD-JWT.
 *
 * @property sdJwt The SD-JWT in compact format (JWT~disclosure1~disclosure2~...~kbJwt)
 * @property identifier Optional identifier for verification (if not in JWT header)
 * @property expectedAudience Expected audience for KB-JWT verification (if KB-JWT present)
 * @property expectedNonce Expected nonce for KB-JWT verification (if KB-JWT present)
 * @property validateDisclosures Whether to validate disclosure digests (default true)
 * @property kbJwtMaxAgeSeconds Maximum allowed age of the Key Binding JWT in seconds, measured
 *   from its `iat` claim against the verifier's clock. Per SD-JWT §7.3 the verifier MUST
 *   "Check that the creation time of the Key Binding JWT, as determined by the iat claim, is
 *   within an acceptable window." Default 300s (5 minutes) — short enough to make replay
 *   attacks impractical for fresh presentations, long enough to absorb mobile network latency
 *   and minor clock drift between the wallet and the verifier.
 * @property kbJwtFutureSkewSeconds Maximum allowed clock skew, in seconds, where the KB-JWT's
 *   `iat` is in the future relative to the verifier. Default 60s — accommodates typical NTP
 *   drift between mobile holders and server-side verifiers without inviting forgery.
 */
@JsExportCompat
data class VerifySdJwtArgs(
    val sdJwt: String,
    val identifier: IdentifierOptsOrResult? = null,
    val expectedAudience: String? = null,
    val expectedNonce: String? = null,
    val validateDisclosures: Boolean = true,
    val kbJwtMaxAgeSeconds: Long = 300L,
    val kbJwtFutureSkewSeconds: Long = 60L,
)

/**
 * Service interface for verifying SD-JWTs.
 */
@JsExportCompat
interface VerifySdJwtCommandService {
    suspend fun verifySdJwt(args: VerifySdJwtArgs): IdkResult<SdJwtVerificationResult, IdkError>
}

// ============================================================================
// Presentation Types
// ============================================================================

/**
 * Arguments for presenting an SD-JWT.
 *
 * @property sdJwt The full SD-JWT string from the issuer
 * @property disclosureSelection Selection of which claims to disclose (null = disclose all)
 * @property audience Optional audience for Key Binding JWT
 * @property nonce Optional nonce for Key Binding JWT
 * @property holderKey Optional holder key for Key Binding JWT signature
 * @property kbJwtOpts Optional JWS creation options for KB-JWT
 */
@JsExportCompat
data class PresentSdJwtArgs(
    val sdJwt: String,
    val disclosureSelection: SdMap? = null,
    val audience: String? = null,
    val nonce: String? = null,
    val holderKey: ManagedIdentifierOptsOrResult? = null,
    val kbJwtOpts: CreateJwsOpts = CreateJwsOpts(),
)

/**
 * Result of SD-JWT presentation.
 *
 * @property presentation The presentation string (JWT with selected disclosures and optional KB-JWT)
 * @property disclosedClaims The claims that were disclosed in this presentation
 */
@JsExportCompat
data class PresentSdJwtResult(
    val presentation: String,
    val disclosedClaims: List<String>,
)

/**
 * Service interface for presenting SD-JWTs.
 */
@JsExportCompat
interface PresentSdJwtCommandService {
    suspend fun presentSdJwt(args: PresentSdJwtArgs): IdkResult<PresentSdJwtResult, IdkError>
}
