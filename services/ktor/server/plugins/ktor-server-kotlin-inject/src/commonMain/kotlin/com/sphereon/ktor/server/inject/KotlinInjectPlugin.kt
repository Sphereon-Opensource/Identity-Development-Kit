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
 *
 */

package com.sphereon.ktor.server.inject

import com.sphereon.core.api.app.CoreApiAppExtensionGraph
import com.sphereon.core.api.log.LogService
import com.sphereon.di.app.AppGraph
import com.sphereon.ktor.server.inject.interceptor.UserContextInterceptor
import com.sphereon.ktor.server.inject.resolver.PrincipalResolver
import com.sphereon.ktor.server.inject.resolver.TenantResolver
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.application.plugin
import io.ktor.util.AttributeKey

/**
 * Ktor server plugin that provides kotlin-inject, kotlin-inject-anvil (Amazon), and
 * Amazon App Platform injection support for the Identity Development Kit (IDK).
 *
 * This plugin enables the three-scope architecture:
 * - **AppScope**: Singleton instances shared across the entire application
 * - **UserScope**: Principal and tenant scoped instances (per user/tenant combination)
 * - **SessionScope**: Session scoped instances (per request/response)
 *
 * **Multiplatform Compatible:**
 * This plugin is fully multiplatform and works on JVM, JavaScript, Native, and GraalVM.
 * Services are accessed via graph inspection (properties and provider methods) using reflection.
 *
 * **Key Features:**
 * - Request-scoped context resolution (tenant + principal + session)
 * - Service access via extension functions using graph inspection
 * - Thread-safe per-request isolation
 * - Configurable authentication and resolver strategies
 * - Works across all Kotlin platforms
 *
 * **Installation:**
 * ```kotlin
 * install(KotlinInjectPlugin) {
 *     appGraph = myAppGraph
 *
 *     // Optional: custom resolvers
 *     tenantResolver = MyTenantResolver()
 *     principalResolver = MyPrincipalResolver()
 * }
 * ```
 *
 * **Usage in Routes:**
 * ```kotlin
 * routing {
 *     get("/api/resource") {
 *         // Access app-scoped services via extension functions
 *         val appService = call.getAppService<MyAppService>()
 *
 *         // Or directly from graph
 *         val appConfig = call.appGraph.appConfigEnvironment
 *
 *         // Access user-scoped services
 *         val userService = call.getUserService<MyUserService>()
 *
 *         // Access session-scoped services
 *         val sessionService = call.getSessionService<MySessionService>()
 *
 *         // Access context instances directly
 *         val userInstance = call.userInstance
 *         val sessionInstance = call.sessionInstance
 *     }
 * }
 * ```
 *
 * @see KotlinInjectConfiguration for configuration options
 */
class KotlinInjectPlugin(
    configuration: KotlinInjectConfiguration,
) {
    val appGraph: AppGraph =
        configuration.appGraph
            ?: throw IllegalStateException("AppGraph is required. Please provide it in the configuration.")

    val tenantResolver: TenantResolver = configuration.tenantResolver
    val principalResolver: PrincipalResolver = configuration.principalResolver

    // Get the app-scoped logger from the graph
    private val logger: LogService by lazy {
        (appGraph as CoreApiAppExtensionGraph).appLogManager.withTag("KotlinInjectPlugin")
    }

    init {
        logger.info("Initializing KotlinInject plugin with AppGraph: ${appGraph::class.simpleName}")
        logger.info("Plugin is multiplatform compatible - services accessed via graph inspection")
    }

    companion object Plugin : BaseApplicationPlugin<Application, KotlinInjectConfiguration, KotlinInjectPlugin> {
        override val key: AttributeKey<KotlinInjectPlugin> = AttributeKey("KotlinInject")

        override fun install(
            pipeline: Application,
            configure: KotlinInjectConfiguration.() -> Unit,
        ): KotlinInjectPlugin {
            val configuration = KotlinInjectConfiguration().apply(configure)
            val plugin = KotlinInjectPlugin(configuration)

            // Install the interceptor for automatic context resolution
            val interceptor =
                UserContextInterceptor(
                    appGraph = plugin.appGraph,
                    tenantResolver = plugin.tenantResolver,
                    principalResolver = plugin.principalResolver,
                )

            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                interceptor.intercept(call)
            }

            plugin.logger.info("KotlinInject plugin installed successfully")

            return plugin
        }
    }
}

/**
 * Extension property to access the KotlinInject plugin instance.
 */
val Application.kotlinInject: KotlinInjectPlugin
    get() = plugin(KotlinInjectPlugin)
