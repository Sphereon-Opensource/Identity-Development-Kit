package com.sphereon.oauth2.server.authorization.impl.http

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.impl.TestFixtures
import com.sphereon.oauth2.server.authorization.impl.service.AuthorizationServerServiceImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryTokenStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.oidc.OidcScopeClaimsMapperImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.TestUserAuthenticationProvider
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for OIDC-specific HTTP endpoints:
 * - GET /.well-known/openid-configuration
 * - GET /userinfo
 * - GET /.well-known/jwks.json
 */
class OAuth2HttpAdapterOidcTest {

    private val ctx = OAuth2ServerTestContext("oauth2-oidc-test", this)
    private val json = Json { ignoreUnknownKeys = true }

    // ========================================================================
    // OIDC Discovery
    // ========================================================================

    @Test
    fun oidcDiscoveryReturns200WhenEnabled() = runTest {
        val configProvider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf(
                    "default" to OAuth2ServerInstanceConfig(
                        baseUrl = "https://auth.example.com",
                        oidc = FeaturePolicy.SUPPORTED,
                        introspection = FeaturePolicy.SUPPORTED
                    )
                )
            )
        )
        val authServerService = (ctx.session.component as AuthorizationServerServiceImpl.Component).authorizationServerService
        val adapter = OAuth2HttpAdapter(authServerService, configProvider, TestUserAuthenticationProvider(), OidcScopeClaimsMapperImpl())

        val request = GenericHttpRequest(
            method = "GET",
            path = "/.well-known/openid-configuration",
            headers = mapOf("host" to "auth.example.com")
        )

        val response = adapter.handleRequest(request)

        assertEquals(200, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertNotNull(body.jsonObject["issuer"])
        assertNotNull(body.jsonObject["token_endpoint"])
    }

    @Test
    fun oidcDiscoveryReturns404WhenDisabled() = runTest {
        val configProvider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf(
                    "default" to OAuth2ServerInstanceConfig(
                        baseUrl = "https://auth.example.com",
                        oidc = FeaturePolicy.DISABLED
                    )
                )
            )
        )
        val authServerService = (ctx.session.component as AuthorizationServerServiceImpl.Component).authorizationServerService
        val adapter = OAuth2HttpAdapter(authServerService, configProvider, TestUserAuthenticationProvider(), OidcScopeClaimsMapperImpl())

        val request = GenericHttpRequest(
            method = "GET",
            path = "/.well-known/openid-configuration",
            headers = mapOf("host" to "auth.example.com")
        )

        val response = adapter.handleRequest(request)

        assertEquals(404, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertEquals("not_found", body.jsonObject["error"]?.jsonPrimitive?.content)
    }

    // ========================================================================
    // UserInfo Endpoint
    // ========================================================================

    @Test
    fun userinfoReturns404WhenOidcDisabled() = runTest {
        val configProvider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf(
                    "default" to OAuth2ServerInstanceConfig(
                        baseUrl = "https://auth.example.com",
                        oidc = FeaturePolicy.DISABLED
                    )
                )
            )
        )
        val authServerService = (ctx.session.component as AuthorizationServerServiceImpl.Component).authorizationServerService
        val adapter = OAuth2HttpAdapter(authServerService, configProvider, TestUserAuthenticationProvider(), OidcScopeClaimsMapperImpl())

        val request = GenericHttpRequest(
            method = "GET",
            path = "/userinfo",
            headers = mapOf(
                "host" to "auth.example.com",
                "authorization" to "Bearer some-token"
            )
        )

        val response = adapter.handleRequest(request)
        assertEquals(404, response.statusCode)
    }

    @Test
    fun userinfoReturns401WithoutBearerToken() = runTest {
        val configProvider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf(
                    "default" to OAuth2ServerInstanceConfig(
                        baseUrl = "https://auth.example.com",
                        oidc = FeaturePolicy.SUPPORTED
                    )
                )
            )
        )
        val authServerService = (ctx.session.component as AuthorizationServerServiceImpl.Component).authorizationServerService
        val adapter = OAuth2HttpAdapter(authServerService, configProvider, TestUserAuthenticationProvider(), OidcScopeClaimsMapperImpl())

        val request = GenericHttpRequest(
            method = "GET",
            path = "/userinfo",
            headers = mapOf("host" to "auth.example.com")
            // No Authorization header
        )

        val response = adapter.handleRequest(request)
        assertEquals(401, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertEquals("invalid_token", body.jsonObject["error"]?.jsonPrimitive?.content)
    }

    // ========================================================================
    // JWKS Endpoint
    // ========================================================================

    @Test
    fun jwksEndpointReturns200() = runTest {
        val configProvider = TestOAuth2ServersConfigProvider()
        val authServerService = (ctx.session.component as AuthorizationServerServiceImpl.Component).authorizationServerService
        val adapter = OAuth2HttpAdapter(authServerService, configProvider, TestUserAuthenticationProvider(), OidcScopeClaimsMapperImpl())

        val request = GenericHttpRequest(
            method = "GET",
            path = "/.well-known/jwks.json",
            headers = mapOf("host" to "auth.example.com")
        )

        val response = adapter.handleRequest(request)

        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        assertEquals("max-age=3600", response.headers["Cache-Control"])

        val body = json.parseToJsonElement(response.body!!)
        // Should have a keys array (possibly empty if no server identifier configured)
        assertNotNull(body.jsonObject["keys"])
    }

    @Test
    fun jwksEndpointAvailableEvenWithOidcDisabled() = runTest {
        val configProvider = TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf(
                    "default" to OAuth2ServerInstanceConfig(
                        baseUrl = "https://auth.example.com",
                        oidc = FeaturePolicy.DISABLED
                    )
                )
            )
        )
        val authServerService = (ctx.session.component as AuthorizationServerServiceImpl.Component).authorizationServerService
        val adapter = OAuth2HttpAdapter(authServerService, configProvider, TestUserAuthenticationProvider(), OidcScopeClaimsMapperImpl())

        val request = GenericHttpRequest(
            method = "GET",
            path = "/.well-known/jwks.json",
            headers = mapOf("host" to "auth.example.com")
        )

        val response = adapter.handleRequest(request)

        // JWKS is always available (needed for JWT access token verification)
        assertEquals(200, response.statusCode)
    }

    // ========================================================================
    // Federation Callback
    // ========================================================================

    @Test
    fun federationCallbackReturns404WithoutProvider() = runTest {
        val configProvider = TestOAuth2ServersConfigProvider()
        val authServerService = (ctx.session.component as AuthorizationServerServiceImpl.Component).authorizationServerService
        // No federatedAuthProvider provided (null)
        val adapter = OAuth2HttpAdapter(authServerService, configProvider, TestUserAuthenticationProvider(), OidcScopeClaimsMapperImpl())

        val request = GenericHttpRequest(
            method = "GET",
            path = "/federation/callback",
            queryParameters = mapOf("code" to "test-code", "state" to "test-state"),
            headers = mapOf("host" to "auth.example.com")
        )

        val response = adapter.handleRequest(request)

        assertEquals(404, response.statusCode)
        val body = json.parseToJsonElement(response.body!!)
        assertEquals("not_found", body.jsonObject["error"]?.jsonPrimitive?.content)
    }
}
