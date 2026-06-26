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

package com.sphereon.oauth2.server.authorization.impl.http.describe

import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.EndpointAuthPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2AttestationHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2AuthorizationHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2DeviceAuthorizationHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2DeviceVerificationHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2DiscoveryHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2EndSessionHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2FederationHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2InternalHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2LoginHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2OpenidDiscoveryPathIssuerHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2TokenHttpAdapter
import com.sphereon.oauth2.server.authorization.impl.http.OAuth2UserInfoHttpAdapter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

private val LEADING_SLUG_MOUNT =
    HttpAdapterMount(
        serverPrefix = "",
        adapterBasePath = "/",
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2),
    )
private val WELL_KNOWN_SUFFIX_MOUNT =
    HttpAdapterMount(
        serverPrefix = "",
        adapterBasePath = "/",
        tenantPathPolicy = TenantPathPolicy.WellKnownSuffix(maxDepth = 2),
    )
private val REQUIRED_LEADING_SLUG_MOUNT =
    HttpAdapterMount(
        serverPrefix = "",
        adapterBasePath = "/",
        tenantPathPolicy = TenantPathPolicy.LeadingSlug(maxDepth = 2, required = true),
    )

/**
 * AppScope descriptor for [OAuth2DiscoveryHttpAdapter]. Lets the [com.sphereon.core.api.http.dispatch.HttpAdapterCatalog]
 * be built at startup without instantiating the SessionScope adapter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2DiscoveryHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2DiscoveryHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = WELL_KNOWN_SUFFIX_MOUNT,
            // OAuth2/OIDC discovery + JWKS are RFC-mandated anonymous documents. They MUST be
            // PUBLIC: the fail-closed EndpointAuthCatalog silent-404s any PROTECTED endpoint for an
            // anonymous caller, and every token validator fetches <issuer>/.well-known/jwks.json
            // anonymously to verify signatures.
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/oauth-authorization-server",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "serverMetadataDefault",
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/oauth-authorization-server/{tenant-path}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "serverMetadata",
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/openid-configuration",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "openidConfigurationDefault",
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/openid-configuration/{tenant-path}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "openidConfiguration",
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/jwks.json",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "jwks",
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                ),
        )
}

/**
 * OIDC Discovery 1.0 descriptor for path-bearing issuers:
 * `/<issuer-path>/.well-known/openid-configuration`.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2OpenidDiscoveryPathIssuerDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2OpenidDiscoveryPathIssuerHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = REQUIRED_LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/openid-configuration",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "openidConfigurationLegacyPrefix",
                        // Anonymous OIDC discovery (path-issuer form); must not fail-closed.
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2TokenHttpAdapter].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2TokenHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2TokenHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/token",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "token",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/introspect",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "introspectToken",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/revoke",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "revokeToken",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/par",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "pushedAuthorizationRequest",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2AuthorizationHttpAdapter].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2AuthorizationHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2AuthorizationHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/authorize",
                        operationId = "authorizeGet",
                        summary = "Browser-facing authorization endpoint that responds with redirects",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/authorize",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "authorizePost",
                        summary = "RFC 6749 §3.1 / OIDC Core 1.0 §3.1.2.1 authorization endpoint accepting form-encoded body",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/authorize/callback",
                        operationId = "authorizeCallback",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/iae",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "interactiveAuthorization",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2UserInfoHttpAdapter].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2UserInfoHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2UserInfoHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/userinfo",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "userinfoGet",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/userinfo",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "userinfoPost",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2FederationHttpAdapter].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2FederationHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2FederationHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/federation/authorize",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "federationAuthorize",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/federation/callback",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "federationCallback",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/federation/providers",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "federationProviders",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/reconciliation/authorize",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "reconciliationAuthorize",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/reconciliation/callback",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "reconciliationCallback",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2InternalHttpAdapter].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2InternalHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2InternalHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/internal/preauth/register",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "registerPreAuthCode",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/internal/provision/signing-key",
                        consumes = setOf(MediaType.ApplicationJson),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "provisionSigningKey",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2EndSessionHttpAdapter] (the OIDC RP-Initiated Logout 1.0
 * end-session surface). Both GET and POST forms are mounted at `/logout`, per RP-Initiated
 * Logout §2.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2EndSessionHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2EndSessionHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/logout",
                        operationId = "endSessionGet",
                        summary = "OIDC RP-Initiated Logout 1.0 end-session endpoint (GET)",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/logout",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "endSessionPost",
                        summary = "OIDC RP-Initiated Logout 1.0 end-session endpoint (POST)",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2AttestationHttpAdapter]. Registers the
 * `GET /attestation-challenge` endpoint with the [com.sphereon.core.api.http.dispatch.HttpAdapterCatalog]
 * so the dispatcher routes the spec endpoint
 * (draft-ietf-oauth-attestation-based-client-auth §5) to the SessionScope adapter. The endpoint
 * itself enforces the `attestation` + `attestationChallengeRequired` policy and returns a 404
 * `oauth2ErrorResponse` body when the deployment has not opted in, so the dispatcher catalog
 * carries the registration unconditionally.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2AttestationHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2AttestationHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/attestation-challenge",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "createAttestationChallenge",
                        summary = "OAuth 2.0 Attestation-Based Client Auth challenge endpoint",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2LoginHttpAdapter] (the AS first-party browser-login surface).
 *
 * Exposes four login routes (`GET /login`, `POST /login`, `POST /login/cancel`,
 * `GET /login/assets/{path...}`). The tail-wildcard segment matches arbitrary remaining path
 * segments, so a single descriptor covers flat, folder-scoped, and deeply nested asset trees. The
 * runtime adapter delegates the actual asset lookup to
 * [com.sphereon.oauth2.server.authorization.command.login.LoginAssetHttpEndpointCommand], which
 * reads the relative asset path from the captured `path` parameter.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2LoginHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2LoginHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/login",
                        produces = setOf(MediaType.Custom("text/html")),
                        operationId = "renderLoginPage",
                        summary = "Render the Authorization Server's first-party login page",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/login",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "submitLogin",
                        summary = "Submit username + password to the Authorization Server's login form",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/login/cancel",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "cancelLogin",
                        summary = "User cancelled the login flow; redirect back to the RP with error=access_denied",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/login/assets/{path...}",
                        operationId = "loginAsset",
                        summary = "Serve a static asset bundled with the login renderer",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2DeviceAuthorizationHttpAdapter] (RFC 8628 §3.1
 * `/device_authorization`). Registered unconditionally so the dispatcher catalog can route the
 * spec endpoint; the adapter itself enforces the per-server `deviceFlow` policy and returns a
 * 404 `oauth2ErrorResponse` when the deployment has not opted in.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2DeviceAuthorizationHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2DeviceAuthorizationHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/device_authorization",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "deviceAuthorization",
                        summary = "RFC 8628 OAuth 2.0 Device Authorization Endpoint",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2DeviceVerificationHttpAdapter] (RFC 8628 §3.3 user-interaction
 * surface served at `/device` and `/device/approve`). Registered unconditionally so the dispatcher
 * catalog can route the user-facing routes; the adapter's endpoint commands enforce the per-server
 * `deviceFlow` policy and return a 404 `oauth2ErrorResponse` when the deployment has not opted in.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<HttpAdapterDescriptorProvider>())
class OAuth2DeviceVerificationHttpAdapterDescriptorProvider : HttpAdapterDescriptorProvider {
    override val id: String = OAuth2DeviceVerificationHttpAdapter.ID

    override fun describe(): HttpAdapterDescription =
        HttpAdapterDescription(
            id = id,
            mount = LEADING_SLUG_MOUNT,
            endpoints =
                listOf(
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/device",
                        produces = setOf(MediaType.Custom("text/html")),
                        operationId = "deviceEntry",
                        summary = "Render the RFC 8628 device verification entry form",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/device",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "deviceSubmit",
                        summary = "Submit the user_code to the RFC 8628 device verification flow",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/device/approve",
                        produces = setOf(MediaType.Custom("text/html")),
                        operationId = "deviceApproveGet",
                        summary = "Render the RFC 8628 device approval prompt",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/device/approve",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "deviceApprove",
                        summary = "Submit the allow / deny decision for the RFC 8628 device approval",
                    ),
                ),
        )
}
