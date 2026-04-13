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

package com.sphereon.oauth2.client.impl.oidc

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.client.command.FetchUserInfoArgs
import com.sphereon.oauth2.client.command.FetchUserInfoCommand
import com.sphereon.oauth2.client.command.FetchUserInfoResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of FetchUserInfoCommand
 *
 * Fetches user claims from the OIDC UserInfo endpoint (OpenID Connect Core 1.0 Section 5.3).
 * Sends a GET request with the Bearer access token and parses the JSON response.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("FetchUserInfoCommandImpl", exact = true)
class FetchUserInfoCommandImpl(
    execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
) : TypedServiceCommandAdapter<FetchUserInfoArgs, FetchUserInfoResult>(
        commandId = FetchUserInfoCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<FetchUserInfoArgs>(),
        outputTypeToken = typeToken<FetchUserInfoResult>(),
    ),
    FetchUserInfoCommand {
    private val json = Json { ignoreUnknownKeys = true }

    override val commandId: String get() = FetchUserInfoCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is FetchUserInfoArgs

    override suspend fun doExecute(
        args: FetchUserInfoArgs,
        applyDuring: (FetchUserInfoArgs) -> FetchUserInfoArgs,
    ): IdkResult<FetchUserInfoResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied)
    }

    private suspend fun executeInternal(args: FetchUserInfoArgs): IdkResult<FetchUserInfoResult, IdkError> {
        val client =
            httpClientFactory.createClient(
                HttpClientOptions(
                    engine = null,
                    enableContentNegotiation = true,
                ),
            )

        return try {
            val response =
                client.get(args.userinfoEndpoint) {
                    bearerAuth(args.accessToken)
                    accept(ContentType.Application.Json)
                }

            if (!response.status.isSuccess()) {
                return Err(
                    IdkError(
                        code = "userinfo_error",
                        message =
                            IdkError.Message(
                                i18nKey = "oauth2.client.error.userinfo_fetch_failed",
                                defaultMessage = "UserInfo request failed with status ${response.status.value}",
                            ),
                    ),
                )
            }

            val bodyText = response.bodyAsText()
            val jsonObject = json.decodeFromString<JsonObject>(bodyText)

            val sub =
                (jsonObject["sub"] as? JsonPrimitive)?.content
                    ?: return Err(
                        IdkError(
                            code = "userinfo_error",
                            message =
                                IdkError.Message(
                                    i18nKey = "oauth2.client.error.userinfo_missing_sub",
                                    defaultMessage = "UserInfo response missing required 'sub' claim",
                                ),
                        ),
                    )

            val claims = jsonObject.filterKeys { it != "sub" }

            Ok(FetchUserInfoResult(sub = sub, claims = claims))
        } catch (expected: Exception) {
            Err(
                IdkError(
                    code = "userinfo_error",
                    message =
                        IdkError.Message(
                            i18nKey = "oauth2.client.error.userinfo_fetch_failed",
                            defaultMessage = "UserInfo request failed: ${expected.message}",
                        ),
                ),
            )
        } finally {
            client.close()
        }
    }
}
