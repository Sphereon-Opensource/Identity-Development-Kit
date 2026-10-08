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

package com.sphereon.crypto.core.x509

import at.asitplus.awesn1.crypto.pki.X509CertificateExtension

// In awesn1 0.3.0 [X509CertificateExtension.value] is the raw extnValue OCTET STRING content,
// i.e. the DER encoding of the inner KeyUsage BIT STRING, which is exactly what
// [KeyUsage.fromDerBitString] consumes.
internal fun getKeyUsageContent(extensions: List<X509CertificateExtension>?): ByteArray? =
    extensions
        ?.firstOrNull { it.oid.toString() == X509ExtensionOids.KEY_USAGE }
        ?.value

fun parseKeyUsage(raw: ByteArray): Map<String, Boolean> {
    val keyUsage = KeyUsage.fromDerBitString(raw)
    return KeyUsageFlag.entries.associate { flag ->
        flag.value to (keyUsage.flags[flag] ?: false)
    }
}
