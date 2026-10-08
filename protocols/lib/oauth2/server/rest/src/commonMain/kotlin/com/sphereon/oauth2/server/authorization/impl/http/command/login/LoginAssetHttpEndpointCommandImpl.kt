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

// Role-neutral OAuth REST capability. Executable server startup remains in services-oauth2-as-rest.
package com.sphereon.oauth2.server.authorization.impl.http.command.login

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.login.LoginAssetHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.ResponseCategory
import com.sphereon.oauth2.server.authorization.impl.http.withSecurityHeaders
import com.sphereon.oauth2.server.authorization.provider.LoginPageRenderer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * HTTP shell over [LoginPageRenderer.staticAssets] for `GET /login/assets/{...}`. Looks up the
 * asset by its relative path and serves it with `Content-Type` + `Cache-Control: public,
 * max-age=...` from the [com.sphereon.oauth2.server.authorization.provider.LoginPageAsset]
 * declaration. Text-shaped assets (CSS, SVG with `image/svg+xml`) are returned as a UTF-8 string;
 * everything else is returned as a binary `GenericHttpBody.Bytes` so the transport layer writes
 * the bytes verbatim.
 *
 * `supports()` matches any path under [LoginAssetHttpEndpointCommand.ASSETS_PREFIX]; the endpoint
 * descriptor's `pathPattern` is the catalog anchor only.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(LoginAssetHttpEndpointCommand.COMMAND_ID)
class LoginAssetHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val loginPageRenderer: LoginPageRenderer,
) : HttpEndpointCommandAdapter(
        id = LoginAssetHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = LoginAssetHttpEndpointCommand.ENDPOINT,
    ),
    LoginAssetHttpEndpointCommand {
    private val assetsByPath: Map<String, AssetSlot> by lazy {
        loginPageRenderer.staticAssets().associate { asset ->
            asset.path to
                AssetSlot(
                    contentType = asset.contentType,
                    bytes = asset.bytes,
                    maxAgeSeconds = asset.maxAgeSeconds,
                    isText = isTextContentType(asset.contentType),
                )
        }
    }

    override suspend fun supports(args: Any): Boolean =
        if (args is GenericHttpRequest) {
            args.method.equals("GET", ignoreCase = true) &&
                args.path.startsWith(LoginAssetHttpEndpointCommand.ASSETS_PREFIX)
        } else {
            false
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        val relative = request.path.removePrefix(LoginAssetHttpEndpointCommand.ASSETS_PREFIX)
        if (relative.isEmpty() || relative.contains("..")) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "Asset not found: ${request.path}"))
        }
        val slot =
            assetsByPath[relative]
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Asset not found: ${request.path}"))
        val headers =
            mapOf(
                "Content-Type" to slot.contentType,
                "Cache-Control" to "public, max-age=${slot.maxAgeSeconds}",
            )
        val category = if (slot.contentType.startsWith("text/html")) ResponseCategory.HTML else ResponseCategory.BINARY
        return if (slot.isText) {
            Ok(
                GenericHttpResponse(
                    statusCode = 200,
                    headers = headers,
                    body = slot.bytes.decodeToString(),
                ).withSecurityHeaders(category),
            )
        } else {
            Ok(
                GenericHttpResponse
                    .withBinaryBody(statusCode = 200, body = slot.bytes, headers = headers)
                    .withSecurityHeaders(category),
            )
        }
    }

    private fun isTextContentType(contentType: String): Boolean =
        contentType.startsWith("text/") ||
            contentType.contains("svg+xml") ||
            contentType.contains("json") ||
            contentType.contains("javascript") ||
            contentType.contains("xml")

    private data class AssetSlot(
        val contentType: String,
        val bytes: ByteArray,
        val maxAgeSeconds: Long,
        val isText: Boolean,
    )
}
