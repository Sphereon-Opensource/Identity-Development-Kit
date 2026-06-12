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

package com.sphereon.data.store.party.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * An inert envelope describing a protected correlation identifier value.
 *
 * This is a pure data carrier: it holds the protection [mode] together with whatever
 * representation(s) of the value apply (plaintext, ciphertext, and/or HMAC) plus opaque
 * references to the keys used. The IDK lite model performs NO encryption, hashing, or key
 * resolution; those concerns live in higher layers (EDK/VDX). Every field is optional so
 * the envelope can represent any combination required by the chosen [mode].
 */
@JsExportCompat
@Serializable
data class ProtectedIdentifierValue
    @JvmOverloads
    constructor(
        /** The protection mode applied to the identifier value. */
        @SerialName("mode")
        val mode: IdentifierProtectionMode,
        /** The readable plaintext value, when applicable. */
        @SerialName("plaintext")
        val plaintext: String? = null,
        /** The encrypted value, when applicable. */
        @SerialName("valueCiphertext")
        val valueCiphertext: String? = null,
        /** Opaque reference to the encryption key used for [valueCiphertext]. */
        @SerialName("encKeyRef")
        val encKeyRef: String? = null,
        /** Version of the encryption key referenced by [encKeyRef]. */
        @SerialName("encKeyVersion")
        val encKeyVersion: String? = null,
        /** The HMAC (blind index) of the value, when applicable. */
        @SerialName("valueHmac")
        val valueHmac: String? = null,
        /** Opaque reference to the HMAC key used for [valueHmac]. */
        @SerialName("hmacKeyRef")
        val hmacKeyRef: String? = null,
        /** Version of the HMAC key referenced by [hmacKeyRef]. */
        @SerialName("hmacKeyVersion")
        val hmacKeyVersion: String? = null,
    )
