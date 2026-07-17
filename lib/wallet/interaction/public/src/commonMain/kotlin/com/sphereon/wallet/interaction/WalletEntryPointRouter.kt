/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.interaction

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Thin intake layer for arbitrary external entry-point URIs, e.g. an
 * `openid-credential-offer:`, `openid4vp:` or `mdoc-openid4vp:` link, or a bare
 * `request_uri` reference handed to the wallet by an issuer or verifier the wallet
 * has never seen before (such as the OIDF conformance suite).
 *
 * [WalletEntryPointRouter] performs no protocol classification itself. It only:
 * 1. Validates that [route]'s `uri` argument is at least URI-shaped (non-blank, has
 *    a scheme), failing fast otherwise.
 * 2. Wraps the validated string as a [WalletEntryPoint] via [WalletEntryPoint.link].
 * 3. Delegates to [WalletInteractionClient.start], which resolves the entry point
 *    against every registered [WalletInteractionProtocolAdapter]'s `canHandle` and
 *    starts the strongest match.
 *
 * Fetching a by-reference `request_uri` / `credential_offer_uri` payload, and the
 * actual scheme/content classification, both remain the responsibility of the
 * selected adapter, not this router. When no adapter can handle the entry point,
 * or the supplied `uri` is not parseable, [route] returns an [IdkError] instead of
 * forwarding malformed input further into the engine.
 */
interface WalletEntryPointRouter {
    /**
     * Validate, classify (via delegation to the engine's registered adapters) and
     * start a wallet interaction session for an arbitrary external entry-point [uri].
     *
     * @param uri the raw external URI, e.g. `openid-credential-offer://issuer.example.com?...`
     * @param walletUnitId the wallet unit that should own the resulting session
     * @param executionMode where protocol execution should run; defaults to [WalletInteractionExecutionMode.LOCAL]
     * @return [com.sphereon.core.api.Ok] with the started session id, or
     *   [com.sphereon.core.api.Err] with a clean [IdkError] when [uri] is not a
     *   parseable URI or no registered adapter can handle it
     */
    suspend fun route(
        uri: String,
        walletUnitId: String,
        executionMode: WalletInteractionExecutionMode = WalletInteractionExecutionMode.LOCAL,
    ): IdkResult<WalletInteractionSessionId, IdkError>
}
