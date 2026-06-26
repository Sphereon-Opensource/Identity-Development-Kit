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
 *
 */

package com.sphereon.crypto.core

import com.sphereon.core.api.model.Origin
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Filter criteria for querying managed key references.
 */
@Serializable
@JsExportCompat
data class
ManagedKeyReferenceFilter
    @JvmOverloads
    constructor(
        val providerId: String? = null,
        val alias: String? = null,
        val kid: String? = null,
        val origin: Origin? = null,
        val keyType: KeyTypeMapping? = null,
    )

/**
 * Convert a fully-resolved [ManagedKeyInfoType] to a metadata-only [ManagedKeyReference].
 */
fun ManagedKeyInfoType<*>.toKeyReference(origin: Origin = Origin.MANAGED): ManagedKeyReference =
    ManagedKeyReference(
        alias = alias,
        kid = kid,
        providerId = providerId,
        origin = origin,
        signatureAlgorithm = signatureAlgorithm,
        keyType = keyType,
        keyVisibility = keyVisibility,
        keyEncoding = keyEncoding,
    )

/**
 * Convert any [KeyInfoType] to a [ManagedKeyReference] if it has the required fields.
 * Returns null if alias or providerId are missing.
 */
fun KeyInfoType<*>.toKeyReferenceOrNull(origin: Origin = Origin.MANAGED): ManagedKeyReference? {
    val a = alias ?: return null
    val p = providerId ?: return null
    return ManagedKeyReference(
        alias = a,
        kid = kid,
        providerId = p,
        origin = origin,
        signatureAlgorithm = signatureAlgorithm,
        keyType = keyType,
        keyVisibility = keyVisibility,
        keyEncoding = keyEncoding,
    )
}

/**
 * Convert any [KeyInfoType] to a signing reference when it has a managed alias.
 *
 * Signing must address the private key by its managed provider alias. A public key id
 * derived for JOSE/COSE headers is not necessarily the provider lookup id, so do not
 * send it as an additional lookup constraint to remote KMS services.
 */
fun KeyInfoType<*>.toSigningKeyReferenceOrNull(origin: Origin = Origin.MANAGED): ManagedKeyReference? {
    val a = alias ?: return null
    val p = providerId ?: return null
    return ManagedKeyReference(
        alias = a,
        kid = null,
        providerId = p,
        origin = origin,
        signatureAlgorithm = signatureAlgorithm,
        keyType = keyType,
        keyVisibility = keyVisibility,
        keyEncoding = keyEncoding,
    )
}
