/*
 * Copyright (c) 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core.kms.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import com.sphereon.core.compat.JsExportCompat
import kotlin.native.ObjCName

// ============================================================================
// ResolvePublicKey Command
// ============================================================================

/**
 * Arguments for resolving a public key.
 *
 * @property keyInfo The key information containing metadata and the cryptographic key to be resolved
 * @property identifierMethod Optional method used to identify the key (e.g., jwk, kid, cose_key, x5c)
 * @property trustedCerts Optional array of trusted certificates used in the resolution process
 * @property verifyX509CertificateChain Optional boolean indicating whether the X.509 certificate chain should be verified
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolvePublicKeyArgs", exact = true)
@JsExportCompat
@Serializable
data class ResolvePublicKeyArgs(
    @kotlinx.serialization.Transient
    val keyInfo: KeyInfoType<*>? = null,
    @kotlinx.serialization.Transient
    val identifierMethod: IdentifierMethod? = null,
    val trustedCerts: Array<String>? = null,
    val verifyX509CertificateChain: Boolean? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ResolvePublicKeyArgs

        if (keyInfo != other.keyInfo) return false
        if (identifierMethod != other.identifierMethod) return false
        if (trustedCerts != null) {
            if (other.trustedCerts == null) return false
            if (!trustedCerts.contentEquals(other.trustedCerts)) return false
        } else if (other.trustedCerts != null) return false
        if (verifyX509CertificateChain != other.verifyX509CertificateChain) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyInfo?.hashCode() ?: 0
        result = 31 * result + (identifierMethod?.hashCode() ?: 0)
        result = 31 * result + (trustedCerts?.contentHashCode() ?: 0)
        result = 31 * result + (verifyX509CertificateChain?.hashCode() ?: 0)
        return result
    }
}

/**
 * Result of a public key resolution operation.
 *
 * @property resolvedKeyInfo The resolved key information including the cryptographic key and metadata
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolvePublicKeyResult", exact = true)
@JsExportCompat
data class ResolvePublicKeyResult(
    @kotlinx.serialization.Transient
    val resolvedKeyInfo: ResolvedKeyInfoType<*>? = null
)

/**
 * Command interface for resolving a public key.
 *
 * This command wraps the KeyManagerService.resolvePublicKey operation,
 * providing uniform logging, auditing, authorization, and plugin capabilities.
 *
 * Example usage:
 * ```kotlin
 * val args = ResolvePublicKeyArgs(
 *     keyInfo = keyInfo,
 *     identifierMethod = IdentifierMethod.JWK,
 *     verifyX509CertificateChain = true
 * )
 * val result = resolvePublicKeyCommand.execute(args)
 * if (result.isOk) {
 *     val resolvedKey = result.value.resolvedKeyInfo
 *     // Use resolved key...
 * }
 * ```
 */
interface ResolvePublicKeyCommand :
    ServiceCommand<ResolvePublicKeyArgs, ResolvePublicKeyResult> {

    companion object {
        const val COMMAND_ID = "kms.key.resolve"
    }

    override val commandId: String get() = COMMAND_ID
}
