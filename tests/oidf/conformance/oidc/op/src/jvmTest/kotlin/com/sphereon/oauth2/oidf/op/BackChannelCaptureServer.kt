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

package com.sphereon.oauth2.oidf.op

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import java.net.ServerSocket

/**
 * Live capture endpoint used by the Back-Channel Logout test to inspect the actual
 * `logout_token` JWT the AS dispatches to a registered RP. The harness's per-test
 * client registry is overridden to point `backchannel_logout_uri` at this server's
 * `/backchannel` route so the IDK orchestrator delivers the POST in-process.
 *
 * Each request body is form-encoded per OIDC Back-Channel Logout 1.0 §2.7. The
 * captured `logout_token` parameter is published on [logoutToken] so the calling
 * test can await delivery and decode the JWT for spec-shape assertions.
 */
interface BackChannelCaptureServer {
    /** URL the AS posts to. Suitable as the per-test override for `backchannel-logout-uri`. */
    val url: String

    /**
     * Resolves to the captured `logout_token` parameter as soon as the AS posts. Tests
     * `await` this with a timeout so a missing delivery surfaces as a test failure
     * instead of a silent hang.
     */
    val logoutToken: CompletableDeferred<String>

    /** Stop the embedded server and release the port. */
    fun stop()

    companion object {
        /** Allocate a free localhost port and start the capture server bound to it. */
        fun start(): BackChannelCaptureServer {
            val port = ServerSocket(0).use { it.localPort }
            return BackChannelCaptureServerImpl(port)
        }
    }
}

private class BackChannelCaptureServerImpl(
    port: Int,
) : BackChannelCaptureServer {
    override val logoutToken: CompletableDeferred<String> = CompletableDeferred()
    override val url: String = "http://127.0.0.1:$port/backchannel"

    private val server =
        embeddedServer(CIO, port = port, host = "127.0.0.1") {
            routing {
                post("/backchannel") {
                    val params = call.receiveParameters()
                    val token = params["logout_token"]
                    if (token != null) {
                        // Single-shot capture: completing again is a no-op so a stray retry from
                        // the AS does not surface as a coroutine cancellation.
                        logoutToken.complete(token)
                    }
                    call.respond(HttpStatusCode.OK)
                }
            }
        }

    init {
        server.start(wait = false)
    }

    override fun stop() {
        server.stop(gracePeriodMillis = 50, timeoutMillis = 500)
    }
}
