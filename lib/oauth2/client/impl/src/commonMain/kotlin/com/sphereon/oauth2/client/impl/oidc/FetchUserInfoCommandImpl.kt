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
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
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
    private val httpClientFactory: HttpClientFactory
) : TypedServiceCommandAdapter<FetchUserInfoArgs, FetchUserInfoResult>(
    commandId = FetchUserInfoCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<FetchUserInfoArgs>(),
    outputTypeToken = typeToken<FetchUserInfoResult>(),
), FetchUserInfoCommand {

    private val json = Json { ignoreUnknownKeys = true }

    override val commandId: String get() = FetchUserInfoCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is FetchUserInfoArgs

    override suspend fun doExecute(
        args: FetchUserInfoArgs,
        applyDuring: (FetchUserInfoArgs) -> FetchUserInfoArgs
    ): IdkResult<FetchUserInfoResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied)
    }

    private suspend fun executeInternal(
        args: FetchUserInfoArgs
    ): IdkResult<FetchUserInfoResult, IdkError> {
        val client = httpClientFactory.createClient(
            HttpClientOptions(
                engine = null,
                enableContentNegotiation = true
            )
        )

        return try {
            val response = client.get(args.userinfoEndpoint) {
                bearerAuth(args.accessToken)
                accept(ContentType.Application.Json)
            }

            if (!response.status.isSuccess()) {
                return Err(
                    IdkError(
                        code = "userinfo_error",
                        message = IdkError.Message(
                            i18nKey = "oauth2.client.error.userinfo_fetch_failed",
                            defaultMessage = "UserInfo request failed with status ${response.status.value}"
                        )
                    )
                )
            }

            val bodyText = response.bodyAsText()
            val jsonObject = json.decodeFromString<JsonObject>(bodyText)

            val sub = (jsonObject["sub"] as? JsonPrimitive)?.content
                ?: return Err(
                    IdkError(
                        code = "userinfo_error",
                        message = IdkError.Message(
                            i18nKey = "oauth2.client.error.userinfo_missing_sub",
                            defaultMessage = "UserInfo response missing required 'sub' claim"
                        )
                    )
                )

            val claims = jsonObject.filterKeys { it != "sub" }

            Ok(FetchUserInfoResult(sub = sub, claims = claims))
        } catch (e: Exception) {
            Err(
                IdkError(
                    code = "userinfo_error",
                    message = IdkError.Message(
                        i18nKey = "oauth2.client.error.userinfo_fetch_failed",
                        defaultMessage = "UserInfo request failed: ${e.message}"
                    )
                )
            )
        } finally {
            client.close()
        }
    }
}
