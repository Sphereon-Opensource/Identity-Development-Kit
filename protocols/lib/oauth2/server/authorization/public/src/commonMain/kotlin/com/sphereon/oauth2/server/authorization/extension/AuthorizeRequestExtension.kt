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

package com.sphereon.oauth2.server.authorization.extension

import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.model.ClientRegistration

/**
 * Narrow extension hook the AS calls during `/authorize` request verification, after the
 * standard parameter-shape checks but before returning the verified request. Each
 * registered extension gets a chance to refuse the request; the first refusal
 * short-circuits with the supplied error code + description.
 *
 * Multibound — the AS injects `Set<AuthorizeRequestExtension>` and runs every member in
 * iteration order. An empty set is the IDK default and means no extensions intervene
 * (the per-flag legacy checks on [ClientRegistration] and [com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig]
 * remain the only enforcement).
 *
 * **Why this hook lives in IDK:** the wiring point ([com.sphereon.oauth2.server.authorization.command.VerifyAuthorizationRequestCommand]
 * is in IDK, IDK cannot depend on EDK, so the contract this hook satisfies has to be
 * IDK-resident. The actual policy / profile-bundle / FAPI machinery is intentionally
 * EDK-resident — it ships richer concepts (named bundles, registry-driven configuration,
 * tenant overlays) that pure-IDK deployments don't need. EDK module
 * `lib-oauth2-server-authorization-policy` ships the framework + a bridge that
 * implements this hook.
 *
 * Implementations are stateless and thread-safe; AppScope by convention.
 */
interface AuthorizeRequestExtension {
    /**
     * Decide whether [request] from [client] may proceed. [Allow] lets the next extension
     * (or the request itself) continue; [Deny] short-circuits with the supplied OAuth2
     * error code + description.
     */
    suspend fun evaluate(
        client: ClientRegistration,
        request: AuthorizationRequestData,
    ): AuthorizeExtensionResult
}

/**
 * Outcome of a single [AuthorizeRequestExtension.evaluate] call. The error-code default
 * `invalid_request` matches the most common policy denial shape (RFC 6749 §4.1.2.1);
 * extensions can pick a more specific code when the semantics call for it
 * (`unauthorized_client`, `access_denied`, ...). The AS's verifier maps these onto the
 * matching [com.sphereon.oauth2.server.authorization.error.AuthorizationServerError]
 * variant.
 */
sealed class AuthorizeExtensionResult {
    data object Allow : AuthorizeExtensionResult()

    data class Deny(
        val errorCode: String = "invalid_request",
        val description: String,
    ) : AuthorizeExtensionResult()
}
