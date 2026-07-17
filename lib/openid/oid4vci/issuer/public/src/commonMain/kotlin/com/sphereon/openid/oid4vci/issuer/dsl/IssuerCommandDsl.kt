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

package com.sphereon.openid.oid4vci.issuer.dsl

import com.sphereon.openid.oid4vci.common.dsl.CreateCredentialOfferArgsBuilder
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs

// ============================================================================
// Issuer command DSL entry points
// ============================================================================
// These top-level functions are the public API. The builder state classes live
// in common-public (Oid4vciCommandDsl.kt) so they are reusable without pulling
// in issuer-specific args types. The actual args data class is constructed
// here where both common-public and issuer-public are on the compile classpath.

/**
 * Build [CreateCredentialOfferArgs] using the DSL.
 *
 * Example:
 * ```kotlin
 * val args = createOfferArgs {
 *     issuerId("https://issuer.example.com")
 *     credentials("UniversityDegree", "MembershipCard")
 *     preAuthorizedCodeGrant(txCodeRequired = true)
 *     offerTtlSeconds = 300
 *     attributes {
 *         put("given_name", JsonPrimitive("Jane"))
 *     }
 * }
 * ```
 *
 * At least one grant type must be enabled via [CreateCredentialOfferArgsBuilder.preAuthorizedCodeGrant]
 * or [CreateCredentialOfferArgsBuilder.authorizationCodeGrant].
 */
fun createOfferArgs(
    instanceId: String,
    builder: CreateCredentialOfferArgsBuilder.() -> Unit,
): CreateCredentialOfferArgs {
    val state = CreateCredentialOfferArgsBuilder().apply(builder).buildState()
    return CreateCredentialOfferArgs(
        instanceId = instanceId,
        issuerId = state.issuerId,
        credentialConfigurationIds = state.credentialConfigurationIds,
        preAuthorizedCodeGrant = state.preAuthorizedCodeGrant,
        authorizationCodeGrant = state.authorizationCodeGrant,
        txCodeRequired = state.txCodeRequired,
        txCodeLength = state.txCodeLength,
        txCodeInputMode = state.txCodeInputMode,
        preSeededAttributes = state.preSeededAttributes,
        offerTtlSeconds = state.offerTtlSeconds,
    )
}
