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

package com.sphereon.openid.wallet.cli

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred

/**
 * A minimal Ktor CIO server that listens on a loopback port for the OAuth2 authorization
 * code redirect. Starts on the given [port] (default 8765).
 *
 * Usage:
 * 1. Call [start] to bring up the server.
 * 2. Open [redirectUri] in the user's browser as the OAuth2 redirect_uri.
 * 3. Call [awaitCode] with the expected state value; returns the code when it arrives.
 * 4. Call [stop] to shut down.
 */
class LoopbackRedirectServer(
    val port: Int = 8765
) {
    private val deferred = CompletableDeferred<Pair<String, String?>>()

    private lateinit var server: EmbeddedServer<*, *>

    /** The redirect_uri to register with the authorization server. */
    val redirectUri: String get() = "http://localhost:$port/callback"

    /** Start the server without blocking the calling thread. */
    fun start() {
        server =
            embeddedServer(CIO, port = port) {
                routing {
                    get("/callback") {
                        val code = call.request.queryParameters["code"]
                        val state = call.request.queryParameters["state"]
                        if (code != null) {
                            deferred.complete(Pair(code, state))
                            call.respondText(
                                """<!DOCTYPE html>
<html>
<head><title>Authorization Complete</title></head>
<body><p>Authorization complete, you may close this window.</p></body>
</html>""",
                                io.ktor.http.ContentType.Text.Html,
                                HttpStatusCode.OK,
                            )
                        } else {
                            val error = call.request.queryParameters["error"] ?: "unknown_error"
                            deferred.completeExceptionally(IllegalStateException("Authorization failed: $error"))
                            call.respondText("Authorization failed: $error", status = HttpStatusCode.BadRequest)
                        }
                    }
                }
            }
        server.start(wait = false)
    }

    /**
     * Suspends until the authorization server redirects to the callback.
     * Validates the returned state against [expectedState] and returns the authorization code.
     */
    suspend fun awaitCode(expectedState: String): String {
        val (code, returnedState) = deferred.await()
        require(returnedState == expectedState) {
            "State mismatch: expected '$expectedState' but got '$returnedState'"
        }
        return code
    }

    /** Stop the embedded server. */
    fun stop() {
        if (::server.isInitialized) {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 500)
        }
    }
}
