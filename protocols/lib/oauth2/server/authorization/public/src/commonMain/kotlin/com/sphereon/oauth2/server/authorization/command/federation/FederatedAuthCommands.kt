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

package com.sphereon.oauth2.server.authorization.command.federation

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.provider.AuthenticatedUser
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.AuthenticationHint
import com.sphereon.oauth2.server.authorization.provider.FlowContext
import com.sphereon.oauth2.server.authorization.provider.UserInfo
import com.sphereon.oauth2.server.authorization.storage.PendingFederation

/*
 * Federated authentication ServiceCommand contracts. Command interfaces live here so LOCAL
 * vs SERVER routing works (the `@GenerateRoutedCommands` plumbing sees them in `-public`),
 * and so transport-routed callers (gRPC) can reference Args/Result types without a hard
 * dependency on the `-impl` module. Tenant is intentionally NOT part of any Args: impls read it from `SessionExecution.tenantId` at the use site.
 */

// ============================================================================
// InitiateProviderAuthenticationCommand
// ============================================================================

data class InitiateProviderAuthenticationArgs(
    val sessionId: String,
    val returnUrl: String,
    val providerId: String,
    val callbackPath: String? = null,
    val flowContext: FlowContext? = null,
    val hint: AuthenticationHint? = null,
    val acrValues: List<String> = emptyList(),
    val forceReauth: Boolean = false,
    /**
     * Opaque application / login-surface id from `AuthenticationContext.applicationId`.
     * Persisted on the pending federation record so the callback can scope identity
     * linking to the application the login targets. Null = application-agnostic flow.
     */
    val applicationId: String? = null,
)

/**
 * Returns the upstream authorization URL the caller redirects the browser to. The error type
 * aligns with [com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider] so
 * the facade can delegate without an `IdkError`-to-domain mapping shim.
 */
interface InitiateProviderAuthenticationCommand : ServiceCommand<InitiateProviderAuthenticationArgs, AuthorizationUrl, AuthenticationError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.initiate-provider-authentication"
    }
}

/**
 * Domain-typed wrapper for the upstream authorization URL produced by
 * [InitiateProviderAuthenticationCommand]. A typed wrapper preserves binary-transport
 * serialization shape so the same command can route LOCAL or via gRPC without changing its
 * declared Result type.
 */
data class AuthorizationUrl(
    val value: String,
)

// ============================================================================
// HandleFederationCallbackCommand
// ============================================================================

data class HandleFederationCallbackArgs(
    val code: String? = null,
    val state: String,
    val error: String? = null,
    val errorDescription: String? = null,
)

/**
 * Normal federation outcome: the pending-federation record is completed, the claims cache is
 * populated, and [sessionId] identifies the local authorization session ready for code issuance.
 */
data class FederationCompleteOutcome(
    val sessionId: String,
)

/**
 * Reconciliation outcome: claims were forwarded to the auth-bridge and the browser is
 * redirected to [redirectUrl]. No local authorization session is created.
 */
data class ReconciliationCompleteOutcome(
    val redirectUrl: String,
)

data class UpstreamAuthorizationErrorOutcome(
    val sessionId: String,
    val error: String,
    val errorDescription: String?,
)

/**
 * Dispatched outcome of [HandleFederationCallbackCommand]: either the normal login
 * completed (a session is ready for code issuance), or a reconciliation flow completed
 * (auth-bridge redirect URL returned). `outcomeType` is the discriminator that downstream
 * rendering uses to pick the response shape.
 *
 * Uses a type-tag + payload rather than a sealed hierarchy because this type is public API
 * and may cross KMP JS boundaries where sealed-class-with-data-subclasses is unsafe for JS
 * export (per IDK KMP JS export rules).
 */
data class FederationCallbackOutcome(
    val outcomeType: FederationCallbackOutcomeType,
    val federation: FederationCompleteOutcome? = null,
    val reconciliation: ReconciliationCompleteOutcome? = null,
    val upstreamError: UpstreamAuthorizationErrorOutcome? = null,
) {
    init {
        require(
            (outcomeType == FederationCallbackOutcomeType.FEDERATION_COMPLETE && federation != null && reconciliation == null && upstreamError == null) ||
                (outcomeType == FederationCallbackOutcomeType.RECONCILIATION_COMPLETE && reconciliation != null && federation == null && upstreamError == null) ||
                (outcomeType == FederationCallbackOutcomeType.UPSTREAM_ERROR && upstreamError != null && federation == null && reconciliation == null),
        ) { "FederationCallbackOutcome payload must match outcomeType" }
    }

    companion object {
        fun federationComplete(sessionId: String): FederationCallbackOutcome =
            FederationCallbackOutcome(
                outcomeType = FederationCallbackOutcomeType.FEDERATION_COMPLETE,
                federation = FederationCompleteOutcome(sessionId = sessionId),
            )

        fun reconciliationComplete(redirectUrl: String): FederationCallbackOutcome =
            FederationCallbackOutcome(
                outcomeType = FederationCallbackOutcomeType.RECONCILIATION_COMPLETE,
                reconciliation = ReconciliationCompleteOutcome(redirectUrl = redirectUrl),
            )

        fun upstreamError(sessionId: String, error: String, errorDescription: String?): FederationCallbackOutcome =
            FederationCallbackOutcome(
                outcomeType = FederationCallbackOutcomeType.UPSTREAM_ERROR,
                upstreamError = UpstreamAuthorizationErrorOutcome(sessionId, error, errorDescription),
            )
    }
}

enum class FederationCallbackOutcomeType {
    FEDERATION_COMPLETE,
    RECONCILIATION_COMPLETE,
    UPSTREAM_ERROR,
}

interface HandleFederationCallbackCommand : ServiceCommand<HandleFederationCallbackArgs, FederationCallbackOutcome, AuthenticationError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.handle-federation-callback"
    }
}

// ============================================================================
// ExchangeCodeAndExtractClaimsCommand
// ============================================================================

data class ExchangeCodeAndExtractClaimsArgs(
    val code: String,
    val pending: PendingFederation,
    val providerConfig: FederationProviderConfig,
)

/**
 * Merged upstream claim map plus structurally-preserved `acr` / `amr` / `sid`. The generic
 * `.mapValues { v.toString() }` step in the merge would otherwise mangle list-valued `amr`.
 * [upstreamSid] is propagated so inbound Back-Channel Logout can terminate the precise local
 * session bound to the upstream one.
 */
data class FederatedExchangeResult(
    val claims: Map<String, Any>,
    val upstreamAcr: String?,
    val upstreamAmr: List<String>?,
    val upstreamSid: String?,
    val upstreamSubject: String,
    val upstreamAuthTime: kotlin.time.Instant?,
    val validatedAt: kotlin.time.Instant,
)

interface ExchangeCodeAndExtractClaimsCommand : ServiceCommand<ExchangeCodeAndExtractClaimsArgs, FederatedExchangeResult, AuthenticationError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.exchange-code-and-extract-claims"
    }
}

// ============================================================================
// HandleFederationOutcomeCommand
// ============================================================================

data class HandleFederationOutcomeArgs(
    val exchange: FederatedExchangeResult,
    val state: String,
    val pending: PendingFederation,
    val providerConfig: FederationProviderConfig,
)

interface HandleFederationOutcomeCommand : ServiceCommand<HandleFederationOutcomeArgs, FederationCompleteOutcome, AuthenticationError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.handle-federation-outcome"
    }
}

// ============================================================================
// HandleReconciliationOutcomeCommand
// ============================================================================

data class HandleReconciliationOutcomeArgs(
    val rawClaims: Map<String, Any>,
    val pending: PendingFederation,
    val providerConfig: FederationProviderConfig,
)

interface HandleReconciliationOutcomeCommand : ServiceCommand<HandleReconciliationOutcomeArgs, ReconciliationCompleteOutcome, AuthenticationError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.handle-reconciliation-outcome"
    }
}

// ============================================================================
// GetAuthenticatedUserCommand
// ============================================================================

data class GetAuthenticatedUserArgs(
    val sessionId: String,
)

/**
 * Carries the resolved [AuthenticatedUser], or `null` when the session has no completed
 * federation record. A typed wrapper keeps the Result shape compatible with binary transport.
 */
data class AuthenticatedUserResult(
    val user: AuthenticatedUser?,
)

interface GetAuthenticatedUserCommand : ServiceCommand<GetAuthenticatedUserArgs, AuthenticatedUserResult, AuthenticationError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.get-authenticated-user"
    }
}

// ============================================================================
// GetUserInfoCommand
// ============================================================================

data class GetUserInfoArgs(
    val userId: String,
)

interface GetUserInfoCommand : ServiceCommand<GetUserInfoArgs, UserInfo, AuthenticationError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.get-user-info"
    }
}

// ============================================================================
// ListEnabledFederationProvidersCommand
// ============================================================================

/**
 * No-arg request marker for [ListEnabledFederationProvidersCommand]. A typed object preserves
 * binary-transport serialization shape; reusing a shared `Unit`/empty-args type would force
 * every reader to discriminate on command id alone.
 */
object ListEnabledFederationProvidersArgs

/**
 * Carries the list of enabled federation provider configs back to the caller. Wrapping the
 * list in a typed result keeps the gRPC payload self-describing.
 */
data class EnabledFederationProviders(
    val providers: List<FederationProviderConfig>,
)

/**
 * Returns the federation providers currently marked enabled for the active tenant. Used by the
 * login UI to render exact canonical federation-binding choices.
 */
interface ListEnabledFederationProvidersCommand : ServiceCommand<ListEnabledFederationProvidersArgs, EnabledFederationProviders, AuthenticationError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.federated-auth.list-enabled-federation-providers"
    }
}
