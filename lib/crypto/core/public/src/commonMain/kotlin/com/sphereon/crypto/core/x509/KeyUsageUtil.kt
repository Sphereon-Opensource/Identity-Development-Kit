/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core.x509

import at.asitplus.signum.indispensable.asn1.Asn1BitString
import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.pki.X509CertificateExtension

internal fun getKeyUsageContent(extensions: List<X509CertificateExtension>?): ByteArray? =
    extensions
        ?.firstOrNull { it.oid.toString() == X509ExtensionOids.KEY_USAGE }
        ?.value
        ?.let { value ->
            when (value) {
                is Asn1BitString -> value.rawBytes
                is Asn1EncapsulatingOctetString -> value.content
                else -> null
            }
        }

fun parseKeyUsage(raw: ByteArray): Map<String, Boolean> {
    val keyUsage = KeyUsage.fromDerBitString(raw)
    return KeyUsageFlag.entries.associate { flag ->
        flag.value to (keyUsage.flags[flag] ?: false)
    }
}
