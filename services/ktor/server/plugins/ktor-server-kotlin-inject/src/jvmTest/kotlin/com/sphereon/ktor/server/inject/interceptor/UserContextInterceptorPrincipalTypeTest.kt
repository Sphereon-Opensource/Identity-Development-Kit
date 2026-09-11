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

package com.sphereon.ktor.server.inject.interceptor

import com.sphereon.core.api.app.CoreApiAppExtensionGraph
import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.api.log.LogService
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.JwtClaimsInput
import com.sphereon.core.defaults.context.markValidated
import com.sphereon.di.app.AppGraph
import com.sphereon.di.context.BasicSecuredDetails
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.context.IdentityMetadata
import com.sphereon.di.context.IdentityResolutionInput
import com.sphereon.di.context.IdentityResolutionPipeline
import com.sphereon.di.context.IdentityResolutionResult
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.context.ResolutionSource
import com.sphereon.di.context.TenantContextData
import com.sphereon.di.context.UserContext
import com.sphereon.di.context.UserContextInstance
import com.sphereon.di.context.UserContextManager
import com.sphereon.di.session.SessionContextManager
import com.sphereon.di.session.SessionInstance
import com.sphereon.ktor.server.inject.ValidatedJwtClaimsAttribute
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import com.sphereon.ktor.server.inject.resolver.PrincipalResolver
import io.ktor.http.Headers
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.util.Attributes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The identity pipeline is the single authority for principal classification.
 * Once it resolves a request as WORKLOAD, the session opened for that request
 * must carry the same classification: the interceptor has to use the 5-arg
 * [SessionContextManager.createOrGetFromId] overload instead of one of the
 * defaulting overloads that would silently downgrade to USER.
 */
class UserContextInterceptorPrincipalTypeTest {
    @Test
    fun anonymousTokenIdentityUsesFrameworkPrincipalSentinel() {
        val resolution =
            IdentityResolutionResult(
                tenantId = "tenant-1",
                principalId = null,
                principalType = PrincipalType.ANONYMOUS,
                metadata = IdentityMetadata(resolvedFrom = ResolutionSource.TOKEN),
            )

        val normalized = resolution.withAnonymousPrincipalSentinel()

        assertEquals(IdentityConstants.ANONYMOUS_PRINCIPAL_ID, normalized.principalId)
        assertEquals("tenant-1", normalized.tenantId)
        assertEquals(PrincipalType.ANONYMOUS, normalized.principalType)
    }

    @Test
    fun namedIdentityIsNotRewritten() {
        val resolution =
            IdentityResolutionResult(
                tenantId = "tenant-1",
                principalId = "service-1",
                principalType = PrincipalType.WORKLOAD,
                metadata = IdentityMetadata(resolvedFrom = ResolutionSource.TOKEN),
            )

        assertSame(resolution, resolution.withAnonymousPrincipalSentinel())
    }

    @Test
    fun workloadClassificationFromPipelineReachesSessionCreation() =
        runTest {
            val workloadResolution =
                IdentityResolutionResult(
                    tenantId = "platform",
                    principalId = "service-crypto",
                    principalType = PrincipalType.WORKLOAD,
                    metadata = IdentityMetadata(resolvedFrom = ResolutionSource.TOKEN),
                )

            var capturedIdentityResolution: IdentityResolutionResult? = null
            var capturedSessionArgs: Array<out Any?>? = null

            val sessionInstance =
                proxy<SessionInstance> { method, _ ->
                    when (method.name) {
                        "getSessionId" -> "session-under-test"
                        else -> error("Unexpected SessionInstance call: ${method.name}")
                    }
                }
            val sessionContextManager =
                proxy<SessionContextManager> { method, arguments ->
                    if (method.name == "createOrGetFromId" && arguments?.size == 5) {
                        capturedSessionArgs = arguments
                        sessionInstance
                    } else {
                        error("Unexpected SessionContextManager call: ${method.name}")
                    }
                }
            val tenantData =
                object : TenantContextData {
                    override val tenantId: String = "platform"
                }
            val userContext =
                proxy<UserContext> { method, _ ->
                    when (method.name) {
                        "getTenant" -> tenantData
                        else -> error("Unexpected UserContext call: ${method.name}")
                    }
                }
            val contextInstance =
                proxy<UserContextInstance> { method, _ ->
                    when (method.name) {
                        "getContext" -> userContext
                        "getContextId" -> "user-context-under-test"
                        "getSessionContextManager" -> sessionContextManager
                        else -> error("Unexpected UserContextInstance call: ${method.name}")
                    }
                }
            val userContextManager =
                proxy<UserContextManager> { method, arguments ->
                    if (method.name == "createOrGetFromResolvedInputs" && arguments?.size == 4) {
                        capturedIdentityResolution = arguments[2] as IdentityResolutionResult
                        contextInstance
                    } else {
                        error("Unexpected UserContextManager call: ${method.name}")
                    }
                }

            val logService = proxy<LogService> { _, _ -> null }
            val appLogManager =
                proxy<AppLogManager> { method, _ ->
                    when (method.name) {
                        "withTag" -> logService
                        else -> error("Unexpected AppLogManager call: ${method.name}")
                    }
                }
            val pipeline =
                object : IdentityResolutionPipeline {
                    override suspend fun resolve(input: IdentityResolutionInput): IdentityResolutionResult = workloadResolution
                }
            val appGraph =
                Proxy.newProxyInstance(
                    AppGraph::class.java.classLoader,
                    arrayOf(AppGraph::class.java, CoreApiAppExtensionGraph::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "getAppLogManager" -> appLogManager
                        "getIdentityResolutionPipeline" -> pipeline
                        "getUserContextManager" -> userContextManager
                        else -> error("Unexpected AppGraph call: ${method.name}")
                    }
                } as AppGraph

            val attributes = Attributes()
            attributes.put(
                ValidatedJwtClaimsAttribute,
                JwtClaimsInput(
                    claims =
                        mapOf(
                            "sub" to JsonPrimitive("service-crypto"),
                            "azp" to JsonPrimitive("service-crypto"),
                        ),
                    rawToken = "validated-workload-token",
                ).markValidated(),
            )
            val request =
                proxy<ApplicationRequest> { method, _ ->
                    when (method.name) {
                        "getHeaders" -> Headers.Empty
                        else -> error("Unexpected ApplicationRequest call: ${method.name}")
                    }
                }
            val call =
                proxy<ApplicationCall> { method, _ ->
                    when (method.name) {
                        "getAttributes" -> attributes
                        "getRequest" -> request
                        else -> error("Unexpected ApplicationCall call: ${method.name}")
                    }
                }

            val interceptor =
                UserContextInterceptor(
                    appGraph = appGraph,
                    tenantResolver = FixedTenantResolver("platform"),
                    principalResolver =
                        object : PrincipalResolver {
                            override fun resolve(call: ApplicationCall) = DefaultPrincipalInputString("service-crypto")
                        },
                )

            interceptor.intercept(call)

            assertEquals(PrincipalType.WORKLOAD, requireNotNull(capturedIdentityResolution).principalType)
            val arguments = requireNotNull(capturedSessionArgs)
            assertEquals(false, arguments[2])
            assertEquals("validated-workload-token", (arguments[3] as BasicSecuredDetails).jwt)
            assertEquals(PrincipalType.WORKLOAD, arguments[4])
        }
}

private inline fun <reified T> proxy(crossinline handler: (method: Method, args: Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { proxyInstance, method, args ->
        when (method.name) {
            "toString" -> "proxy<${T::class.simpleName}>"
            "hashCode" -> System.identityHashCode(proxyInstance)
            "equals" -> proxyInstance === args?.get(0)
            else -> handler(method, args)
        }
    } as T
