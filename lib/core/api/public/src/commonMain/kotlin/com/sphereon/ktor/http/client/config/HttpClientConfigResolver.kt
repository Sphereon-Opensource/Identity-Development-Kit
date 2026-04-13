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

package com.sphereon.ktor.http.client.config

import com.sphereon.core.api.conf.CommandConfigScope
import com.sphereon.core.api.conf.CommandScopedConfigBinder
import com.sphereon.core.api.conf.getConfig
import com.sphereon.core.api.conf.toCommandScopedBinder
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.session.CommandId
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Resolves [HttpClientProperties] by merging layered config prefixes for a given command scope.
 *
 * Resolution order (each level overrides the previous):
 * 1. Global: `http.client.*`
 * 2. Module: `cmd.<module>.default.default.http.client.*`
 * 3. Service: `cmd.<module>.<service>.default.http.client.*`
 * 4. Command: `cmd.<module>.<service>.<command>.http.client.*`
 *
 * Within each prefix level, the APP → TENANT → PRINCIPAL config hierarchy
 * is resolved by the underlying PropertyResolver.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HttpClientConfigResolver", exact = true)
interface HttpClientConfigResolver {
    fun resolve(commandId: CommandId): HttpClientProperties

    fun resolve(commandId: String): HttpClientProperties = resolve(CommandId(commandId))

    fun resolveGlobal(): HttpClientProperties
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class HttpClientConfigResolverImpl(
    private val execution: SessionExecution,
) : HttpClientConfigResolver {
    override fun resolve(commandId: CommandId): HttpClientProperties {
        val binder = execution.conf.toCommandScopedBinder(CommandConfigScope.fromCommandId(commandId))
        return binder.getConfig<HttpClientProperties>(HttpClientProperties.CONFIG_SUFFIX)
            ?: HttpClientProperties()
    }

    override fun resolveGlobal(): HttpClientProperties {
        val binder = execution.conf.toCommandScopedBinder(CommandConfigScope.GLOBAL)
        return binder.getConfig<HttpClientProperties>(HttpClientProperties.CONFIG_SUFFIX)
            ?: HttpClientProperties()
    }
}

/**
 * Create an [HttpClient] with config resolved for the given command scope.
 *
 * @param resolver The config resolver to use
 * @param commandId The command ID for scoped config resolution
 * @param overrides Optional programmatic overrides applied after config resolution
 */
fun HttpClientFactory.createClient(
    resolver: HttpClientConfigResolver,
    commandId: CommandId,
    overrides: (HttpClientOptions) -> HttpClientOptions = { it },
): HttpClient {
    val props = resolver.resolve(commandId)
    return createClient(overrides(props.toOptions()))
}

/**
 * Create an [HttpClient] with config resolved for the given command ID string.
 */
fun HttpClientFactory.createClient(
    resolver: HttpClientConfigResolver,
    commandId: String,
    overrides: (HttpClientOptions) -> HttpClientOptions = { it },
): HttpClient = createClient(resolver, CommandId(commandId), overrides)

/**
 * Create an [HttpClient] with global config (no command scoping).
 */
fun HttpClientFactory.createClientFromConfig(
    resolver: HttpClientConfigResolver,
    overrides: (HttpClientOptions) -> HttpClientOptions = { it },
): HttpClient {
    val props = resolver.resolveGlobal()
    return createClient(overrides(props.toOptions()))
}

/**
 * Create an [HttpClient] with scoped config, use it in [block], then close it.
 */
suspend fun <T> HttpClientFactory.withClient(
    resolver: HttpClientConfigResolver,
    commandId: CommandId,
    overrides: (HttpClientOptions) -> HttpClientOptions = { it },
    block: suspend (HttpClient) -> T,
): T {
    val client = createClient(resolver, commandId, overrides)
    return try {
        block(client)
    } finally {
        client.close()
    }
}

/**
 * Create an [HttpClient] with scoped config using string command ID.
 */
suspend fun <T> HttpClientFactory.withClient(
    resolver: HttpClientConfigResolver,
    commandId: String,
    overrides: (HttpClientOptions) -> HttpClientOptions = { it },
    block: suspend (HttpClient) -> T,
): T = withClient(resolver, CommandId(commandId), overrides, block)
