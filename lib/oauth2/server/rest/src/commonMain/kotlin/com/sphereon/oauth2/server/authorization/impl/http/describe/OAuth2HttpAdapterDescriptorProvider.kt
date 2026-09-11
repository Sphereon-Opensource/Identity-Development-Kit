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

// Role-neutral OAuth HTTP capability. Executable graph bindings remain in the service assembly.

import com.sphereon.core.api.http.command.TenantPathPolicy
import com.sphereon.core.api.http.describe.EndpointAuthPolicy
import com.sphereon.core.api.http.describe.HttpAdapterDescription
import com.sphereon.core.api.http.describe.HttpAdapterDescriptorProvider
import com.sphereon.core.api.http.describe.HttpAdapterMount
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.oauth2.server.authorization.command.attestation.AttestationChallengeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.authorization.AuthorizeCallbackHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.authorization.AuthorizeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.authorization.IaeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.device.DeviceAuthorizationHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationApprovalHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationApprovalSubmitHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationEntryHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.device.DeviceVerificationSubmitHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.discovery.JwksHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.discovery.OAuth2ServerMetadataHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.discovery.OpenidDiscoveryHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.discovery.OAuth2DiscoveryHttpContract
import com.sphereon.oauth2.server.authorization.command.federation.FederationAuthorizeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.federation.FederationCallbackHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.federation.ListFederationProvidersHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.federation.ReconciliationAuthorizeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.federation.ReconciliationCallbackHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.introspection.IntrospectionHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.login.AccountActionPageHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginAssetHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginCancelHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginPageHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginSubmitHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.login.LoginWebAuthnAssertionBeginHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.logout.EndSessionGetHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.logout.EndSessionPostHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.par.ParHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.revocation.RevocationHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.token.TokenHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.command.userinfo.UserInfoHttpEndpointCommand
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
    override val id: String = OAuth2DiscoveryHttpContract.ADAPTER_ID

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
                        handlerCommandId = OAuth2ServerMetadataHttpEndpointCommand.COMMAND_ID,
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/oauth-authorization-server/{tenant-path}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "serverMetadata",
                        handlerCommandId = OAuth2ServerMetadataHttpEndpointCommand.COMMAND_ID,
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/openid-configuration",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "openidConfigurationDefault",
                        handlerCommandId = OpenidDiscoveryHttpEndpointCommand.COMMAND_ID,
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/openid-configuration/{tenant-path}",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "openidConfiguration",
                        handlerCommandId = OpenidDiscoveryHttpEndpointCommand.COMMAND_ID,
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/jwks.json",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "jwks",
                        handlerCommandId = JwksHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = OpenidDiscoveryHttpEndpointCommand.COMMAND_ID,
                        // Anonymous OIDC discovery (path-issuer form); must not fail-closed.
                        authPolicy = EndpointAuthPolicy.PUBLIC,
                    ),
                    // The discovery document this adapter serves for a path-bearing issuer
                    // advertises `jwks_uri` under the same issuer path. Without this endpoint the
                    // server publishes a key set URL it does not serve, and every relying party
                    // that pins the advertised URL reports the resulting empty key set as an
                    // invalid signature rather than as a missing key.
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/.well-known/jwks.json",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "jwksPathIssuer",
                        handlerCommandId = JwksHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = TokenHttpEndpointCommand.COMMAND_ID,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/introspect",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "introspectToken",
                        handlerCommandId = IntrospectionHttpEndpointCommand.COMMAND_ID,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/revoke",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "revokeToken",
                        handlerCommandId = RevocationHttpEndpointCommand.COMMAND_ID,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/par",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "pushedAuthorizationRequest",
                        handlerCommandId = ParHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = AuthorizeHttpEndpointCommand.COMMAND_ID,
                        summary = "Browser-facing authorization endpoint that responds with redirects",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/authorize",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "authorizePost",
                        handlerCommandId = AuthorizeHttpEndpointCommand.COMMAND_ID,
                        summary = "RFC 6749 §3.1 / OIDC Core 1.0 §3.1.2.1 authorization endpoint accepting form-encoded body",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/authorize/callback",
                        operationId = "authorizeCallback",
                        handlerCommandId = AuthorizeCallbackHttpEndpointCommand.COMMAND_ID,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/iae",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "interactiveAuthorization",
                        handlerCommandId = IaeHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = UserInfoHttpEndpointCommand.COMMAND_ID,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/userinfo",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "userinfoPost",
                        handlerCommandId = UserInfoHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = FederationAuthorizeHttpEndpointCommand.COMMAND_ID,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/federation/callback",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "federationCallback",
                        handlerCommandId = FederationCallbackHttpEndpointCommand.COMMAND_ID,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/federation/providers",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "federationProviders",
                        handlerCommandId = ListFederationProvidersHttpEndpointCommand.COMMAND_ID,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/reconciliation/authorize",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "reconciliationAuthorize",
                        handlerCommandId = ReconciliationAuthorizeHttpEndpointCommand.COMMAND_ID,
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/reconciliation/callback",
                        produces = setOf(MediaType.ApplicationJson),
                        operationId = "reconciliationCallback",
                        handlerCommandId = ReconciliationCallbackHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = RegisterPreAuthorizedCodeHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = EndSessionGetHttpEndpointCommand.COMMAND_ID,
                        summary = "OIDC RP-Initiated Logout 1.0 end-session endpoint (GET)",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/logout",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "endSessionPost",
                        handlerCommandId = EndSessionPostHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = AttestationChallengeHttpEndpointCommand.COMMAND_ID,
                        summary = "OAuth 2.0 Attestation-Based Client Auth challenge endpoint",
                    ),
                ),
        )
}

/**
 * AppScope descriptor for [OAuth2LoginHttpAdapter] (the AS first-party browser-login surface).
 *
 * Exposes five login routes (`GET /login`, `POST /login`, `POST /login/cancel`,
 * `POST /login/webauthn/assertion/begin`, and `GET /login/assets/{path...}`). The tail-wildcard segment matches arbitrary remaining path
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
                        handlerCommandId = LoginPageHttpEndpointCommand.COMMAND_ID,
                        summary = "Render the Authorization Server's first-party login page",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/login",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "submitLogin",
                        handlerCommandId = LoginSubmitHttpEndpointCommand.COMMAND_ID,
                        summary = "Submit username + password to the Authorization Server's login form",
                    ),
                    LoginWebAuthnAssertionBeginHttpEndpointCommand.ENDPOINT,
                    AccountActionPageHttpEndpointCommand.ENDPOINT,
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/login/cancel",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "cancelLogin",
                        handlerCommandId = LoginCancelHttpEndpointCommand.COMMAND_ID,
                        summary = "User cancelled the login flow; redirect back to the RP with error=access_denied",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/login/assets/{path...}",
                        operationId = "loginAsset",
                        handlerCommandId = LoginAssetHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = DeviceAuthorizationHttpEndpointCommand.COMMAND_ID,
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
                        handlerCommandId = DeviceVerificationEntryHttpEndpointCommand.COMMAND_ID,
                        summary = "Render the RFC 8628 device verification entry form",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/device",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "deviceSubmit",
                        handlerCommandId = DeviceVerificationSubmitHttpEndpointCommand.COMMAND_ID,
                        summary = "Submit the user_code to the RFC 8628 device verification flow",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.GET,
                        pathPattern = "/device/approve",
                        produces = setOf(MediaType.Custom("text/html")),
                        operationId = "deviceApproveGet",
                        handlerCommandId = DeviceVerificationApprovalHttpEndpointCommand.COMMAND_ID,
                        summary = "Render the RFC 8628 device approval prompt",
                    ),
                    HttpEndpointDescriptor(
                        method = HttpMethod.POST,
                        pathPattern = "/device/approve",
                        consumes = setOf(MediaType.ApplicationFormUrlEncoded),
                        operationId = "deviceApprove",
                        handlerCommandId = DeviceVerificationApprovalSubmitHttpEndpointCommand.COMMAND_ID,
                        summary = "Submit the allow / deny decision for the RFC 8628 device approval",
                    ),
                ),
        )
}
