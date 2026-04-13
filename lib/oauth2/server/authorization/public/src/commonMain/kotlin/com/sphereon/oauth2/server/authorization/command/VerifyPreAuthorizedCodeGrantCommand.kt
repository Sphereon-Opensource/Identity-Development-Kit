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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.service.ServiceCommand

// ============================================================================
// VerifyPreAuthorizedCodeGrantCommand
// ============================================================================

/**
 * Arguments for verifying a pre-authorized code grant (OID4VCI)
 */
data class VerifyPreAuthCodeArgs(
    val preAuthorizedCode: String,
    val txCode: String?,
    val clientId: String,
)

/**
 * Verified pre-authorized code grant result
 */
data class VerifiedPreAuthCodeGrant(
    val sessionId: String,
    val subject: String?,
    val credentialConfigurationIds: List<String>,
    val credentialIdentifiers: List<String>? = null,
    val issuerIdentifier: String? = null,
    val useCredentialIdentifiers: Boolean = true,
)

/**
 * Verify pre-authorized code grant command
 *
 * OID4VCI 1.0 Section 4.1.1: Pre-Authorized Code Grant
 *
 * Verifies a pre-authorized code grant:
 * - Atomically consumes the pre-authorized code
 * - Validates tx_code if required
 * - Checks code expiry
 * - Returns associated session and credential configuration data
 */
interface VerifyPreAuthorizedCodeGrantCommand : ServiceCommand<VerifyPreAuthCodeArgs, VerifiedPreAuthCodeGrant> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.preauth.verify"
    }
}
