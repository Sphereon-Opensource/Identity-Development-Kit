/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.impl.token

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.oauth2.client.command.ExchangeTokenArgs
import com.sphereon.oauth2.client.command.ExchangeTokenCommand
import com.sphereon.oauth2.client.token.OAuth2TokenEndpointTransport
import com.sphereon.oauth2.client.token.OAuth2TokenEndpointTransportArgs
import com.sphereon.oauth2.common.model.TokenResponse
import dev.zacsweers.metro.Inject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/** Thin ServiceCommand adapter over the execution-leaf token endpoint transport. */
@Inject
@OptIn(ExperimentalObjCName::class)
@ObjCName("ExchangeTokenCommandImpl", exact = true)
class ExchangeTokenCommandImpl(
    execution: SessionExecution,
    private val tokenEndpointTransport: OAuth2TokenEndpointTransport,
) : TypedServiceCommandAdapter<ExchangeTokenArgs, TokenResponse, IdkError>(
        commandId = ExchangeTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ExchangeTokenArgs>(),
        outputTypeToken = typeToken<TokenResponse>(),
    ),
    ExchangeTokenCommand {
    override val commandId: String get() = ExchangeTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ExchangeTokenArgs

    override suspend fun doExecute(
        args: ExchangeTokenArgs,
        applyDuring: (ExchangeTokenArgs) -> ExchangeTokenArgs,
    ): IdkResult<TokenResponse, IdkError> {
        val applied = applyDuring(args)
        val context = applied.transportContext
        return tokenEndpointTransport.exchange(
            OAuth2TokenEndpointTransportArgs(
                configuredTokenEndpoint = applied.tokenEndpoint,
                tokenRequest = applied.request,
                clientAuthentication = context?.clientAuthentication,
                authorizationServerMetadata = context?.authorizationServerMetadata,
                callerAuthorizedTokenEndpoints = context?.callerAuthorizedTokenEndpoints.orEmpty(),
                httpClientRequestContext = context?.httpClientRequestContext,
            ),
        ).mapError { IdkError.fromDTO(it) }
    }
}


