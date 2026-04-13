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

package com.sphereon.openid.oid4vci.holder.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.rest.ResolveOfferRequest
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Command interface for resolving a credential offer.
 *
 * POST /offers/resolve
 *
 * Parses and resolves a raw credential offer URI or JSON into a ResolvedCredentialOffer.
 */
interface ResolveOfferEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.client.resolve"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/offers/resolve",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "resolveCredentialOffer",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-holder"),
                summary = "Parse and resolve a credential offer",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolveOfferEndpointCommand>())
class ResolveOfferEndpointCommandImpl(
    execution: SessionExecution,
    private val clientService: Oid4vciHolderService,
) : HttpEndpointCommandAdapter(
        id = ResolveOfferEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ResolveOfferEndpointCommand.ENDPOINT,
    ),
    ResolveOfferEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val body = request.body ?: "{}"

        val rawOffer: String =
            try {
                val wrapper = holderJson.decodeFromString<ResolveOfferRequest>(body)
                wrapper.rawOffer
            } catch (_: Exception) {
                body
            }

        val offer =
            clientService.parseCredentialOffer(rawOffer).getOrElse { error ->
                return Err(error)
            }
        val resolved =
            clientService.resolveCredentialOffer(offer).getOrElse { error ->
                return Err(error)
            }
        return Ok(jsonResponse(200, holderJson.encodeToString(resolved)))
    }
}
