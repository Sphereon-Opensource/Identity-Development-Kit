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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vp.verifier.callback.AuthorizationSessionCallbackDispatcher
import com.sphereon.openid.oid4vp.verifier.callback.AuthorizationSessionStatusUpdate
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Default callback dispatcher using the IDK HTTP abstraction ([HttpClientFactory]).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AuthorizationSessionCallbackDispatcher>())
class KtorAuthorizationSessionCallbackDispatcher(
    private val httpClientFactory: HttpClientFactory,
) : AuthorizationSessionCallbackDispatcher {
    private val client by lazy {
        httpClientFactory.createClient(
            HttpClientOptions.createDefault().copy(
                enableContentNegotiation = true,
                // Avoid leaking session/status payloads in logs by default.
                enableLogging = false,
            ),
        )
    }

    override suspend fun dispatch(
        url: String,
        update: AuthorizationSessionStatusUpdate,
    ): IdkResult<Unit, IdkError> =
        try {
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(update)
            }
            Ok(Unit)
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "Failed to dispatch authorization session callback: ${expected.message}", exception = expected))
        }
}
