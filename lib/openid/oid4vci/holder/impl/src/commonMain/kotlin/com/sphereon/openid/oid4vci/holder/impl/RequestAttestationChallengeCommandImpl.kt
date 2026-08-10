/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.withClient
import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.holder.AttestationChallengeResponse
import com.sphereon.openid.oid4vci.holder.RequestAttestationChallengeArgs
import com.sphereon.openid.oid4vci.holder.RequestAttestationChallengeCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestAttestationChallengeCommand>())
class RequestAttestationChallengeCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<RequestAttestationChallengeArgs, AttestationChallengeResponse, IdkError>(
        commandId = RequestAttestationChallengeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<RequestAttestationChallengeArgs>(),
        outputTypeToken = typeToken<AttestationChallengeResponse>(),
    ),
    RequestAttestationChallengeCommand {
    override val commandId: String get() = RequestAttestationChallengeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RequestAttestationChallengeArgs

    override suspend fun doExecute(
        args: RequestAttestationChallengeArgs,
        applyDuring: (RequestAttestationChallengeArgs) -> RequestAttestationChallengeArgs,
    ): IdkResult<AttestationChallengeResponse, IdkError> {
        val endpoint = applyDuring(args).challengeEndpoint
        return try {
            httpClientFactory.withClient { httpClient ->
                val response = httpClient.post(endpoint)
                if (!response.status.isSuccess()) {
                    return@withClient Err(
                        IdkError.fromString(
                            code = "ATTESTATION_CHALLENGE_REQUEST_FAILED",
                            message = "Failed to request attestation challenge from $endpoint: HTTP ${response.status.value}",
                        ),
                    )
                }
                val challenge =
                    try {
                        Oid4vciJson.lenient.decodeFromString(AttestationChallengeResponse.serializer(), response.bodyAsText())
                    } catch (expected: Exception) {
                        return@withClient Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "Failed to parse attestation challenge response JSON: ${expected.message}",
                                throwable = expected,
                            ),
                        )
                    }
                if (challenge.attestationChallenge.isBlank()) {
                    return@withClient Err(
                        IdkError.fromString(
                            code = "ATTESTATION_CHALLENGE_RESPONSE_INVALID",
                            message = "Attestation challenge endpoint returned a blank challenge",
                        ),
                    )
                }
                Ok(challenge)
            }
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    code = "ATTESTATION_CHALLENGE_NETWORK_ERROR",
                    message = "Network error requesting attestation challenge from $endpoint: ${expected.message}",
                    exception = expected,
                ),
            )
        }
    }
}
