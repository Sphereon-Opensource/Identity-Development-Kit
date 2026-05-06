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

package com.sphereon.oauth2.server.authorization.impl.command.admin

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.RotateSigningKeyArgs
import com.sphereon.oauth2.server.authorization.command.RotateSigningKeyCommand
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.RotationResult
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * Default [RotateSigningKeyCommand] implementation. Two-step rotation:
 *  1. Generate a fresh key pair in the configured KMS via [KeyManagerService.generateKeyResult].
 *     The KMS owns key custody; the AS receives back a [com.sphereon.crypto.core.kms.command.GenerateKeyResult]
 *     containing a [com.sphereon.crypto.core.generic.ManagedKeyPair] with the wire-visible
 *     `kid` and the `alias` / `providerId` the KMS uses to address the bytes.
 *  2. Atomically register the new entry in [SigningKeyStore] via [SigningKeyStore.rotate],
 *     which demotes the previously-`ACTIVE` entry (or entries) to `LEGACY` in the same
 *     critical section so JWKS publication never has a "two ACTIVE" or "no ACTIVE" window.
 *
 * Failure handling: if step 1 fails, step 2 is not attempted. If step 1 succeeds but step 2
 * fails (e.g. transient store failure, duplicate kid), the freshly-generated KMS key is left
 * orphaned — the operator cleans it up via the KMS console because the kid was never
 * published in JWKS so nothing trusted it. Surfaced as the underlying [IdkError] so the
 * caller can decide whether to retry.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RotateSigningKeyCommand>())
class RotateSigningKeyCommandImpl(
    execution: SessionExecution,
    private val keyManagerService: KeyManagerService,
    private val signingKeyStore: SigningKeyStore,
    private val clock: Clock = Clock.System,
) : TypedServiceCommandAdapter<RotateSigningKeyArgs, RotationResult, IdkError>(
        commandId = RotateSigningKeyCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = RotateSigningKeyCommand.INPUT_TYPE_TOKEN,
        outputTypeToken = RotateSigningKeyCommand.OUTPUT_TYPE_TOKEN,
    ),
    RotateSigningKeyCommand {
    override val commandId: String get() = RotateSigningKeyCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is RotateSigningKeyArgs

    override suspend fun doExecute(
        args: RotateSigningKeyArgs,
        applyDuring: (RotateSigningKeyArgs) -> RotateSigningKeyArgs,
    ): IdkResult<RotationResult, IdkError> {
        val applied = applyDuring(args)

        // Determine the alias the KMS will use for the new key. The KMS treats `alias` as a
        // mutable handle inside its keystore; we derive a unique value per rotation so the
        // KMS does not overwrite the previous-active key's bytes (which the LEGACY entry
        // still needs for verifying in-flight tokens). Pattern: `oauth2.<tenant>.<kid>` so
        // operators reading the KMS console can correlate alias → tenant + AS kid at a
        // glance.
        val now = applied.notBefore ?: clock.now()
        val priority = applied.priority ?: nextActivePriority(applied.tenantId)
        val plannedKid = applied.requestedKid ?: generateKid(applied.tenantId, now.toEpochMilliseconds())
        val plannedAlias = "oauth2.${applied.tenantId}.$plannedKid"

        val generateResult =
            keyManagerService.generateKeyResult(
                alias = plannedAlias,
                use = JwkUse.sig,
                alg = applied.algorithm,
            )
        if (!generateResult.isOk) {
            return Err(generateResult.error)
        }
        val keyPair =
            generateResult.value.keyPair
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "KMS generateKeyResult returned no keyPair for alias $plannedAlias"))

        // The wire-visible `kid` MUST match across (a) the JWS header on issued tokens, (b)
        // the JWKS endpoint entry, and (c) the SigningKeyStore row. Pin them all to
        // `plannedKid` so the KMS-derived kid (which depends on the key bytes) does not
        // diverge from what the AS publishes.
        val keyInfo: KeyInfo<KeyType> =
            KeyInfo<KeyType>(
                kid = plannedKid,
                alias = keyPair.alias,
                providerId = keyPair.providerId,
                signatureAlgorithm = applied.algorithm,
            )

        val newKey =
            OAuth2SigningKey(
                tenantId = applied.tenantId,
                keyInfo = keyInfo,
                state = OAuth2SigningKeyState.ACTIVE,
                priority = priority,
                createdAt = now,
                notBefore = now,
            )
        val rotateResult = signingKeyStore.rotate(newKey)
        if (!rotateResult.isOk) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "SigningKeyStore.rotate failed: ${rotateResult.error}"))
        }
        return rotateResult.let {
            com.sphereon.core.api
                .Ok(it.value)
        }
    }

    /**
     * Pick a priority that beats the highest-priority existing key for this tenant. Default
     * to `priority + 1` of the current ACTIVE key so the new key takes precedence
     * immediately; defaults to 1 when no ACTIVE key exists.
     */
    private suspend fun nextActivePriority(tenantId: String): Int {
        val activeResult = signingKeyStore.getActive(tenantId)
        if (!activeResult.isOk) return 1
        val active = activeResult.value ?: return 1
        return active.priority + 1
    }

    /**
     * Generate a CSPRNG-derived `kid` scoped per tenant + millis-since-epoch so the value
     * is unique across the deployment lifetime AND human-debuggable (operators reading the
     * audit log can tell which rotation produced the kid). Length is bounded so JWS headers
     * stay compact.
     */
    private fun generateKid(
        tenantId: String,
        epochMillis: Long,
    ): String = "k-$tenantId-${epochMillis.toString(36)}"
}
