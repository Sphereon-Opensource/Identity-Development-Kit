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

package com.sphereon.crypto.jose.jwe

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName
// ============================================================================
// JWE Header Builder
// ============================================================================

/**
 * Builder for JWE headers.
 * Provides a fluent API for constructing JWE headers with standard and custom parameters.
 *
 * Example:
 * ```kotlin
 * val header = JweHeaderBuilder()
 *     .alg("RSA-OAEP-256")
 *     .enc("A256GCM")
 *     .kid("key-123")
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweHeaderBuilder", exact = true)
@JsExportCompat
class JweHeaderBuilder {
    private val header = JweHeader()

    /**
     * Sets the key encryption algorithm (alg) header parameter.
     * @param value The algorithm name (e.g., "RSA-OAEP", "ECDH-ES+A256KW")
     */
    fun alg(value: String): JweHeaderBuilder {
        header.alg = value
        return this
    }

    /**
     * Sets the content encryption algorithm (enc) header parameter.
     * @param value The algorithm name (e.g., "A256GCM", "A128CBC-HS256")
     */
    fun enc(value: String): JweHeaderBuilder {
        header.enc = value
        return this
    }

    /**
     * Sets the compression algorithm (zip) header parameter.
     * @param value The compression algorithm (typically "DEF" for DEFLATE)
     */
    fun zip(value: String): JweHeaderBuilder {
        header.zip = value
        return this
    }

    /**
     * Sets the key ID (kid) header parameter.
     * @param value The key identifier
     */
    fun kid(value: String): JweHeaderBuilder {
        header.kid = value
        return this
    }

    /**
     * Sets the JWK Set URL (jku) header parameter.
     * @param value The JWK Set URL
     */
    fun jku(value: String): JweHeaderBuilder {
        header.jku = value
        return this
    }

    /**
     * Sets the JWK (jwk) header parameter.
     * @param value The public key as JWK
     */
    fun jwk(value: Jwk): JweHeaderBuilder {
        header.jwk = value
        return this
    }

    /**
     * Sets the X.509 URL (x5u) header parameter.
     * @param value The X.509 certificate URL
     */
    fun x5u(value: String): JweHeaderBuilder {
        header.x5u = value
        return this
    }

    /**
     * Sets the X.509 certificate chain (x5c) header parameter.
     * @param certificates Array of base64-encoded certificates
     */
    fun x5c(vararg certificates: String): JweHeaderBuilder {
        header.x5c = arrayOf(*certificates)
        return this
    }

    /**
     * Sets the X.509 certificate SHA-1 thumbprint (x5t) header parameter.
     * @param value The base64url-encoded SHA-1 thumbprint
     */
    fun x5t(value: String): JweHeaderBuilder {
        header.x5t = value
        return this
    }

    /**
     * Sets the X.509 certificate SHA-256 thumbprint (x5t#S256) header parameter.
     * @param value The base64url-encoded SHA-256 thumbprint
     */
    fun x5tS256(value: String): JweHeaderBuilder {
        header.x5tS256 = value
        return this
    }

    /**
     * Sets the type (typ) header parameter.
     * @param value The token type (e.g., "JOSE", "JWT")
     */
    fun typ(value: String): JweHeaderBuilder {
        header.typ = value
        return this
    }

    /**
     * Sets the content type (cty) header parameter.
     * @param value The content type (e.g., "JWT" for nested JWT)
     */
    fun cty(value: String): JweHeaderBuilder {
        header.cty = value
        return this
    }

    /**
     * Sets the critical (crit) header parameter.
     * @param headers Array of header names that must be understood
     */
    fun crit(vararg headers: String): JweHeaderBuilder {
        header.crit = arrayOf(*headers)
        return this
    }

    /**
     * Sets the ephemeral public key (epk) header parameter for ECDH-ES.
     * @param value The ephemeral public key as JWK
     */
    fun epk(value: Jwk): JweHeaderBuilder {
        header.epk = value
        return this
    }

    /**
     * Sets the Agreement PartyUInfo (apu) header parameter.
     * @param value Base64url-encoded PartyUInfo value
     */
    fun apu(value: String): JweHeaderBuilder {
        header.apu = value
        return this
    }

    /**
     * Sets the Agreement PartyVInfo (apv) header parameter.
     * @param value Base64url-encoded PartyVInfo value
     */
    fun apv(value: String): JweHeaderBuilder {
        header.apv = value
        return this
    }

    /**
     * Sets the initialization vector (iv) header parameter for AES-GCMKW.
     * @param value Base64url-encoded IV
     */
    fun iv(value: String): JweHeaderBuilder {
        header.iv = value
        return this
    }

    /**
     * Sets the authentication tag (tag) header parameter for AES-GCMKW.
     * @param value Base64url-encoded tag
     */
    fun tag(value: String): JweHeaderBuilder {
        header.tag = value
        return this
    }

    /**
     * Sets the PBES2 salt input (p2s) header parameter.
     * @param value Base64url-encoded salt
     */
    fun p2s(value: String): JweHeaderBuilder {
        header.p2s = value
        return this
    }

    /**
     * Sets the PBES2 iteration count (p2c) header parameter.
     * @param value The iteration count
     */
    fun p2c(value: Int): JweHeaderBuilder {
        header.p2c = value
        return this
    }

    /**
     * Builds the JweHeader.
     */
    fun build(): JweHeader = header

    companion object {
        /**
         * Creates a new header builder.
         */
        @JvmStatic
        fun create(): JweHeaderBuilder = JweHeaderBuilder()

        /**
         * Creates a new header builder from an existing JweHeader.
         */
        @JvmStatic
        fun from(header: JweHeader): JweHeaderBuilder {
            val builder = JweHeaderBuilder()
            header.alg?.let { builder.alg(it) }
            header.enc?.let { builder.enc(it) }
            header.zip?.let { builder.zip(it) }
            header.kid?.let { builder.kid(it) }
            header.jku?.let { builder.jku(it) }
            header.jwk?.let { builder.jwk(it) }
            header.x5u?.let { builder.x5u(it) }
            header.x5c?.let { builder.x5c(*it) }
            header.x5t?.let { builder.x5t(it) }
            header.x5tS256?.let { builder.x5tS256(it) }
            header.typ?.let { builder.typ(it) }
            header.cty?.let { builder.cty(it) }
            header.crit?.let { builder.crit(*it) }
            header.epk?.let { builder.epk(it) }
            header.apu?.let { builder.apu(it) }
            header.apv?.let { builder.apv(it) }
            header.iv?.let { builder.iv(it) }
            header.tag?.let { builder.tag(it) }
            header.p2s?.let { builder.p2s(it) }
            header.p2c?.let { builder.p2c(it) }
            return builder
        }
    }
}

// ============================================================================
// JWE Options Builder
// ============================================================================

/**
 * Builder for CreateJweOpts.
 * Provides a fluent API for configuring JWE creation options.
 *
 * Example:
 * ```kotlin
 * val opts = JweOptsBuilder()
 *     .compress()
 *     .protectedHeader {
 *         typ("JOSE")
 *     }
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JweOptsBuilder", exact = true)
@JsExportCompat
class JweOptsBuilder {
    private var compress: Boolean = false
    private var protectedHeaderOverrides: JweHeader? = null
    private var unprotectedHeader: JweHeader? = null

    /**
     * Enables DEFLATE compression.
     */
    @kotlin.js.JsName("enableCompression")
    fun compress(): JweOptsBuilder {
        this.compress = true
        return this
    }

    /**
     * Sets compression explicitly.
     * @param value Whether to compress
     */
    fun compress(value: Boolean): JweOptsBuilder {
        this.compress = value
        return this
    }

    /**
     * Sets additional protected header parameters.
     * @param header The header overrides
     */
    @kotlin.js.JsName("protectedHeaderObject")
    fun protectedHeader(header: JweHeader): JweOptsBuilder {
        this.protectedHeaderOverrides = header
        return this
    }

    /**
     * Sets additional protected header parameters using a builder.
     * @param builder Function to configure the header builder
     */
    @kotlin.js.JsName("protectedHeaderBuilder")
    fun protectedHeader(builder: JweHeaderBuilder.() -> Unit): JweOptsBuilder {
        this.protectedHeaderOverrides = JweHeaderBuilder().apply(builder).build()
        return this
    }

    /**
     * Sets the unprotected header (for JSON formats).
     * @param header The unprotected header
     */
    @kotlin.js.JsName("unprotectedHeaderObject")
    fun unprotectedHeader(header: JweHeader): JweOptsBuilder {
        this.unprotectedHeader = header
        return this
    }

    /**
     * Sets the unprotected header using a builder.
     * @param builder Function to configure the header builder
     */
    @kotlin.js.JsName("unprotectedHeaderBuilder")
    fun unprotectedHeader(builder: JweHeaderBuilder.() -> Unit): JweOptsBuilder {
        this.unprotectedHeader = JweHeaderBuilder().apply(builder).build()
        return this
    }

    /**
     * Builds the options.
     */
    fun build(): CreateJweOpts =
        CreateJweOpts(
            compress = compress,
            protectedHeaderOverrides = protectedHeaderOverrides,
            unprotectedHeader = unprotectedHeader,
        )

    companion object {
        /**
         * Creates a new options builder.
         */
        @JvmStatic
        fun create(): JweOptsBuilder = JweOptsBuilder()
    }
}

// ============================================================================
// PrepareJweArgs Builder
// ============================================================================

/**
 * Builder for PrepareJweArgs.
 * Provides a fluent API for constructing JWE preparation arguments.
 *
 * Example:
 * ```kotlin
 * val args = PrepareJweArgsBuilder()
 *     .plaintext("Hello, World!".encodeToByteArray())
 *     .recipient(recipientKey)
 *     .keyEncryptionAlg("ECDH-ES+A256KW")
 *     .contentEncryptionAlg("A256GCM")
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("PrepareJweArgsBuilder", exact = true)
@JsExportCompat
class PrepareJweArgsBuilder {
    private var plaintext: ByteArray? = null
    private var recipient: ManagedIdentifierOptsOrResult? = null
    private var keyEncryptionAlg: String? = null
    private var contentEncryptionAlg: String? = null
    private var opts: CreateJweOpts = CreateJweOpts()

    /**
     * Sets the plaintext to encrypt.
     * @param value The plaintext bytes
     */
    fun plaintext(value: ByteArray): PrepareJweArgsBuilder {
        this.plaintext = value
        return this
    }

    /**
     * Sets the plaintext from a string.
     * @param value The plaintext string (UTF-8 encoded)
     */
    fun plaintextString(value: String): PrepareJweArgsBuilder {
        this.plaintext = value.encodeToByteArray()
        return this
    }

    /**
     * Sets the recipient's identifier/key.
     * @param value The recipient's managed identifier
     */
    fun recipient(value: ManagedIdentifierOptsOrResult): PrepareJweArgsBuilder {
        this.recipient = value
        return this
    }

    /**
     * Sets the key encryption algorithm.
     * @param value The algorithm identifier (e.g., "RSA-OAEP", "ECDH-ES+A256KW")
     */
    fun keyEncryptionAlg(value: String): PrepareJweArgsBuilder {
        this.keyEncryptionAlg = value
        return this
    }

    /**
     * Sets the content encryption algorithm.
     * @param value The algorithm identifier (e.g., "A256GCM", "A128CBC-HS256")
     */
    fun contentEncryptionAlg(value: String): PrepareJweArgsBuilder {
        this.contentEncryptionAlg = value
        return this
    }

    /**
     * Sets the JWE creation options.
     * @param opts The options
     */
    @kotlin.js.JsName("optionsObject")
    fun options(opts: CreateJweOpts): PrepareJweArgsBuilder {
        this.opts = opts
        return this
    }

    /**
     * Sets the JWE creation options using a builder.
     * @param builder Function to configure the options builder
     */
    @kotlin.js.JsName("optionsBuilder")
    fun options(builder: JweOptsBuilder.() -> Unit): PrepareJweArgsBuilder {
        this.opts = JweOptsBuilder().apply(builder).build()
        return this
    }

    /**
     * Builds the PrepareJweArgs.
     * @throws IllegalArgumentException if required fields are missing
     */
    fun build(): PrepareJweArgs {
        requireNotNull(keyEncryptionAlg) { "keyEncryptionAlg is required" }
        requireNotNull(contentEncryptionAlg) { "contentEncryptionAlg is required" }
        return PrepareJweArgs(
            plaintext = plaintext,
            recipient = recipient,
            keyEncryptionAlg = keyEncryptionAlg!!,
            contentEncryptionAlg = contentEncryptionAlg!!,
            opts = opts,
        )
    }

    companion object {
        /**
         * Creates a new PrepareJweArgs builder.
         */
        @JvmStatic
        fun create(): PrepareJweArgsBuilder = PrepareJweArgsBuilder()
    }
}

// ============================================================================
// CreateJweCompactArgs Builder
// ============================================================================

/**
 * Builder for CreateJweCompactArgs.
 * Provides a fluent API for constructing compact JWE creation arguments.
 *
 * Example:
 * ```kotlin
 * val args = CreateJweCompactArgsBuilder()
 *     .preparedJwe(preparedJwe)
 *     .aad(additionalData)
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweCompactArgsBuilder", exact = true)
@JsExportCompat
class CreateJweCompactArgsBuilder {
    private var preparedJwe: PreparedJwe? = null
    private var aad: ByteArray? = null

    /**
     * Sets the prepared JWE object.
     * @param value The prepared JWE
     */
    fun preparedJwe(value: PreparedJwe): CreateJweCompactArgsBuilder {
        this.preparedJwe = value
        return this
    }

    /**
     * Sets the additional authenticated data.
     * @param value The AAD bytes
     */
    fun aad(value: ByteArray): CreateJweCompactArgsBuilder {
        this.aad = value
        return this
    }

    /**
     * Builds the CreateJweCompactArgs.
     */
    fun build(): CreateJweCompactArgs =
        CreateJweCompactArgs(
            preparedJwe = preparedJwe,
            aad = aad,
        )

    companion object {
        /**
         * Creates a new CreateJweCompactArgs builder.
         */
        @JvmStatic
        fun create(): CreateJweCompactArgsBuilder = CreateJweCompactArgsBuilder()
    }
}

// ============================================================================
// CreateJweJsonArgs Builder
// ============================================================================

/**
 * Builder for CreateJweJsonArgs.
 * Provides a fluent API for constructing JSON JWE creation arguments.
 *
 * Example:
 * ```kotlin
 * val args = CreateJweJsonArgsBuilder()
 *     .preparedJwe(preparedJwe)
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonArgsBuilder", exact = true)
@JsExportCompat
class CreateJweJsonArgsBuilder {
    private var preparedJwe: PreparedJwe? = null
    private var aad: ByteArray? = null

    /**
     * Sets the prepared JWE object.
     * @param value The prepared JWE
     */
    fun preparedJwe(value: PreparedJwe): CreateJweJsonArgsBuilder {
        this.preparedJwe = value
        return this
    }

    /**
     * Sets the additional authenticated data.
     * @param value The AAD bytes
     */
    fun aad(value: ByteArray): CreateJweJsonArgsBuilder {
        this.aad = value
        return this
    }

    /**
     * Builds the CreateJweJsonArgs.
     */
    fun build(): CreateJweJsonArgs =
        CreateJweJsonArgs(
            preparedJwe = preparedJwe,
            aad = aad,
        )

    companion object {
        /**
         * Creates a new CreateJweJsonArgs builder.
         */
        @JvmStatic
        fun create(): CreateJweJsonArgsBuilder = CreateJweJsonArgsBuilder()
    }
}

// ============================================================================
// CreateJweJsonGeneralArgs Builder
// ============================================================================

/**
 * Builder for CreateJweJsonGeneralArgs.
 * Provides a fluent API for constructing multi-recipient JWE creation arguments.
 *
 * Example:
 * ```kotlin
 * val args = CreateJweJsonGeneralArgsBuilder()
 *     .preparedJwe(preparedJwe)
 *     .addRecipient(recipient2)
 *     .addRecipient(recipient3) {
 *         kid("recipient-3-key")
 *     }
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateJweJsonGeneralArgsBuilder", exact = true)
@JsExportCompat
class CreateJweJsonGeneralArgsBuilder {
    private var preparedJwe: PreparedJwe? = null
    private val additionalRecipients = mutableListOf<JweRecipientInfo>()
    private var aad: ByteArray? = null

    /**
     * Sets the prepared JWE object (includes the first recipient).
     * @param value The prepared JWE
     */
    fun preparedJwe(value: PreparedJwe): CreateJweJsonGeneralArgsBuilder {
        this.preparedJwe = value
        return this
    }

    /**
     * Adds an additional recipient.
     * @param recipient The recipient's managed identifier
     */
    @kotlin.js.JsName("addRecipientSimple")
    fun addRecipient(recipient: ManagedIdentifierOptsOrResult): CreateJweJsonGeneralArgsBuilder {
        additionalRecipients.add(JweRecipientInfo(recipient = recipient))
        return this
    }

    /**
     * Adds an additional recipient with per-recipient header.
     * @param recipient The recipient's managed identifier
     * @param headerBuilder Function to configure the per-recipient header
     */
    @kotlin.js.JsName("addRecipientWithHeader")
    fun addRecipient(
        recipient: ManagedIdentifierOptsOrResult,
        headerBuilder: JweHeaderBuilder.() -> Unit,
    ): CreateJweJsonGeneralArgsBuilder {
        val header = JweHeaderBuilder().apply(headerBuilder).build()
        additionalRecipients.add(JweRecipientInfo(recipient = recipient, perRecipientHeader = header))
        return this
    }

    /**
     * Adds an additional recipient info object.
     * @param recipientInfo The recipient info
     */
    fun addRecipientInfo(recipientInfo: JweRecipientInfo): CreateJweJsonGeneralArgsBuilder {
        additionalRecipients.add(recipientInfo)
        return this
    }

    /**
     * Sets the additional authenticated data.
     * @param value The AAD bytes
     */
    fun aad(value: ByteArray): CreateJweJsonGeneralArgsBuilder {
        this.aad = value
        return this
    }

    /**
     * Builds the CreateJweJsonGeneralArgs.
     */
    fun build(): CreateJweJsonGeneralArgs =
        CreateJweJsonGeneralArgs(
            preparedJwe = preparedJwe,
            additionalRecipients = additionalRecipients.takeIf { it.isNotEmpty() },
            aad = aad,
        )

    companion object {
        /**
         * Creates a new CreateJweJsonGeneralArgs builder.
         */
        @JvmStatic
        fun create(): CreateJweJsonGeneralArgsBuilder = CreateJweJsonGeneralArgsBuilder()
    }
}

// ============================================================================
// DecryptJweArgs Builder
// ============================================================================

/**
 * Builder for DecryptJweArgs.
 * Provides a fluent API for constructing JWE decryption arguments.
 *
 * Example:
 * ```kotlin
 * val args = DecryptJweArgsBuilder()
 *     .jwe(encryptedJwe)
 *     .decryptor(privateKey)
 *     .build()
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DecryptJweArgsBuilder", exact = true)
@JsExportCompat
class DecryptJweArgsBuilder {
    private var jwe: Jwe? = null
    private var decryptor: ManagedIdentifierOptsOrResult? = null

    /**
     * Sets the JWE to decrypt.
     * @param value The JWE object
     */
    fun jwe(value: Jwe): DecryptJweArgsBuilder {
        this.jwe = value
        return this
    }

    /**
     * Sets the decryptor's identifier/key (our private key).
     * @param value The decryptor's managed identifier
     */
    fun decryptor(value: ManagedIdentifierOptsOrResult): DecryptJweArgsBuilder {
        this.decryptor = value
        return this
    }

    /**
     * Builds the DecryptJweArgs.
     */
    fun build(): DecryptJweArgs =
        DecryptJweArgs(
            jwe = jwe,
            decryptor = decryptor,
        )

    companion object {
        /**
         * Creates a new DecryptJweArgs builder.
         */
        @JvmStatic
        fun create(): DecryptJweArgsBuilder = DecryptJweArgsBuilder()
    }
}

// ============================================================================
// Extension Functions for Convenience DSL
// ============================================================================

/**
 * Creates a JWE header using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val header = jweHeader {
 *     alg("RSA-OAEP-256")
 *     enc("A256GCM")
 *     kid("key-123")
 * }
 * ```
 */
fun jweHeader(builder: JweHeaderBuilder.() -> Unit): JweHeader = JweHeaderBuilder().apply(builder).build()

/**
 * Creates JWE options using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val opts = jweOptions {
 *     compress()
 *     protectedHeader {
 *         typ("JOSE")
 *     }
 * }
 * ```
 */
fun jweOptions(builder: JweOptsBuilder.() -> Unit): CreateJweOpts = JweOptsBuilder().apply(builder).build()

/**
 * Creates PrepareJweArgs using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val args = prepareJweArgs {
 *     plaintextString("Hello, World!")
 *     recipient(recipientKey)
 *     keyEncryptionAlg("ECDH-ES+A256KW")
 *     contentEncryptionAlg("A256GCM")
 * }
 * ```
 */
fun prepareJweArgs(builder: PrepareJweArgsBuilder.() -> Unit): PrepareJweArgs = PrepareJweArgsBuilder().apply(builder).build()

/**
 * Creates CreateJweCompactArgs using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val args = createJweCompactArgs {
 *     preparedJwe(preparedJwe)
 * }
 * ```
 */
fun createJweCompactArgs(builder: CreateJweCompactArgsBuilder.() -> Unit): CreateJweCompactArgs = CreateJweCompactArgsBuilder().apply(builder).build()

/**
 * Creates CreateJweJsonArgs using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val args = createJweJsonArgs {
 *     preparedJwe(preparedJwe)
 * }
 * ```
 */
fun createJweJsonArgs(builder: CreateJweJsonArgsBuilder.() -> Unit): CreateJweJsonArgs = CreateJweJsonArgsBuilder().apply(builder).build()

/**
 * Creates CreateJweJsonGeneralArgs using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val args = createJweJsonGeneralArgs {
 *     preparedJwe(preparedJwe)
 *     addRecipient(recipient2)
 *     addRecipient(recipient3) {
 *         kid("recipient-3-key")
 *     }
 * }
 * ```
 */
fun createJweJsonGeneralArgs(builder: CreateJweJsonGeneralArgsBuilder.() -> Unit): CreateJweJsonGeneralArgs = CreateJweJsonGeneralArgsBuilder().apply(builder).build()

/**
 * Creates DecryptJweArgs using a builder DSL.
 *
 * Example:
 * ```kotlin
 * val args = decryptJweArgs {
 *     jwe(encryptedJwe)
 *     decryptor(privateKey)
 * }
 * ```
 */
fun decryptJweArgs(builder: DecryptJweArgsBuilder.() -> Unit): DecryptJweArgs = DecryptJweArgsBuilder().apply(builder).build()
