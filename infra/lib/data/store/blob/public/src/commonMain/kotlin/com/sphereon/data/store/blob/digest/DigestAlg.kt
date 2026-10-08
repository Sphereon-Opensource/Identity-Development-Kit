/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.blob.digest

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Crypto-free digest algorithm identifiers for blob content addressing and integrity.
 *
 * Kept in infrastructure so the blob public ABI does not depend on identity-security.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobDigestAlg", exact = true)
enum class DigestAlg(
    val internalName: String,
    val javaName: String,
    val oid: String,
    val xmlId: String? = null,
    val jadesId: String? = null,
    val httpHeaderId: String? = null,
    val saltLength: Int? = 0,
) {
    NONE("", "", "", ""),
    SHA256("SHA256", "SHA-256", "2.16.840.1.101.3.4.2.1", "http://www.w3.org/2001/04/xmlenc#sha256", "S256", "SHA-256", 32),
    SHA384("SHA384", "SHA-384", "2.16.840.1.101.3.4.2.2", "http://www.w3.org/2001/04/xmlenc#sha384", "S384", "SHA-384", 48),
    SHA512("SHA512", "SHA-512", "2.16.840.1.101.3.4.2.3", "http://www.w3.org/2001/04/xmlenc#sha512", "S512", "SHA-512", 64),
    SHA3_256("SHA3-256", "SHA3-256", "2.16.840.1.101.3.4.2.8", "http://www.w3.org/2007/05/xmldsig-more#sha3-256", "S3-256", null, 32),
    SHA3_384("SHA3-384", "SHA3-384", "2.16.840.1.101.3.4.2.9", "http://www.w3.org/2007/05/xmldsig-more#sha3-384", "S3-384", null, 48),
    SHA3_512("SHA3-512", "SHA3-512", "2.16.840.1.101.3.4.2.10", "http://www.w3.org/2007/05/xmldsig-more#sha3-512", "S3-512", null, 64),
    ;

    companion object {
        @JsStatic
        @JvmStatic
        fun isNone(digestAlg: DigestAlg?): Boolean = digestAlg == null || digestAlg == NONE

        @JsStatic
        @JsName("fromValue")
        @JvmStatic
        fun fromValue(name: String): DigestAlg =
            entries.find { entry -> entry.internalName == name || entry.httpHeaderId == name || entry.javaName == name }
                ?: throw IllegalArgumentException("Unknown value $name")
    }
}
