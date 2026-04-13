package com.sphereon.oauth2.server.authorization.impl.http.describe

import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2HttpAdapter
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * AppScope descriptor provider for OAuth2HttpAdapter.
 *
 * Provides metadata-only information about the OAuth2/OIDC endpoints,
 * allowing the HttpAdapterCatalog to be built at startup without
 * instantiating SessionScope adapters.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2HttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2HttpAdapter.ID

    override fun describe(): HttpAdapterDescription = HttpAdapterDescription(
        id = id,
        mount = HttpAdapterMount(
            serverPrefix = "",
            adapterBasePath = "/"
        ),
        endpoints = listOf(
            // RFC 6749 - Token endpoint
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/token",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "token"
            ),
            // RFC 6749 - Authorization endpoint
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/authorize",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "authorize"
            ),
            // RFC 9126 - Pushed Authorization Request
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/par",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "pushedAuthorizationRequest"
            ),
            // RFC 7662 - Token Introspection
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/introspect",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "introspectToken"
            ),
            // RFC 7009 - Token Revocation
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/revoke",
                consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "revokeToken"
            ),
            // RFC 8414 - OAuth2 Discovery
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/.well-known/oauth-authorization-server",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "serverMetadataDefault"
            ),
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/.well-known/oauth-authorization-server/{tenant-path}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "serverMetadata"
            ),
            // OIDC Discovery
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/.well-known/openid-configuration",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "openidConfigurationDefault"
            ),
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/.well-known/openid-configuration/{tenant-path}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "openidConfiguration"
            ),
            // OIDC UserInfo
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/userinfo",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "userinfoGet"
            ),
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/userinfo",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "userinfoPost"
            ),
            // JWKS
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/.well-known/jwks.json",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "jwks"
            ),
            // Federation callback
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/federation/callback",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "federationCallback"
            )
        )
    )
}
