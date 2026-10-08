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

package com.sphereon.wallet.interaction.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.interaction.WalletEntryPoint
import com.sphereon.wallet.interaction.WalletEntryPointRouter
import com.sphereon.wallet.interaction.WalletInteractionClient
import com.sphereon.wallet.interaction.ProtocolExecutionOwner
import com.sphereon.wallet.interaction.WalletInteractionInput
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletInteractionStatus
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [WalletEntryPointRouter]. Deliberately thin: it validates the raw [route]
 * input is URI-shaped, builds a [WalletEntryPoint] via [WalletEntryPoint.link] and
 * delegates entirely to [client] (normally backed by [DefaultWalletInteractionEngine])
 * for adapter classification and session start. It does not re-implement any
 * `canHandle` logic, hold a scheme allow-list, or perform by-reference fetching.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletEntryPointRouter>())
class DefaultWalletEntryPointRouter(
    private val client: WalletInteractionClient,
) : WalletEntryPointRouter {
    override suspend fun route(
        uri: String,
        walletUnitId: String,
        executionOwner: ProtocolExecutionOwner,
    ): IdkResult<WalletInteractionSessionId, IdkError> {
        val trimmed = uri.trim()
        if (!isUriShaped(trimmed)) {
            return Err(invalidEntryPointUriError(uri))
        }

        val entryPoint = WalletEntryPoint.link(trimmed)
        val session =
            client.start(
                WalletInteractionInput(
                    walletUnitId = walletUnitId,
                    entryPoint = entryPoint,
                    executionOwner = executionOwner,
                ),
            )

        if (session.state.status == WalletInteractionStatus.UnsupportedEntryPoint) {
            return Err(unsupportedEntryPointError(session.state.message?.textKey))
        }

        return Ok(session.sessionId)
    }

    private fun isUriShaped(value: String): Boolean = value.isNotBlank() && uriSchemePattern.containsMatchIn(value)

    private fun invalidEntryPointUriError(rawUri: String): IdkError =
        IdkError(
            code = "WALLET_INTERACTION_INVALID_ENTRY_POINT_URI",
            message =
                IdkError.Message(
                    i18nKey = "wallet.interaction.error.invalid_entry_point_uri",
                    defaultMessage = "Entry point is not a parseable URI",
                ),
            category = ErrorCategory.VALIDATION,
            meta = mapOf("uri" to rawUri),
        )

    private fun unsupportedEntryPointError(engineReasonKey: String?): IdkError =
        IdkError(
            code = "WALLET_INTERACTION_UNSUPPORTED_ENTRY_POINT",
            message =
                IdkError.Message(
                    i18nKey = "wallet.interaction.error.unsupported_entry_point",
                    i18nParams = engineReasonKey?.let { mapOf("reasonKey" to it) } ?: emptyMap(),
                    defaultMessage = "No registered adapter can handle this entry point",
                ),
            category = ErrorCategory.PROTOCOL,
            meta = engineReasonKey?.let { mapOf("engineReasonKey" to it) } ?: emptyMap(),
        )

    companion object {
        // RFC 3986 scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) ":"
        private val uriSchemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    }
}
