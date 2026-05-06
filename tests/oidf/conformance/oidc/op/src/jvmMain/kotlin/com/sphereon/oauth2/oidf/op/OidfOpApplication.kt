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

import com.sphereon.di.app.AppGraph
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import kotlinx.coroutines.runBlocking

/**
 * Entry point for the OIDF conformance OP harness.
 *
 * Boots the IDK OAuth2 Authorization Server with classpath-loaded `application.properties`,
 * registers the software KMS, generates the RS256 signing key,
 * and starts a Ktor server that exposes every IDK `HttpAdapter` contributed to the session
 * graph. The conformance suite at `https://www.certification.openid.net/test/a/<test-id>/` then
 * drives this OP through the Basic-OP flows.
 *
 * Port: defaults to 8080, override via the `OIDF_OP_PORT` environment variable.
 *
 * The seeded password for both alice and bob is `Sphereon-OIDF-2026!` and the deployment-wide
 * pepper is fixed at `oidf-op-conformance-pepper-2026`. These are conformance fixtures, not
 * production secrets, and must never be reused outside this harness.
 */
fun main() {
    val port = System.getenv(ENV_PORT)?.toIntOrNull() ?: DEFAULT_PORT
    val host = System.getenv(ENV_HOST) ?: DEFAULT_HOST

    val graph = createOidfOpAppGraph()
    val seeded = runBlocking { OidfOpBootstrap.seed(graph) }

    println(
        "OIDF OP harness listening on http://localhost:$port " +
            "(issuer=http://localhost:$port, signing-key=${seeded.signingKeyAlias} alg=${seeded.signingAlgorithm})",
    )

    embeddedServer(CIO, port = port, host = host) {
        configureOidfOp(graph)
    }.start(wait = true)
}

/**
 * Wires the Ktor application module: KotlinInject plugin attaches the AppGraph and a per-call
 * session graph; ContentNegotiation handles JSON request and response bodies; StatusPages
 * shapes uncaught exceptions; `installUniversalHttpAdapters` mounts every contributed
 * `HttpAdapter` as a catch-all route.
 */
fun Application.configureOidfOp(appGraph: AppGraph) {
    install(KotlinInjectPlugin) {
        this.appGraph = appGraph
        tenantResolver = FixedTenantResolver("default")
    }

    install(ContentNegotiation) {
        json()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = """{"error":"server_error","error_description":"${cause.message?.replace("\"", "\\\"")}"}""",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }

    installUniversalHttpAdapters()
}

private const val ENV_PORT: String = "OIDF_OP_PORT"
private const val ENV_HOST: String = "OIDF_OP_HOST"
private const val DEFAULT_PORT: Int = 8080
private const val DEFAULT_HOST: String = "0.0.0.0"
