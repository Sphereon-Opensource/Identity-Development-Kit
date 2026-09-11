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

package com.sphereon.identity.reconciliation.impl.http

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutedHttpAdapter
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.describe.httpRoutes
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.reconciliation.command.CancelReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.CompleteReconciliationCommand
import com.sphereon.identity.reconciliation.command.CreateReconciliationSessionCommand
import com.sphereon.identity.reconciliation.command.GetReconciliationSessionCommand
import com.sphereon.identity.reconciliation.model.CancelReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.CompleteReconciliationArgs
import com.sphereon.identity.reconciliation.model.CreateReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.GetReconciliationSessionArgs
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DISABLED: This standalone REST surface competes with the portal's auth-bridge endpoints
 * (backed by ReconciliationOrchestrator). The portal endpoints at /auth/oid4vp/idv/ are
 * the authoritative surface for reconciliation. The @ContributesBinding annotation has been
 * removed so this adapter is not auto-wired into the DI graph.
 *
 * See ReconciliationOrchestrator in service-auth-bridge for the active orchestration surface.
 *
 * Note: ContributesBinding removed — portal ReconciliationOrchestrator is the authoritative surface.
 */
@Inject
@Named(IdentityReconciliationHttpAdapter.ID)
@SingleIn(SessionScope::class)
class IdentityReconciliationHttpAdapter(
    private val createSessionCommand: CreateReconciliationSessionCommand,
    private val getSessionCommand: GetReconciliationSessionCommand,
    private val completeCommand: CompleteReconciliationCommand,
    private val cancelCommand: CancelReconciliationSessionCommand,
) : RoutedHttpAdapter() {
    companion object {
        const val ID: String = "identity.reconciliation.http"
    }

    override val id: String = ID

    // @ContributesTo removed — adapter is disabled (see class-level comment)
    interface Graph {
        val identityReconciliationHttpAdapter: IdentityReconciliationHttpAdapter
    }

    override val mount: HttpAdapterMount =
        HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "/identity/reconciliation/v1",
        )

    override val routes =
        httpRoutes {
            post("/sessions") {
                operationId("createReconciliationSession")
                handlerCommandId(CreateReconciliationSessionCommand.COMMAND_ID)
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req ->
                    val args = json.decodeFromString<CreateReconciliationSessionArgs>(req.body ?: "{}")
                    val result = createSessionCommand.execute(args)
                    result.fold(
                        success = { jsonResponse(201, json.encodeToString(it)) },
                        failure = { errorResponse(it) },
                    )
                }
            }
            get("/sessions/{sessionId}") {
                operationId("getReconciliationSession")
                handlerCommandId(GetReconciliationSessionCommand.COMMAND_ID)
                produces(MediaType.ApplicationJson)
                handle { req ->
                    val sessionId = req.pathParameters["sessionId"] ?: return@handle errorResponse(400, "Missing sessionId")
                    val tenantId = req.queryParameters["tenantId"] ?: return@handle errorResponse(400, "Missing tenantId")
                    val result = getSessionCommand.execute(GetReconciliationSessionArgs(sessionId = sessionId, tenantId = tenantId))
                    result.fold(
                        success = { jsonResponse(200, json.encodeToString(it)) },
                        failure = { errorResponse(it) },
                    )
                }
            }
            post("/sessions/{sessionId}/complete") {
                operationId("completeReconciliation")
                handlerCommandId(CompleteReconciliationCommand.COMMAND_ID)
                consumes(MediaType.ApplicationJson)
                produces(MediaType.ApplicationJson)
                handle { req ->
                    val args = json.decodeFromString<CompleteReconciliationArgs>(req.body ?: "{}")
                    val result = completeCommand.execute(args)
                    result.fold(
                        success = { jsonResponse(200, json.encodeToString(it)) },
                        failure = { errorResponse(it) },
                    )
                }
            }
            delete("/sessions/{sessionId}") {
                operationId("cancelReconciliationSession")
                handlerCommandId(CancelReconciliationSessionCommand.COMMAND_ID)
                produces(MediaType.ApplicationJson)
                handle { req ->
                    val sessionId = req.pathParameters["sessionId"] ?: return@handle errorResponse(400, "Missing sessionId")
                    val tenantId = req.queryParameters["tenantId"] ?: return@handle errorResponse(400, "Missing tenantId")
                    val result = cancelCommand.execute(CancelReconciliationSessionArgs(sessionId = sessionId, tenantId = tenantId))
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
                error.code.contains("expired") -> 410
                error.code.contains("invalid_state") || error.code.contains("mismatch") -> 409
                error.code.contains("failed") -> 502
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
