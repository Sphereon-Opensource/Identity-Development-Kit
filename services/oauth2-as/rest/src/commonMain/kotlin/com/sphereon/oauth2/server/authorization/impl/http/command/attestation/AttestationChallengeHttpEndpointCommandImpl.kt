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

package com.sphereon.oauth2.server.authorization.impl.http.command.attestation

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeArgs
import com.sphereon.oauth2.server.authorization.command.CreateAttestationChallengeCommand
import com.sphereon.oauth2.server.authorization.command.attestation.AttestationChallengeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * HTTP shell over [CreateAttestationChallengeCommand] (draft-ietf-oauth-attestation-based-client-auth §5).
 * GET /attestation-challenge returns `{"attestation_challenge": "<nonce>"}` on a JARM-disabled-style
 * cache-control envelope. Gated on [com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig.attestation]
 * + `attestationChallengeRequired` so a 404 surfaces when the deployment has not opted in, mirroring
 * how the discovery `challenge_endpoint` is omitted under the same condition.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AttestationChallengeHttpEndpointCommand>())
class AttestationChallengeHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val createAttestationChallengeCommand: CreateAttestationChallengeCommand,
    private val configProvider: OAuth2ServersConfigProvider,
) : HttpEndpointCommandAdapter(
        id = AttestationChallengeHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = AttestationChallengeHttpEndpointCommand.ENDPOINT,
    ),
    AttestationChallengeHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        applyDuring(args)
        val config = configProvider.serverConfig
        if (!config.attestation.isEnabled || !config.attestationChallengeRequired) {
            return Ok(oauth2ErrorResponse(404, "not_found", "Attestation challenge endpoint is not enabled on this server", json))
        }

        val result = createAttestationChallengeCommand.execute(CreateAttestationChallengeArgs())
        if (result.isErr) {
            return Ok(oauth2ErrorResponse(500, "server_error", result.error.message.defaultMessage, json))
        }

        val body =
            JsonObject(
                mapOf(
                    "attestation_challenge" to JsonPrimitive(result.value.attestationChallenge),
                ),
            )
        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    mapOf(
                        "Content-Type" to "application/json",
                        "Cache-Control" to "no-store",
                        "Pragma" to "no-cache",
                    ),
                body = json.encodeToString(JsonObject.serializer(), body),
            ),
        )
    }
}
