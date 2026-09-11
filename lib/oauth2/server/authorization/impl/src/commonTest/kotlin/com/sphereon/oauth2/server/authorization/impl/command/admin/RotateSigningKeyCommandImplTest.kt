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
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.command.GenerateKeyResult
import com.sphereon.oauth2.server.authorization.command.RotateSigningKeyArgs
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemorySigningKeyStore
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Pins the [RotateSigningKeyCommandImpl] contract that the admin rotation flow depends on:
 * the command must (1) successfully drive the KMS-then-store two-step on the happy path,
 * (2) bail without touching the store if the KMS fails, and (3) propagate the store's
 * DuplicateKid error when a rotation collides with an already-registered kid (operator error
 * — they pre-staged a kid that was used).
 *
 * The two-step ordering matters: KMS first, store second. A KMS failure means no store row
 * exists; a store failure leaves an orphaned KMS key the operator must clean up via the KMS
 * console (audited in the command's KDoc). The "no store row on KMS fail" property is the
 * critical safety property under test below — without it, a store row could point at a kid
 * the KMS never minted, breaking subsequent sign attempts.
 */
class RotateSigningKeyCommandImplTest {
    private val ctx = OAuth2ServerTestContext("rotate-signing-key-test", this)
    private val tenant = "default"

    @Test
    fun rotateOnEmptyStoreInsertsActiveKeyWithNoDemotions() =
        runTest {
            // First-rotation case (also: bootstrap path). No previously-active key, so
            // demotedToLegacy is empty. The store now holds exactly the new ACTIVE key.
            val store: SigningKeyStore = InMemorySigningKeyStore()
            val command = RotateSigningKeyCommandImpl(ctx.execution, ctx.keyManagerService, store)

            val result =
                command.execute(
                    RotateSigningKeyArgs(
                        tenantId = tenant,
                        algorithm = SignatureAlgorithm.RSA_SHA256,
                        requestedKid = "kid-bootstrap",
                    ),
                )

            assertTrue(result.isOk, "first-rotation must succeed: ${if (result.isErr) result.error else ""}")
            val rotation = result.value
            assertEquals("kid-bootstrap", rotation.newActive.kid)
            assertEquals(OAuth2SigningKeyState.ACTIVE, rotation.newActive.state)
            assertEquals(emptyList(), rotation.demotedToLegacy, "no previous ACTIVE means nothing to demote")

            val storedActive = store.getActive(tenant).value
            assertNotNull(storedActive, "store must hold the freshly-rotated ACTIVE key")
            assertEquals("kid-bootstrap", storedActive.kid)
            assertEquals(SignatureAlgorithm.RSA_SHA256, storedActive.algorithm)
        }

    @Test
    fun rotateAtopExistingActiveDemotesPreviousAndPromotesNew() =
        runTest {
            // Standard rotation: pre-existing ACTIVE key, rotate to a fresh ACTIVE. The
            // previous-ACTIVE must be demoted to LEGACY in the same atomic store.rotate call
            // so JWKS publication never sees a "two ACTIVE" or "no ACTIVE" window.
            val store: SigningKeyStore = InMemorySigningKeyStore()
            preRegisterActiveKey(store, kid = "kid-old", priority = 1)

            val command = RotateSigningKeyCommandImpl(ctx.execution, ctx.keyManagerService, store)
            val result =
                command.execute(
                    RotateSigningKeyArgs(
                        tenantId = tenant,
                        algorithm = SignatureAlgorithm.RSA_SHA256,
                        requestedKid = "kid-new",
                    ),
                )

            assertTrue(result.isOk, "rotation must succeed when a previous ACTIVE exists")
            val rotation = result.value
            assertEquals("kid-new", rotation.newActive.kid)
            assertEquals(OAuth2SigningKeyState.ACTIVE, rotation.newActive.state)
            assertEquals(listOf("kid-old"), rotation.demotedToLegacy.map { it.kid })
            assertEquals(OAuth2SigningKeyState.LEGACY, rotation.demotedToLegacy.single().state)

            // Subsequent reads see the new ACTIVE; the demoted LEGACY still publishes.
            assertEquals("kid-new", store.getActive(tenant).value?.kid)
            val publishable =
                store
                    .listPublishable(tenant)
                    .value
                    .map { it.kid }
                    .toSet()
            assertEquals(setOf("kid-new", "kid-old"), publishable)
        }

    @Test
    fun rotateBumpsPriorityAboveExistingActiveByDefault() =
        runTest {
            // The command's default-priority logic computes `currentActive.priority + 1` so
            // the new key takes precedence immediately. Pin this behaviour: an operator who
            // omits the priority arg must NOT silently ship a key that loses to the previous
            // ACTIVE (which would defeat the rotation).
            val store: SigningKeyStore = InMemorySigningKeyStore()
            preRegisterActiveKey(store, kid = "kid-old", priority = 7)

            val command = RotateSigningKeyCommandImpl(ctx.execution, ctx.keyManagerService, store)
            val result =
                command.execute(
                    RotateSigningKeyArgs(
                        tenantId = tenant,
                        algorithm = SignatureAlgorithm.RSA_SHA256,
                        requestedKid = "kid-new",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(8, result.value.newActive.priority, "default priority must be currentActive.priority + 1")
        }

    @Test
    fun rotateRejectsDuplicateRequestedKid() =
        runTest {
            // Operator passes a `requestedKid` that already exists in the tenant (e.g. they
            // pre-staged the same kid twice). The store rejects with DuplicateKid; the
            // command must propagate the failure rather than silently overwriting.
            val store: SigningKeyStore = InMemorySigningKeyStore()
            preRegisterActiveKey(store, kid = "kid-shared", priority = 5)

            val command = RotateSigningKeyCommandImpl(ctx.execution, ctx.keyManagerService, store)
            val result =
                command.execute(
                    RotateSigningKeyArgs(
                        tenantId = tenant,
                        algorithm = SignatureAlgorithm.RSA_SHA256,
                        requestedKid = "kid-shared",
                    ),
                )

            assertTrue(result.isErr, "rotation must fail on duplicate kid")
            // Critical post-condition: the original ACTIVE is untouched. A naive impl that
            // demoted-then-failed-to-insert would leave the tenant with no ACTIVE key.
            val stillActive = store.getActive(tenant).value
            assertNotNull(stillActive, "store must NOT lose its ACTIVE key on a failed rotate")
            assertEquals("kid-shared", stillActive.kid)
            assertEquals(OAuth2SigningKeyState.ACTIVE, stillActive.state)
        }

    @Test
    fun rotateBailsWithoutTouchingStoreWhenKmsFails() =
        runTest {
            // KMS-failure path. The command must NOT attempt to register a store row that
            // points at a kid the KMS never minted, otherwise subsequent sign attempts
            // would resolve a phantom alias and fail at runtime.
            val store: SigningKeyStore = InMemorySigningKeyStore()
            preRegisterActiveKey(store, kid = "kid-existing", priority = 5)

            val failingKms = FailingGenerateKeyManagerService(ctx.keyManagerService)
            val command = RotateSigningKeyCommandImpl(ctx.execution, failingKms, store)

            val result =
                command.execute(
                    RotateSigningKeyArgs(
                        tenantId = tenant,
                        algorithm = SignatureAlgorithm.RSA_SHA256,
                        requestedKid = "kid-new-from-failed-kms",
                    ),
                )

            assertTrue(result.isErr, "rotation must fail when the KMS rejects key generation")
            // Store invariant: the previous ACTIVE remains the one ACTIVE key, and no row
            // was added for the kid the KMS never minted.
            val all =
                store
                    .listAll(tenant)
                    .value
                    .map { it.kid }
                    .toSet()
            assertEquals(
                setOf("kid-existing"),
                all,
                "no store row must exist for a kid the KMS never minted",
            )
            assertEquals("kid-existing", store.getActive(tenant).value?.kid)
        }

    /**
     * Generate a real key in the test KMS and register it as ACTIVE in the store. The
     * test cases need a starting state where an ACTIVE key already exists so the rotation
     * has something to demote.
     */
    private suspend fun preRegisterActiveKey(
        store: SigningKeyStore,
        kid: String,
        priority: Int,
    ): OAuth2SigningKey {
        val alias = "oauth2.$tenant.$kid"
        val genResult =
            ctx.keyManagerService.generateKeyResult(
                alias = alias,
                use = JwkUse.sig,
                alg = SignatureAlgorithm.RSA_SHA256,
            )
        assertTrue(
            genResult.isOk,
            "KMS pre-registration must succeed: ${if (genResult.isErr) genResult.error.message.defaultMessage else ""}",
        )
        val keyPair = genResult.value.keyPair ?: error("KMS returned no keyPair for $alias")
        val now = Clock.System.now()
        val key =
            OAuth2SigningKey(
                tenantId = tenant,
                keyInfo =
                    KeyInfo<KeyType>(
                        kid = kid,
                        alias = keyPair.alias,
                        providerId = keyPair.providerId,
                        signatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
                    ),
                state = OAuth2SigningKeyState.ACTIVE,
                priority = priority,
                createdAt = now,
                notBefore = now,
            )
        val registerResult = store.register(key)
        assertTrue(
            registerResult.isOk,
            "store.register must succeed for pre-condition: ${if (registerResult.isErr) registerResult.error else ""}",
        )
        return key
    }
}

/**
 * Decorator [KeyManagerService] that delegates everything to the wrapped instance EXCEPT
 * `generateKeyResult`, which is forced to return Err. Used to simulate a KMS outage so we
 * can prove [RotateSigningKeyCommandImpl] does not write to the store when the KMS step
 * fails.
 *
 * Defined as a class with `by delegate` so we only override the one method under test;
 * if the [KeyManagerService] interface gains methods later, the delegation continues to
 * forward them rather than going out-of-date.
 */
private class FailingGenerateKeyManagerService(
    private val delegate: KeyManagerService,
) : KeyManagerService by delegate {
    override suspend fun generateKeyResult(
        providerId: String?,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<out KeyOperations>?,
        alg: SignatureAlgorithm?,
        keyVisibility: KeyVisibility?,
        walletUnitId: String?,
    ): IdkResult<GenerateKeyResult, IdkError> = Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "simulated KMS outage for test"))
}
