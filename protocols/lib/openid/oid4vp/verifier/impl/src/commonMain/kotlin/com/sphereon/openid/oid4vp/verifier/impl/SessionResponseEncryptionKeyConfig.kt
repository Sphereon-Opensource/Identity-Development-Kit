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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.config.ResponseEncryptionKeyConfig
import com.sphereon.openid.oid4vp.verifier.spi.VerifierResponseEncryptionKeyNameResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Resolves the verifier's response-encryption key name from the active tenant plus the verifier
 * instance the authorization session belongs to.
 *
 * A bound [VerifierResponseEncryptionKeyNameResolver] is the only source. There is deliberately no
 * configured alias, provider id, or instance-derived name to fall back to: an encrypted response mode
 * whose key material is not centrally bound is refused, so nothing selects a key from a value a
 * tenant, a caller, or a stored session could reach.
 *
 * This type touches no KMS. Resolution can therefore never mint key material as a side effect: the
 * key is provisioned durably, out of band, and a verifier that finds none refuses.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResponseEncryptionKeyConfig>())
class SessionResponseEncryptionKeyConfig(
    private val execution: SessionExecution,
    private val keyNameResolver: Provider<VerifierResponseEncryptionKeyNameResolver>? = null,
) : ResponseEncryptionKeyConfig {
    override suspend fun resolveEncryptionKeyName(verifierInstanceId: String): String? {
        val resolver = keyNameResolver?.invoke() ?: return null
        val tenantId = execution.tenantId.takeIf { it.isNotBlank() } ?: return null
        val instanceId = verifierInstanceId.takeIf { it.isNotBlank() } ?: return null
        return resolver.resolveResponseEncryptionKeyName(tenantId, instanceId)?.takeIf { it.isNotBlank() }
    }
}
