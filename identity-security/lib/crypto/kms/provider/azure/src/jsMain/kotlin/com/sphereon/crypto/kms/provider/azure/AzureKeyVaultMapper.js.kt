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

package com.sphereon.crypto.kms.provider.azure

import org.khronos.webgl.Uint8Array
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.encodeToBase64
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlin.js.unsafeCast

private fun String.toJwaKeyType(): JwaKeyType {
    return when (this) {
        "EC" -> JwaKeyType.EC
        "RSA", "RSA-HSM" -> JwaKeyType.RSA
        else -> throw IllegalArgumentException("Unsupported key type: $this")
    }
}

private fun String.toJoseKeyOperationsArray(): JoseKeyOperations {
    return when (this) {
        "sign" -> JoseKeyOperations.SIGN
        "verify" -> JoseKeyOperations.VERIFY
        "encrypt" -> JoseKeyOperations.ENCRYPT
        "decrypt" -> JoseKeyOperations.DECRYPT
        "wrapKey" -> JoseKeyOperations.WRAP_KEY
        "unwrapKey" -> JoseKeyOperations.UNWRAP_KEY
        "deriveKey" -> JoseKeyOperations.DERIVE_KEY
        "deriveBits" -> JoseKeyOperations.DERIVE_BITS
        else -> throw IllegalArgumentException("Unsupported key operation: $this")
    }
}

fun AzureKeyvaultKeyDetails.toJwaAlgorithm(): JwaAlgorithm? {
    return when (this.kty) {
        "EC" ->
            when (this.crv) {
                "P-256" -> JwaAlgorithm.ES256
                "P-384" -> JwaAlgorithm.ES384
                "P-521" -> JwaAlgorithm.ES512
                else -> throw IllegalArgumentException("Unsupported algorithm: ${this.kty}.${this.crv}")
            }

        "RSA", "RSA-HSM" ->
            when (this.alg) {
                "PS256" -> JwaAlgorithm.PS256
                "PS384" -> JwaAlgorithm.PS384
                "PS512" -> JwaAlgorithm.PS512
                "RS256" -> JwaAlgorithm.RS256
                "RS384" -> JwaAlgorithm.RS384
                "RS512" -> JwaAlgorithm.RS512
                // A Key Vault RSA descriptor may omit alg. Such a key is algorithm-neutral
                // across RS*/PS*; do not fabricate PS256 metadata during lookup.
                else -> null
            }

        else -> throw IllegalArgumentException("Unsupported key type: ${this.kty}")
    }
}

fun AzureKeyVaultKey.toJwk(): Jwk {
    return Jwk(
        alg = key.toJwaAlgorithm(),
        kid = key.kid,
        kty = key.kty.toJwaKeyType(),
        key_ops = key.keyOps.map { it.toJoseKeyOperationsArray() }.toTypedArray(),
        crv = key.crv?.let { JwaCurve.fromValue(it) },
        x = key.x?.encodeToBase64(),
        y = key.y?.encodeToBase64(),
        n = key.n?.encodeToBase64(),
        e = key.e?.encodeToBase64(),
        d = key.d?.encodeToBase64(),
    )
}

fun AzureKeyvaultKeyDetails.getSignatureAlgorithmName(): String {
    return when (this.kty) {
        "EC" ->
            when (this.crv) {
                "P-256" -> "ES256"
                "P-384" -> "ES384"
                "P-521" -> "ES512"
                else -> throw IllegalArgumentException("Unsupported algorithm: ${this.kty}.${this.crv}")
            }

        "RSA", "RSA-HSM" ->
            alg ?: throw IllegalArgumentException("RSA Azure key does not declare a signing algorithm; supply one explicitly")

        else -> throw IllegalArgumentException("Unsupported key type: ${this.kty}")
    }
}


/**
 * Converts this Jwk to a JsonWebKey suitable for Azure KeyVault import operations.
 * Handles the conversion of base64-encoded fields to Uint8Array as required by the Azure SDK.
 */
fun Jwk.toJSType(): JsonWebKey {
    return js("{}").unsafeCast<JsonWebKey>().apply {
        kty = this@toJSType.kty.value
        key_ops = this@toJSType.key_ops?.map { it.value }?.toTypedArray()
        crv = this@toJSType.crv?.value
        kid = this@toJSType.kid
        use = this@toJSType.use
        alg = this@toJSType.alg?.value

        // Convert base64 encoded values to Uint8Array for EC keys
        if (this@toJSType.kty == JwaKeyType.EC) {
            this@toJSType.x?.decodeFromBase64(true)?.let { bytes ->
                x = bytes.toUint8Array()
            }

            this@toJSType.y?.decodeFromBase64(true)?.let { bytes ->
                y = bytes.toUint8Array()
            }
        }

        // Convert base64 encoded values to Uint8Array for RSA keys
        if (this@toJSType.kty == JwaKeyType.RSA) {
            this@toJSType.n?.decodeFromBase64(true)?.let { bytes ->
                n = bytes.toUint8Array()
            }

            this@toJSType.e?.decodeFromBase64(true)?.let { bytes ->
                e = bytes.toUint8Array()
            }
        }

        // Handle private key graph if present
        this@toJSType.d?.decodeFromBase64(true)?.let { bytes ->
            d = bytes.toUint8Array()
        }
    }
}

/**
 * Converts a ByteArray to a JavaScript Uint8Array.
 */
private fun ByteArray.toUint8Array(): Uint8Array {
    val uint8Array = Uint8Array(this.size)
    for (i in this.indices) {
        uint8Array.asDynamic()[i] = this[i].toInt() and 0xFF
    }
    return uint8Array
}
