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

import com.sphereon.oauth2.server.authorization.storage.DeviceAuthorizationStorage
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking

/**
 * In-process test fixture that boots the OIDF OP harness on an ephemeral port.
 *
 * Each test instantiates [OidfOpServerFixture], reads [baseUrl] to drive the Ktor client at
 * the live server, and calls [stop] in `@AfterAll` / cleanup so the JVM does not leak the
 * Netty engine between test classes. The fixture composes [OidfOpTestAppGraph] (the test-scope
 * mirror of [OidfOpAppGraph] that picks up [TestClockModule]) + [OidfOpBootstrap], so
 * production-shaped DI wiring is exercised but tests can drive virtual time through [testClock].
 */
class OidfOpServerFixture {
    /**
     * The composed test-scope app graph. Exposed so individual tests that need to reach into
     * the AS's session-scoped services (KMS, JWE) can do so without spawning a parallel graph.
     * Use for crypto-side test helpers only — do not bypass HTTP for normal flows.
     */
    val graph: OidfOpTestAppGraph = createOidfOpTestAppGraph()

    /**
     * The shared [TestClock] bound at [AppScope] via [TestClockModule]. Tests advance virtual
     * time through this handle (see [TestClock.advance]) instead of calling `Thread.sleep`,
     * because the AS commands read [kotlin.time.Clock] from DI.
     */
    val testClock: TestClock = graph.testClock

    /**
     * AppScope-bound [DeviceAuthorizationStorage] for tests that drive the RFC 8628 token-side
     * polling without walking through the HTML approval UI. Tests pre-flip a record to
     * APPROVED / DENIED here, then exercise `/token` to assert the wire shape.
     */
    val deviceAuthorizationStorage: DeviceAuthorizationStorage = graph.deviceAuthorizationStorage

    private val server =
        run {
            runBlocking { OidfOpBootstrap.seed(graph) }
            embeddedServer(CIO, port = 0, host = "127.0.0.1") {
                configureOidfOp(graph)
            }
        }

    init {
        server.start(wait = false)
    }

    /** Resolved port the embedded engine bound to. Reads off the engine's connector list. */
    val port: Int by lazy {
        runBlocking {
            server.engine
                .resolvedConnectors()
                .first()
                .port
        }
    }

    val baseUrl: String get() = "http://127.0.0.1:$port"

    fun stop() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
    }
}
