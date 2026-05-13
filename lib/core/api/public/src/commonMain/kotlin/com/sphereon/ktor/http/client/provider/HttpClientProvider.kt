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

package com.sphereon.ktor.http.client.provider

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.CommandId
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.config.HttpClientConfigResolver
import com.sphereon.ktor.http.client.config.HttpClientProperties
import com.sphereon.ktor.http.client.config.toOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Single injection point for obtaining config-driven HTTP clients.
 *
 * Combines [HttpClientFactory] and [HttpClientConfigResolver] so commands
 * only need to inject one dependency. The command ID is used to resolve
 * configuration from the `cmd.*` scoped property hierarchy.
 *
 * Usage in a command:
 * ```kotlin
 * @Inject @SingleIn(SessionScope::class)
 * class MyCommandImpl(
 *     execution: SessionExecution,
 *     private val httpClients: HttpClientProvider
 * ) : TypedServiceCommandAdapter<..., IdkError>(...) {
 *
 *     override suspend fun doExecute(args, applyDuring) {
 *         httpClients.withClient(commandId) { client ->
 *             client.get("https://...")
 *         }
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HttpClientProvider", exact = true)
interface HttpClientProvider {
    /**
     * Create an [HttpClient] with config resolved for the given command ID.
     * Config is resolved from the `cmd.*` scoped property hierarchy.
     *
     * @param commandId The 3-part command ID (e.g., "kms.keys.get")
     * @param overrides Optional programmatic overrides applied after config resolution
     */
    fun createClient(
        commandId: String,
        overrides: (HttpClientOptions) -> HttpClientOptions = { it },
    ): HttpClient

    /**
     * Create an [HttpClient] with config resolved for the given [CommandId].
     */
    fun createClient(
        commandId: CommandId,
        overrides: (HttpClientOptions) -> HttpClientOptions = { it },
    ): HttpClient = createClient(commandId.value, overrides)

    /**
     * Create an [HttpClient] using only global config (no command scoping).
     */
    fun createClient(overrides: (HttpClientOptions) -> HttpClientOptions = { it }): HttpClient

    /**
     * Create an [HttpClient] with scoped config, use it in [block], then close it.
     */
    suspend fun <T> withClient(
        commandId: String,
        overrides: (HttpClientOptions) -> HttpClientOptions = { it },
        block: suspend (HttpClient) -> T,
    ): T

    /**
     * Create an [HttpClient] with scoped config using [CommandId].
     */
    suspend fun <T> withClient(
        commandId: CommandId,
        overrides: (HttpClientOptions) -> HttpClientOptions = { it },
        block: suspend (HttpClient) -> T,
    ): T = withClient(commandId.value, overrides, block)

    /**
     * Create an [HttpClient] with global config, use it in [block], then close it.
     */
    suspend fun <T> withClient(
        overrides: (HttpClientOptions) -> HttpClientOptions = { it },
        block: suspend (HttpClient) -> T,
    ): T
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class HttpClientProviderImpl(
    private val factory: HttpClientFactory,
    private val configResolver: HttpClientConfigResolver,
) : HttpClientProvider {
    override fun createClient(
        commandId: String,
        overrides: (HttpClientOptions) -> HttpClientOptions,
    ): HttpClient {
        val props = configResolver.resolve(commandId)
        return factory.createClient(overrides(props.toOptions()))
    }

    override fun createClient(overrides: (HttpClientOptions) -> HttpClientOptions): HttpClient {
        val props = configResolver.resolveGlobal()
        return factory.createClient(overrides(props.toOptions()))
    }

    override suspend fun <T> withClient(
        commandId: String,
        overrides: (HttpClientOptions) -> HttpClientOptions,
        block: suspend (HttpClient) -> T,
    ): T {
        val client = createClient(commandId, overrides)
        return try {
            block(client)
        } finally {
            client.close()
        }
    }

    override suspend fun <T> withClient(
        overrides: (HttpClientOptions) -> HttpClientOptions,
        block: suspend (HttpClient) -> T,
    ): T {
        val client = createClient(overrides)
        return try {
            block(client)
        } finally {
            client.close()
        }
    }
}
