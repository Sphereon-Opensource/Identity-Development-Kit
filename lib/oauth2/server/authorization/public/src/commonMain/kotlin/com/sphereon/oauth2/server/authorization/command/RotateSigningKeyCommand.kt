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

import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.oauth2.server.authorization.storage.RotationResult

/**
 * Admin-gated rotation entry point. Generates a fresh signing key in the configured KMS,
 * registers it as the new `ACTIVE` entry for the given tenant, and atomically demotes any
 * previously-`ACTIVE` keys to `LEGACY` so JWKS continues to publish them for in-flight token
 * verification.
 *
 * Authorization: this command MUST be gated behind an admin authorization check (the
 * downstream `EdkUserAuthenticationProvider` enforces it via the standard admin-role
 * assertion; service-to-service callers MUST present a credential that resolves to the
 * admin role). The command itself does not duplicate the gate — it trusts the calling
 * context.
 *
 * Atomicity: the underlying [com.sphereon.oauth2.server.authorization.storage.SigningKeyStore.rotate]
 * call is atomic; this command is a thin wrapper that pairs it with the actual KMS key
 * generation step. If the KMS step fails the rotation is not attempted; if the rotation
 * step fails the freshly-generated KMS key is left orphaned (operators clean up via the
 * KMS console — the kid was never published in JWKS so nothing trusted it).
 */
interface RotateSigningKeyCommand : ServiceCommand<RotateSigningKeyArgs, RotationResult, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID: String = "oauth2.signing-key.rotate"
        val INPUT_TYPE_TOKEN = typeToken<RotateSigningKeyArgs>()
        val OUTPUT_TYPE_TOKEN = typeToken<RotationResult>()
    }
}

/**
 * Arguments for [RotateSigningKeyCommand]. The KMS handles the actual key generation;
 * everything here is metadata the AS uses to register the new entry in the
 * [com.sphereon.oauth2.server.authorization.storage.SigningKeyStore].
 *
 * @property tenantId Tenant whose signer is being rotated. Single-tenant deployments pass
 *   the fixed default (typically `"default"`); multi-tenant deployments scope per realm.
 * @property algorithm JWS algorithm the new key will sign with. Operators driving rotation
 *   typically keep this stable (RS256 → RS256) but the command supports algorithm migration
 *   (RS256 → ES256) as a side-effect of rotation, since the new key carries its own algorithm
 *   independent of the previous active key.
 * @property requestedKid Optional caller-supplied `kid` for the new key. When null, the
 *   command generates a fresh CSPRNG kid; supplying one lets a deployment pre-stage the
 *   wire-visible kid (e.g. for an external compliance review of the planned rotation). MUST
 *   be unique within the tenant.
 * @property notBefore Earliest instant the new key may sign new tokens. Defaults to now;
 *   pre-staging a future-dated rotation lets operators schedule a cutover.
 * @property priority Sort order among same-tenant `ACTIVE` keys (highest wins). Defaults to
 *   one above the current ACTIVE so the new key takes precedence immediately. Operators
 *   doing a controlled rollout may pass a lower priority and bump it later.
 */
data class RotateSigningKeyArgs(
    val tenantId: String,
    val algorithm: SignatureAlgorithm,
    val requestedKid: String? = null,
    val notBefore: kotlin.time.Instant? = null,
    val priority: Int? = null,
)
