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

package com.sphereon.openid.oid4vci.holder.dsl

import com.sphereon.openid.oid4vci.common.dsl.CreateCredentialRequestProofArgsBuilder
import com.sphereon.openid.oid4vci.common.dsl.PollDeferredCredentialArgsBuilder
import com.sphereon.openid.oid4vci.common.dsl.RequestCredentialArgsBuilder
import com.sphereon.openid.oid4vci.common.dsl.RequestCredentialWithFlowArgsBuilder
import com.sphereon.openid.oid4vci.common.dsl.SendNotificationWithRetryArgsBuilder
import com.sphereon.openid.oid4vci.holder.CreateCredentialRequestProofArgs
import com.sphereon.openid.oid4vci.holder.PollDeferredCredentialArgs
import com.sphereon.openid.oid4vci.holder.RequestCredentialArgs
import com.sphereon.openid.oid4vci.holder.RequestCredentialWithFlowArgs
import com.sphereon.openid.oid4vci.holder.SendNotificationWithRetryArgs

// ============================================================================
// Holder command DSL entry points
// ============================================================================
// These top-level functions are the public API. The builder state classes live
// in common-public (Oid4vciCommandDsl.kt) so they are reusable without pulling
// in holder/issuer-specific args types. The actual args data classes are
// constructed here where both common-public and holder-public are on the
// compile classpath.

/**
 * Build [RequestCredentialArgs] using the DSL.
 *
 * Example:
 * ```kotlin
 * val args = requestCredentialArgs {
 *     endpoint("https://issuer.example.com/credential", accessToken = "Bearer …")
 *     credentialConfigurationId("UniversityDegree")
 *     proof {
 *         proofType = "jwt"
 *         jwt = "eyJ…"
 *     }
 *     nonceEndpoint("https://issuer.example.com/nonce")
 * }
 * ```
 */
fun requestCredentialArgs(builder: RequestCredentialArgsBuilder.() -> Unit): RequestCredentialArgs {
    val state = RequestCredentialArgsBuilder().apply(builder).buildState()
    return RequestCredentialArgs(
        credentialEndpoint = state.credentialEndpoint,
        accessToken = state.accessToken,
        credentialConfigurationId = state.credentialConfigurationId,
        credentialIdentifier = state.credentialIdentifier,
        proofs = state.proofs,
        credentialResponseEncryption = state.credentialResponseEncryption,
        nonceEndpoint = state.nonceEndpoint,
    )
}

/**
 * Build [CreateCredentialRequestProofArgs] using the DSL.
 *
 * Example:
 * ```kotlin
 * val args = createProofArgs {
 *     issuerUrl("https://issuer.example.com")
 *     signingKey("key-1", algorithm = JwaAlgorithm.ES256)
 *     nonce("c_nonce_value")
 *     clientId("wallet-app")
 *     batch(count = 3)
 *     keyMode(JwsIdentifierMode.JWK)
 * }
 * ```
 */
fun createProofArgs(builder: CreateCredentialRequestProofArgsBuilder.() -> Unit): CreateCredentialRequestProofArgs {
    val state = CreateCredentialRequestProofArgsBuilder().apply(builder).buildState()
    return CreateCredentialRequestProofArgs(
        issuerUrl = state.issuerUrl,
        cNonce = state.cNonce,
        signingKeyId = state.signingKeyId,
        signingAlgorithm = state.signingAlgorithm,
        clientId = state.clientId,
        count = state.count,
        keyInclusionMode = state.keyInclusionMode,
    )
}

/**
 * Build [RequestCredentialWithFlowArgs] using the DSL.
 *
 * Example:
 * ```kotlin
 * val args = credentialFlowArgs {
 *     sessionId("sess-1")
 *     endpoint("https://issuer.example.com/credential", accessToken = "Bearer …")
 *     issuerUrl("https://issuer.example.com")
 *     signingKey("key-1", algorithm = JwaAlgorithm.ES256)
 *     credentialConfigurationId("UniversityDegree")
 *     nonceEndpoint("https://issuer.example.com/nonce")
 *     deferredEndpoint("https://issuer.example.com/deferred")
 *     notificationEndpoint("https://issuer.example.com/notification")
 * }
 * ```
 */
fun credentialFlowArgs(builder: RequestCredentialWithFlowArgsBuilder.() -> Unit): RequestCredentialWithFlowArgs {
    val state = RequestCredentialWithFlowArgsBuilder().apply(builder).buildState()
    return RequestCredentialWithFlowArgs(
        sessionId = state.sessionId,
        credentialEndpoint = state.credentialEndpoint,
        accessToken = state.accessToken,
        issuerUrl = state.issuerUrl,
        signingKeyId = state.signingKeyId,
        signingAlgorithm = state.signingAlgorithm,
        credentialConfigurationId = state.credentialConfigurationId,
        credentialIdentifier = state.credentialIdentifier,
        nonceEndpoint = state.nonceEndpoint,
        deferredCredentialEndpoint = state.deferredCredentialEndpoint,
        notificationEndpoint = state.notificationEndpoint,
        credentialResponseEncryption = state.credentialResponseEncryption,
    )
}

/**
 * Build [PollDeferredCredentialArgs] using the DSL.
 *
 * Example:
 * ```kotlin
 * val args = pollDeferredArgs {
 *     endpoint("https://issuer.example.com/deferred", accessToken = "Bearer …")
 *     transactionId("tx-abc")
 *     sessionId("sess-1")
 *     polling(interval = 10, maxAttempts = 60)
 * }
 * ```
 */
fun pollDeferredArgs(builder: PollDeferredCredentialArgsBuilder.() -> Unit): PollDeferredCredentialArgs {
    val state = PollDeferredCredentialArgsBuilder().apply(builder).buildState()
    return PollDeferredCredentialArgs(
        deferredCredentialEndpoint = state.deferredCredentialEndpoint,
        accessToken = state.accessToken,
        transactionId = state.transactionId,
        sessionId = state.sessionId,
        interval = state.interval,
        maxAttempts = state.maxAttempts,
        credentialResponseEncryption = state.credentialResponseEncryption,
    )
}

/**
 * Build [SendNotificationWithRetryArgs] using the DSL.
 *
 * Example:
 * ```kotlin
 * val args = notifyWithRetryArgs {
 *     endpoint("https://issuer.example.com/notification", accessToken = "Bearer …")
 *     notificationId("notif-id-from-credential-response")
 *     event(CredentialNotificationEvent.CREDENTIAL_ACCEPTED)
 *     retryPolicy(maxRetries = 5, initialBackoffMs = 500)
 * }
 * ```
 */
fun notifyWithRetryArgs(builder: SendNotificationWithRetryArgsBuilder.() -> Unit): SendNotificationWithRetryArgs {
    val state = SendNotificationWithRetryArgsBuilder().apply(builder).buildState()
    return SendNotificationWithRetryArgs(
        notificationEndpoint = state.notificationEndpoint,
        accessToken = state.accessToken,
        notificationId = state.notificationId,
        event = state.event,
        eventDescription = state.eventDescription,
        maxRetries = state.maxRetries,
        initialBackoffMs = state.initialBackoffMs,
    )
}
