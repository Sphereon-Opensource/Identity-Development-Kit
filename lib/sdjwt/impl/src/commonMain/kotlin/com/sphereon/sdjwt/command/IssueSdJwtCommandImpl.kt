/*
 * Copyright (c) 2025 Sphereon B.V.
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
package com.sphereon.sdjwt.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter

import com.sphereon.crypto.jose.jws.*
import com.sphereon.di.session.SessionScope
import com.sphereon.sdjwt.DefaultSaltProvider
import com.sphereon.sdjwt.IssueSdJwtArgs

import com.sphereon.sdjwt.IssueSdJwtResult
import com.sphereon.sdjwt.vc.SdJwtVcTypeHeaders
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of IssueSdJwtCommand.
 *
 * This command:
 * 1. Uses [SdJwtIssuer] to transform the JWT payload with SD metadata into an unsigned SD-JWT
 * 2. Signs the JWT payload using the existing JWS infrastructure
 * 3. Combines the signed JWT with disclosures in compact format
 *
 * @property execution Session execution context
 * @property createJwsCompactCommand Command for creating compact JWS
 * @property plugin Optional plugin for command execution
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssueSdJwtCommandImpl", exact = true)
class IssueSdJwtCommandImpl(
    execution: SessionExecution,
    private val createJwsCompactCommand: CreateJwsCompactCommand,
) : TypedServiceCommandAdapter<IssueSdJwtArgs, IssueSdJwtResult>(
    commandId = IssueSdJwtCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<IssueSdJwtArgs>(),
    outputTypeToken = typeToken<IssueSdJwtResult>(),
), IssueSdJwtCommand {

    override val commandId: String get() = IssueSdJwtCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is IssueSdJwtArgs

    override suspend fun doExecute(
        args: IssueSdJwtArgs,
        applyDuring: (IssueSdJwtArgs) -> IssueSdJwtArgs
    ): IdkResult<IssueSdJwtResult, IdkError> {
        val appliedArgs = applyDuring(args)

        log.debug("Issuing SD-JWT with spec: hashAlg=${appliedArgs.spec.digestAlg}, decoyMode=${appliedArgs.spec.decoyConfig.mode}")

        try {
            // Step 1: Use SdJwtIssuer to create unsigned SD-JWT
            val issuer = SdJwtIssuer(
                spec = appliedArgs.spec,
                saltProvider = DefaultSaltProvider()
            )
            val unsigned = issuer.issue(appliedArgs.payload)
            log.debug("Created unsigned SD-JWT with ${unsigned.disclosures.size} disclosures")

            // Step 2: Sign the JWT payload
            log.debug("Signing JWT payload (type: ${unsigned.jwtPayload::class.simpleName})")

            // Check if this is an SD-JWT-VC (has vct claim)
            val isVc = unsigned.jwtPayload["vct"] != null

            // Set typ header if this is an SD-JWT-VC
            val jwsOpts = if (isVc && appliedArgs.opts.protectedHeader == null) {
                // Add typ header for SD-JWT-VC (draft-13 §4.1)
                appliedArgs.opts.copy(
                    protectedHeader = buildJsonObject {
                        put("typ", JsonPrimitive(SdJwtVcTypeHeaders.DC_SD_JWT))
                    }
                )
            } else {
                appliedArgs.opts
            }

            val jwsArgs = CreateJwsArgs(
                issuer = appliedArgs.issuer,
                payload = unsigned.jwtPayload,  // Just pass JsonObject directly
                opts = jwsOpts
            )

            val jwsResult = createJwsCompactCommand.execute(jwsArgs)
            if (jwsResult.isErr) {
                log.error("Failed to sign JWT payload: ${jwsResult.error.message}")
                return IdkResult.err(jwsResult.error)
            }

            val signedJwt = jwsResult.value.jwt
            log.debug("Signed JWT: ${signedJwt.take(50)}...")

            // Step 3: Combine JWT with disclosures in compact format
            // Format for issuance: JWT~disclosure1~disclosure2~... (no trailing ~)
            // Format for presentation: JWT~disclosure1~disclosure2~... (with trailing ~ for KB-JWT placeholder)
            val compactSdJwt = buildString {
                append(signedJwt)
                for (disclosure in unsigned.disclosures) {
                    append("~")
                    append(disclosure.encoded)
                }
                // No trailing ~ for issuance format
            }

            log.info("Issued SD-JWT with ${unsigned.disclosures.size} disclosures, total length: ${compactSdJwt.length}")

            return IssueSdJwtResult(
                sdJwt = compactSdJwt,
                jwt = signedJwt,
                disclosures = unsigned.disclosures
            ).asOkResult()

        } catch (e: Exception) {
            log.error("Failed to issue SD-JWT: ${e.message}", e)
            return IdkResult.err(
                IdkError.fromString(
                    message = "Failed to issue SD-JWT: ${e.message}",
                    exception = e
                )
            )
        }
    }

}
