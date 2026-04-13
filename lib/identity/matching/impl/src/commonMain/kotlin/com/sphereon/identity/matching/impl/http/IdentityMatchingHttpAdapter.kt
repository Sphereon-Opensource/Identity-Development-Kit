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

package com.sphereon.identity.matching.impl.http

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.command.CreateIdentityMatchCommand
import com.sphereon.identity.matching.command.DeleteIdentityMatchCommand
import com.sphereon.identity.matching.command.ListIdentityMatchesCommand
import com.sphereon.identity.matching.command.LookupIdentityMatchCommand
import com.sphereon.identity.matching.model.CreateIdentityMatchArgs
import com.sphereon.identity.matching.model.DeleteIdentityMatchArgs
import com.sphereon.identity.matching.model.ListIdentityMatchesArgs
import com.sphereon.identity.matching.model.LookupIdentityMatchArgs
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Inject
@Named(IdentityMatchingHttpAdapter.ID)
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<HttpAdapter>())
class IdentityMatchingHttpAdapter(
    private val lookupCommand: LookupIdentityMatchCommand,
    private val createCommand: CreateIdentityMatchCommand,
    private val deleteCommand: DeleteIdentityMatchCommand,
    private val listCommand: ListIdentityMatchesCommand,
) : RoutedHttpAdapter() {
    companion object {
        const val ID: String = "IDENTITY_MATCHING"
    }

    override val id: String = ID

    @ContributesTo(SessionScope::class)
    interface Graph {
        val identityMatchingHttpAdapter: IdentityMatchingHttpAdapter
    }

    override val mount: HttpAdapterMount =
        HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "/identity/matching/v1",
        )

    override val routes =
        httpRoutes {
            post("/matches/lookup") {
                operationId("lookupIdentityMatch")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req ->
                    val args = json.decodeFromString<LookupIdentityMatchArgs>(req.body ?: "{}")
                    val result = lookupCommand.execute(args)
                    result.fold(
                        success = { jsonResponse(200, json.encodeToString(it)) },
                        failure = { errorResponse(it) },
                    )
                }
            }
            post("/matches") {
                operationId("createIdentityMatch")
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req ->
                    val args = json.decodeFromString<CreateIdentityMatchArgs>(req.body ?: "{}")
                    val result = createCommand.execute(args)
                    result.fold(
                        success = { jsonResponse(201, json.encodeToString(it)) },
                        failure = { errorResponse(it) },
                    )
                }
            }
            delete("/matches/{matchId}") {
                operationId("deleteIdentityMatch")
                produces(MediaType.ApplicationJson)
                handle { req ->
                    val matchId = req.pathParameters["matchId"] ?: return@handle errorResponse(400, "Missing matchId")
                    val tenantId = req.queryParameters["tenantId"] ?: return@handle errorResponse(400, "Missing tenantId")
                    val result = deleteCommand.execute(DeleteIdentityMatchArgs(matchId = matchId, tenantId = tenantId))
                    result.fold(
                        success = { jsonResponse(200, json.encodeToString(it)) },
                        failure = { errorResponse(it) },
                    )
                }
            }
            get("/matches") {
                operationId("listIdentityMatches")
                produces(MediaType.ApplicationJson)
                handle { req ->
                    val identityId = req.queryParameters["identityId"] ?: return@handle errorResponse(400, "Missing identityId")
                    val tenantId = req.queryParameters["tenantId"] ?: return@handle errorResponse(400, "Missing tenantId")
                    val result = listCommand.execute(ListIdentityMatchesArgs(internalIdentityId = identityId, tenantId = tenantId))
                    result.fold(
                        success = { jsonResponse(200, json.encodeToString(it)) },
                        failure = { errorResponse(it) },
                    )
                }
            }
        }

    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    private fun jsonResponse(
        statusCode: Int,
        body: String,
    ): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = statusCode,
            headers = mapOf("Content-Type" to "application/json"),
            body = body,
        )

    private fun errorResponse(error: IdkError): GenericHttpResponse {
        val statusCode =
            when {
                error.code.contains("not_found") -> 404
                error.code.contains("duplicate") -> 409
                error.code.contains("invalid") -> 400
                else -> 500
            }
        return errorResponse(statusCode, error.message.defaultMessage ?: "Unknown error")
    }

    private fun errorResponse(
        statusCode: Int,
        message: String,
    ): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = statusCode,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(mapOf("error" to message)),
        )
}
