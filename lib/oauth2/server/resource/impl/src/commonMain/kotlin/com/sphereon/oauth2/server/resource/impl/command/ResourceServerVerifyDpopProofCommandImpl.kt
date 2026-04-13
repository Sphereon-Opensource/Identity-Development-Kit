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
 */

package com.sphereon.oauth2.server.resource.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand as ClientVerifyDpopProofCommand
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.server.resource.cache.DpopNonceCache
import com.sphereon.oauth2.server.resource.command.VerifyDpopProofArgs
import com.sphereon.oauth2.server.resource.command.VerifyDpopProofCommand
import com.sphereon.oauth2.server.resource.error.ResourceServerError
import com.sphereon.oauth2.server.resource.model.DpopVerificationResult
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import com.sphereon.di.session.SessionScope
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of VerifyDpopProofCommand for resource server
 *
 * Verifies DPoP proof JWTs according to RFC 9449 (OAuth 2.0 Demonstrating Proof-of-Possession).
 *
 * **Verification flow**:
 * 1. Delegate to client's DPoP verification (signature, claims)
 * 2. Check JTI replay protection using DpopNonceCache
 * 3. Validate JWK thumbprint matches access token binding (expectedJkt)
 * 4. Return DpopVerificationResult with JWK, jkt, and jti
 *
 * **Replay protection**:
 * - Uses DpopNonceCache to track used JTI values
 * - Prevents replay attacks within time window (120 seconds)
 * - JTI is marked as used after successful verification
 *
 * **Note**: Delegates signature and claim verification to the OAuth2 client's
 * VerifyDpopProofCommand to reuse existing logic.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResourceServerVerifyDpopProofCommandImpl", exact = true)
class ResourceServerVerifyDpopProofCommandImpl(
    execution: SessionExecution,
    private val clientVerifyDpopProofCommand: ClientVerifyDpopProofCommand,
    private val dpopNonceCache: DpopNonceCache
) : TypedServiceCommandAdapter<VerifyDpopProofArgs, DpopVerificationResult>(
    commandId = VerifyDpopProofCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifyDpopProofArgs>(),
    outputTypeToken = typeToken<DpopVerificationResult>(),
), VerifyDpopProofCommand {

    companion object {
        // DPoP proof JTI time window: iat ± 60 seconds + 60 seconds buffer
        private val DPOP_JTI_TIME_WINDOW = 120.seconds
    }

    override val commandId: String get() = VerifyDpopProofCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyDpopProofArgs

    override suspend fun doExecute(
        args: VerifyDpopProofArgs,
        applyDuring: (VerifyDpopProofArgs) -> VerifyDpopProofArgs
    ): IdkResult<DpopVerificationResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.dpopProof, applied.httpMethod, applied.httpUrl, applied.expectedJkt).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        dpopProof: String,
        httpMethod: String,
        httpUrl: String,
        expectedJkt: String?
    ): IdkResult<DpopVerificationResult, ResourceServerError> {
        // 1. Prepare verification options
        val options = VerifyDpopProofOptions(
            dpopProof = dpopProof,
            httpMethod = httpMethod,
            httpUrl = httpUrl,
            expectedJwkThumbprint = expectedJkt,
            accessToken = null // Resource server doesn't validate ath (that's for token endpoint)
        )

        // 2. Verify DPoP proof using client command (signature, typ, claims)
        val verifyResult = clientVerifyDpopProofCommand.execute(options)
        if (verifyResult.isErr) {
            return Err(ResourceServerError.InvalidDpopProof(
                "DPoP proof verification failed: ${verifyResult.error.message.defaultMessage}"
            ))
        }

        val verifiedProof = verifyResult.value

        // 3. Check JTI replay protection
        val jti = verifiedProof.payload.jti
        if (dpopNonceCache.hasBeenUsed(jti)) {
            return Err(ResourceServerError.InvalidDpopProof(
                "DPoP proof JTI '$jti' has already been used (replay attack detected)"
            ))
        }

        // 4. Mark JTI as used (with expiration based on iat + time window)
        val iat = Instant.fromEpochSeconds(verifiedProof.payload.iat)
        val jtiExpiration = iat + DPOP_JTI_TIME_WINDOW
        dpopNonceCache.markAsUsed(jti, jtiExpiration)

        // 5. Build and return DpopVerificationResult
        return Ok(DpopVerificationResult(
            jwk = verifiedProof.header.jwk,
            jkt = verifiedProof.jwkThumbprint,
            jti = jti
        ))
    }
}
