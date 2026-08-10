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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vp.verifier.spi.VerifierResponseEncryptionKeyNameResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Contract for the key a verifier decrypts an encrypted authorization response with.
 *
 * 1. Without a bound seam there is nothing to fall back to: `direct_post.jwt` is refused. There is
 *    deliberately no configured alias, no configured provider, and no instance-derived name, so a
 *    deployment cannot end up decrypting under a key a tenant chose or a key created on first use.
 * 2. Absent, detached, cross-tenant, inactive, and unmapped bindings all refuse the same way, so the
 *    outcome is not a discovery oracle over which verifiers hold which key material.
 * 3. Resolution never touches a KMS, which is what makes "refuses" and "creates nothing" the same
 *    statement.
 */
class ResponseEncryptionKeySeamTest {
    @Test
    fun refusesWhenNoSeamIsBound() =
        runTest {
            val config = SessionResponseEncryptionKeyConfig(execution = TenantScopedExecution(TENANT_ID))

            assertNull(config.resolveEncryptionKeyName(INSTANCE_ID))
        }

    @Test
    fun resolvesTheServerDerivedNameWhileTheSeamIsBound() =
        runTest {
            val config =
                SessionResponseEncryptionKeyConfig(
                    execution = TenantScopedExecution(TENANT_ID),
                    keyNameResolver = { FixedResponseEncryptionKeyNameResolver(SERVER_KEY_NAME) },
                )

            assertEquals(SERVER_KEY_NAME, config.resolveEncryptionKeyName(INSTANCE_ID))
        }

    @Test
    fun everyUnusableBindingShapeRefusesIdentically() =
        runTest {
            val outcomes =
                BindingShape.entries.map { shape ->
                    SessionResponseEncryptionKeyConfig(
                        execution = TenantScopedExecution(TENANT_ID),
                        keyNameResolver = { ShapedResponseEncryptionKeyNameResolver(shape) },
                    ).resolveEncryptionKeyName(INSTANCE_ID)
                }

            assertEquals(setOf(null), outcomes.toSet(), "unusable bindings must refuse identically, got $outcomes")
        }

    @Test
    fun refusesWithoutATenantOrAnInstance() =
        runTest {
            val bound: VerifierResponseEncryptionKeyNameResolver = FixedResponseEncryptionKeyNameResolver(SERVER_KEY_NAME)

            assertNull(SessionResponseEncryptionKeyConfig(TenantScopedExecution(""), { bound }).resolveEncryptionKeyName(INSTANCE_ID))
            assertNull(SessionResponseEncryptionKeyConfig(TenantScopedExecution(TENANT_ID), { bound }).resolveEncryptionKeyName(""))
        }

    @Test
    fun aBlankAnswerIsARefusalRatherThanAKeyName() =
        runTest {
            val config =
                SessionResponseEncryptionKeyConfig(
                    execution = TenantScopedExecution(TENANT_ID),
                    keyNameResolver = { FixedResponseEncryptionKeyNameResolver("   ") },
                )

            assertNull(config.resolveEncryptionKeyName(INSTANCE_ID))
        }

    private companion object {
        const val TENANT_ID = "tenant-acme"
        const val INSTANCE_ID = "acme"
        const val SERVER_KEY_NAME = "jarm-encryption-acme"
    }
}

/** The reasons a server-side binding cannot be honoured. All of them must look the same downstream. */
private enum class BindingShape {
    ABSENT,
    DETACHED,
    CROSS_TENANT,
    INACTIVE,
    UNMAPPED,
}

/**
 * A deployment whose binding cannot be honoured. The seam offers exactly one way to express that,
 * which is what keeps the five shapes indistinguishable to the protocol.
 */
private class ShapedResponseEncryptionKeyNameResolver(
    private val shape: BindingShape,
) : VerifierResponseEncryptionKeyNameResolver {
    override suspend fun resolveResponseEncryptionKeyName(
        tenantId: String,
        verifierInstanceId: String,
    ): String? =
        when (shape) {
            BindingShape.ABSENT,
            BindingShape.DETACHED,
            BindingShape.CROSS_TENANT,
            BindingShape.INACTIVE,
            BindingShape.UNMAPPED,
            -> null
        }
}

/** A deployment with a usable binding: the key name is server-derived and stable. */
private class FixedResponseEncryptionKeyNameResolver(
    private val keyName: String,
) : VerifierResponseEncryptionKeyNameResolver {
    override suspend fun resolveResponseEncryptionKeyName(
        tenantId: String,
        verifierInstanceId: String,
    ): String = keyName
}

/** [SessionExecution] carrying a tenant, which the response-encryption seam is keyed on. */
private class TenantScopedExecution(
    override val tenantId: String,
) : SessionExecution {
    override val sessionContext: SessionContext
        get() = throw UnsupportedOperationException("not used in this test")
    override val sessionContextManager: SessionContextManager
        get() = throw UnsupportedOperationException("not used in this test")
    override val log: SessionLogService
        get() = throw UnsupportedOperationException("not used in this test")
    override val conf: ContextConfig
        get() = throw UnsupportedOperationException("not used in this test")
}
