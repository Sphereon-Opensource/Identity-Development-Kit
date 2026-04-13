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

package com.sphereon.mdoc

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.CborBuilder
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.CborStructure
import com.sphereon.cbor.StringLabel
import com.sphereon.cbor.cborSerializer
import com.sphereon.cbor.toCborByteString
import com.sphereon.cbor.toCborUInt
import com.sphereon.cbor.dsl.cborMapBuilder
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.cbor.json.JsonView
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.mdoc.logging.IMdocDebugLogger
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.io.bytestring.ByteStringBuilder
import com.sphereon.cbor.HasFromCborWithOriginal
import com.sphereon.cbor.NumberLabel
import com.sphereon.core.api.encodeToHex
import com.sphereon.util.stringify
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.interop.toDerEcdhPrivateKey
import com.sphereon.crypto.core.interop.toDerEcdhPublicKey
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic

private val MDOC_READER_IDENTIFIER = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
private val MDOC_DEVICE_IDENTIFIER = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionEncryption", exact = true)
class SessionEncryption internal constructor(
    val selfRole: MdocRole,
    val selfPublicKey: ResolvedKeyInfoType<CoseKeyType>,
    val cryptoProvider: CryptographyProvider = CryptographyProvider.Default,
    val selfRawSessionKey: ByteArray,
    val remoteRawSessionKey: ByteArray,
) {
    private var encryptCount: UInt = 1U // Starts at one according to 18013-5
    private var decryptCount: UInt = 1U // Starts at one according to 18013-5

    lateinit var encodedReaderKey: CborEncodedItem<CoseKey>



    private fun isSendSessionEstablishment(): Boolean {
        return encryptCount == 1U && selfRole === MdocRole.MDOC_READER
    }

    suspend fun decryptSessionEstablishment(messageData: ByteArray) = decrypt(messageData).encryptedSessionEstablishment
        ?: throw IllegalStateException("No session establishment found. Are you looking for session data? Then call the correct decrypt method")

    suspend fun decryptSessionData(messageData: ByteArray) =
        decrypt(messageData).sessionData ?: throw IllegalStateException("No session data found. Are you looking for session establishment? Then call the correct decrypt method")


    @OptIn(DelicateCryptographyApi::class)
    suspend fun decrypt(messageData: ByteArray): DecryptResult {
        val map: CborMap<StringLabel, CborItem<*>> = cborSerializer.decode(messageData)
        if (map.value.containsKey(SessionEstablishment.Decoder.E_READER_KEY)) {
            val encodedReaderKeyMap: CborEncodedItem<CborMap<NumberLabel, CborItem<*>>> = SessionEstablishment.Decoder.E_READER_KEY.required(map)
            this.encodedReaderKey = encodedReaderKeyMap.copy(CoseKey.fromCborStructure(encodedReaderKeyMap.data()))
        }
        val encryptedSessionData = SessionData.Decoder.decodeCbor(messageData)
        var decryptedSessionData: SessionData? = null
        if (encryptedSessionData.data != null) {
            val ivBuilder = ByteStringBuilder(12)
            ivBuilder.append(if (selfRole === MdocRole.MDOC) MDOC_READER_IDENTIFIER else MDOC_DEVICE_IDENTIFIER)
            ivBuilder.appendCounter(decryptCount)
            val iv = ivBuilder.toByteString().toByteArray()
            check(iv.size == 12) { "Invalid IV length, expected 12 bytes, got: ${iv.encodeTo(Encoding.HEX)}" }
            val otherSessionKeyRaw = cryptoProvider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, remoteRawSessionKey)
            val selfSessionKeyRaw = cryptoProvider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, selfRawSessionKey)
            val plainTextData = otherSessionKeyRaw.cipher(tagSize = 128.bits).decryptWithIv(
                iv = iv, ciphertext = encryptedSessionData.data.value, associatedData = /*"".encodeToByteArray()*/ null
            )
            decryptCount++

            decryptedSessionData = encryptedSessionData.copy(data = plainTextData.toCborByteString())
        } else if (!::encodedReaderKey.isInitialized) {
            throw IllegalStateException("Cannot have an eReaderKey (session establishment), without data as mdoc holder")
        }
        return object : DecryptResult {
            // Both properties are mutual exclusive, hence the eReaderKey expression in both
            override val encryptedSessionEstablishment: SessionEstablishment?
                get() = if (::encodedReaderKey.isInitialized) SessionEstablishment(
                    encodedReaderKey = encodedReaderKey, data = encryptedSessionData.data!!, original = null
                ) else null
            override val sessionData: SessionData = if (decryptedSessionData !== null) decryptedSessionData else SessionData(status = encryptedSessionData.status, original = null)
        }

    }

    @OptIn(DelicateCryptographyApi::class)
    suspend fun encrypt(plainTextData: ByteArray? = null, status: Long? = null): ByteArray {
        var cipherText: ByteArray? = null
        val sendSessionEstablishment = isSendSessionEstablishment() // We do this before the below counter increase, as the logic looks for mdoc_reader and encrypt counter being 1
        if (plainTextData != null || sendSessionEstablishment) {
            requireNotNull(plainTextData) { "plain text data is required when encrypting data" }
            val ivBuilder = ByteStringBuilder(12)
            ivBuilder.append(if (selfRole === MdocRole.MDOC) MDOC_DEVICE_IDENTIFIER else MDOC_READER_IDENTIFIER)
            ivBuilder.appendCounter(encryptCount)
            val iv = ivBuilder.toByteString().toByteArray()
            check(iv.size == 12) { "Invalid IV length, expected 12 bytes, got: ${iv.encodeTo(Encoding.HEX)}" }
            val selfSessionKeyRaw = cryptoProvider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, selfRawSessionKey)
            cipherText = selfSessionKeyRaw.cipher(tagSize = 128.bits).encryptWithIv(iv = iv, plaintext = plainTextData, associatedData = null /* "".encodeToByteArray()*/)
            encryptCount++
        }
        val message: CborStructure<*, *>
        if (sendSessionEstablishment) {
            requireNotNull(cipherText) { "An initial message always needs a data object" }
            if (selfRole === MdocRole.MDOC_READER && !::encodedReaderKey.isInitialized) {
                // It already is initialized at this point for the mdoc. Since this is the send session establishment, let's make sure to also initialize it for the reader
                this.encodedReaderKey = CborEncodedItem.fromData(CoseKey.fromDTO(selfPublicKey.key))
            }
            require(::encodedReaderKey.isInitialized) { "Cannot have an eReaderKey (session establishment), without data as mdoc holder" }

            message = SessionEstablishment(
                encodedReaderKey = encodedReaderKey, data = cipherText.toCborByteString(), original = null
            )
        } else {
            message = SessionData(
                data = cipherText?.toCborByteString(), status = status?.toCborUInt(), original = null
            )
        }
        return message.encodeCbor()
    }

    suspend fun encryptAsSessionData(plainTextData: ByteArray? = null, status: Long? = SessionDataStatus.SESSION_TERMINATION.getCode()) =
        encrypt(plainTextData, status).let { SessionData.Decoder.decodeCbor(it) }

    suspend fun encryptAsSessionEstablishment(plainTextData: ByteArray? = null, status: Long? = null): SessionEstablishment {
        check(isSendSessionEstablishment()) { "Can only encrypt session establishment as a first action on the reader side" }
        return encrypt(plainTextData, status).let { SessionEstablishment.Decoder.decodeCbor(it) }
    }

    override fun toString(): String {
        return "SessionEncryption(selfRole=$selfRole, selfPublicKey=$selfPublicKey, cryptoProvider=$cryptoProvider, selfRawSessionKey=${stringify( selfRawSessionKey)}, remoteRawSessionKey=${stringify(remoteRawSessionKey)}, encryptCount=$encryptCount, decryptCount=$decryptCount, encodedReaderKey=$encodedReaderKey)"
    }


    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Builder", exact = true)
    class Builder(
        private var selfRole: MdocRole = MdocRole.MDOC,
        private var selfEphemeralPrivateKey: ResolvedKeyInfoType<CoseKey>? = null,
        private var remoteEphemeralPublicKey: ResolvedKeyInfoType<CoseKey>? = null,
        private var sessionTranscriptBytes: ByteArray? = null,
        private var provider: CryptographyProvider = CryptographyProvider.Default,
        private var debugLogger: IMdocDebugLogger? = null,
        private var preComputedSharedSecret: ByteArray? = null,
    ) {

        fun withSelfRole(selfRole: MdocRole) = apply { this.selfRole = selfRole }

        /**
         * Sets the self ephemeral private key for ECDH key agreement.
         *
         * The key must have a private part (d parameter) unless a pre-computed
         * shared secret is also provided via [withPreComputedSharedSecret].
         *
         * For iOS native keychain keys that don't export the private key,
         * use [withSelfPublicEphemeralKey] along with [withPreComputedSharedSecret].
         */
        fun withSelfPrivateEphemeralKey(selfEphemeralPrivateKey: ResolvedKeyInfoType<*>) = apply {
            this.selfEphemeralPrivateKey = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(selfEphemeralPrivateKey)
            // Note: d parameter check is deferred to build() to allow pre-computed shared secret
        }

        /**
         * Sets the self ephemeral public key for cases where the private key is not exportable
         * (e.g., iOS native keychain keys).
         *
         * When using this method, you MUST also call [withPreComputedSharedSecret] to provide
         * the ECDH shared secret that was computed using native platform APIs.
         */
        fun withSelfPublicEphemeralKey(selfEphemeralPublicKey: ResolvedKeyInfoType<*>) = apply {
            this.selfEphemeralPrivateKey = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(selfEphemeralPublicKey)
        }

        fun withRemotePublicEphemeralKey(otherEphemeralPublicKey: ResolvedKeyInfoType<*>) =
            apply { this.remoteEphemeralPublicKey = CoseJoseKeyMappingService.toResolvedCoseKeyInfo(otherEphemeralPublicKey).toResolvedPublicKeyInfo() }

        fun withSessionTranscriptBytes(sessionTranscriptBytes: ByteArray) = apply { this.sessionTranscriptBytes = sessionTranscriptBytes }
        fun withProvider(provider: CryptographyProvider = CryptographyProvider.Default) = apply { this.provider = provider }
        fun withDebugLogger(debugLogger: IMdocDebugLogger?) = apply { this.debugLogger = debugLogger }

        /**
         * Sets a pre-computed ECDH shared secret.
         *
         * Use this when the private key is stored in a native keystore that doesn't export
         * the private key material (e.g., iOS Keychain). The shared secret should be computed
         * using the platform's native ECDH API (e.g., SecKeyCreateSharedSecret on iOS).
         *
         * When this is set, the private key's 'd' parameter is not required.
         */
        fun withPreComputedSharedSecret(sharedSecret: ByteArray) = apply {
            this.preComputedSharedSecret = sharedSecret
        }

            suspend fun build(): SessionEncryption {
            requireNotNull(selfEphemeralPrivateKey) { "selfPrivateKey or selfPublicKey must be set" }
            requireNotNull(remoteEphemeralPublicKey) { "otherPublicKey must be set" }
            requireNotNull(sessionTranscriptBytes) { "sessionTranscript must be set" }

            // Validate that we can perform ECDH - either via private key or pre-computed secret
            val hasPrivateKey = selfEphemeralPrivateKey!!.key.d != null
            val hasPreComputedSecret = preComputedSharedSecret != null

            if (!hasPrivateKey && !hasPreComputedSecret) {
                throw IllegalArgumentException(
                    "Self ephemeral key must have a private part (d parameter) for session establishment, " +
                    "OR a pre-computed shared secret must be provided via withPreComputedSharedSecret(). " +
                    "Key alias: ${selfEphemeralPrivateKey!!.alias ?: "unknown"}, " +
                    "key encoding: ${selfEphemeralPrivateKey!!.keyEncoding}, " +
                    "has x: ${selfEphemeralPrivateKey!!.key.x != null}, " +
                    "has y: ${selfEphemeralPrivateKey!!.key.y != null}, " +
                    "has d: false. " +
                    "If this is an iOS native keychain key, compute the shared secret using " +
                    "SecKeyCreateSharedSecret and pass it via withPreComputedSharedSecret()."
                )
            }

            try {
                val sessionTranscriptEncoded = cborSerializer.decode<CborEncodedItem<SessionTranscript>>(sessionTranscriptBytes!!)
            } catch (cce: ClassCastException) {
                throw cce
            } catch (e: Error) {
                throw e
            }

            // Compute or use pre-computed shared secret
            val sharedSecret = if (hasPreComputedSecret) {
                preComputedSharedSecret!!
            } else {
                val selfRawPrivateKey = toDerEcdhPrivateKey(provider = provider, selfEphemeralPrivateKey!!)
                val remoteRawPublicKey = toDerEcdhPublicKey(provider = provider, remoteEphemeralPublicKey!!)

                // Log the full EDeviceKey being used for ECDH (including private part d)
                // The public part (x,y) MUST match the EDeviceKey in the DeviceEngagement
                debugLogger?.logEDeviceKeyForEcdh(
                    "EDeviceKey being used for ECDH (full key with private d)",
                    selfEphemeralPrivateKey!!.key.encodeCbor()
                )

                selfRawPrivateKey.sharedSecretGenerator().generateSharedSecretToByteArray(remoteRawPublicKey)
            }

            // Log SessionTranscriptBytes (Tag 24 wrapped) - these are the HKDF salt input
            debugLogger?.logSessionTranscriptBytes(
                "HKDF salt input - SessionTranscriptBytes used for session key derivation (selfRole=$selfRole)",
                sessionTranscriptBytes!!
            )

            val salt = hash(sessionTranscriptBytes!!, DigestAlg.SHA256)
            val hkdf = provider.get(HKDF)
            val sessionKeyDevice =
                hkdf.secretDerivation(digest = SHA256, outputSize = 32.bytes, salt = salt, info = "SKDevice".encodeToByteArray()).deriveSecretToByteArray(sharedSecret)
            val sessionKeyReader =
                hkdf.secretDerivation(digest = SHA256, outputSize = 32.bytes, salt = salt, info = "SKReader".encodeToByteArray()).deriveSecretToByteArray(sharedSecret)

            // Log all HKDF parameters and derived keys
            debugLogger?.logSessionKeyDerivation(
                "Session key derivation completed (selfRole=$selfRole)",
                sharedSecretZab = sharedSecret,
                sessionTranscriptBytesHash = salt,
                skDevice = sessionKeyDevice,
                skReader = sessionKeyReader
            )

            return SessionEncryption(
                selfRole = selfRole,
                selfPublicKey = selfEphemeralPrivateKey!!.toResolvedPublicKeyInfo(),
                cryptoProvider = provider,
                // Please keep the logic below invariant. They need to be opposites
                selfRawSessionKey = if (selfRole === MdocRole.MDOC) sessionKeyDevice else sessionKeyReader,
                remoteRawSessionKey = if (selfRole === MdocRole.MDOC) sessionKeyReader else sessionKeyDevice,
            )
        }
    }
}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionEstablishment", exact = true)
data class SessionEstablishment(val encodedReaderKey: CborEncodedItem<CoseKey>, val data: CborByteString, override val original: ByteArray?) :
    CborStructure<SessionEstablishment, CborMap<StringLabel, CborItem<*>>>(CDDL.map, original = original) {
    override fun cborBuilder(): CborBuilder<SessionEstablishment> = cborMapBuilder(this) {
        E_READER_KEY to encodedReaderKey
        DATA to data
    }

    fun getReaderKey(): CoseKey = encodedReaderKey.data()

    fun getRawData() = original!!

    fun copyWith(encodedReaderKey: CborEncodedItem<CoseKey> = this.encodedReaderKey, data: CborByteString = this.data, original: ByteArray? = this.original): SessionEstablishment {
        return SessionEstablishment(encodedReaderKey = encodedReaderKey, data = data, original = original)
    }

    override fun encodeCbor(): ByteArray {
        if (original != null) {
            return original
        }
        return super.encodeCbor()
    }

    override fun toString(): String {
        return "SessionEstablishment(encodedReaderKey=$encodedReaderKey, data=$data, original=${original?.encodeToHex()})"
    }


    companion object Decoder : HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, SessionEstablishment> {

        @JsStatic
        val E_READER_KEY = StringLabel("eReaderKey")

        @JsStatic
        val DATA = StringLabel("data")

        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>): SessionEstablishment {
            val eReaderKeyBytes: CborEncodedItem<CborMap<NumberLabel, CborItem<*>>> = Decoder.E_READER_KEY.required(structure)
            val encodedReaderKey = eReaderKeyBytes.copy(CoseKey.fromCborStructure(eReaderKeyBytes.data()))
            return SessionEstablishment(
                encodedReaderKey = encodedReaderKey, data = Decoder.DATA.required(structure), original = null
            )
        }

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): SessionEstablishment {
            return fromCborStructure(structure).copyWith(original = original)
        }

        override fun decodeCbor(bytes: ByteArray): SessionEstablishment {
            return fromCborStructureWithOriginal(cborSerializer.decode(bytes), original = bytes)
        }
    }
}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionDataJson", exact = true)
data class SessionDataJson(val data: String? = null, val status: Long? = null) : JsonView() {
    override fun toJsonString(): String {
        throw IllegalStateException("Only cbor version can be used for session data!")
    }

    override fun toCbor(): Any {
        throw IllegalStateException("Only cbor version can be used for session data!")
    }
}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionData", exact = true)
data class SessionData(val data: CborByteString? = null, val status: CborUInt? = null, override val original: ByteArray?) :
    CborStructure<SessionData, CborMap<StringLabel, CborItem<*>>>(CDDL.map, original = original) {
    override fun cborBuilder(): CborBuilder<SessionData> = cborMapBuilder(this) {
        optional(DATA, data)
        optional(STATUS, status)
    }

    fun getStatus(): SessionDataStatus? {
        return status?.let { SessionDataStatus.entries.firstOrNull { entry -> entry.status.value == it.value } }
    }

    fun copyWith(data: CborByteString? = this.data, status: CborUInt? = this.status, original: ByteArray? = this.original): SessionData {
        return SessionData(data = data, status = status, original = original)
    }

    override fun toString(): String {
        return "SessionData(data=$data, status=$status, original=${stringify(original)})"
    }


    companion object Decoder : HasFromCborWithOriginal<CborMap<StringLabel, CborItem<*>>, SessionData> {
        val DATA = StringLabel("data")
        val STATUS = StringLabel("status")
        override fun fromCborStructure(structure: CborMap<StringLabel, CborItem<*>>) = SessionData(
            data = DATA.optional(structure), status = STATUS.optional(structure), original = null
        )

        override fun fromCborStructureWithOriginal(structure: CborMap<StringLabel, CborItem<*>>, original: ByteArray?): SessionData {
            return fromCborStructure(structure).copyWith(original = original)
        }

        override fun decodeCbor(bytes: ByteArray) = fromCborStructureWithOriginal(cborSerializer.decode(bytes), bytes)
    }
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionDataStatus", exact = true)
enum class SessionDataStatus(val requiredAction: String, val errorDescription: String, val status: CborUInt) {
    ERROR_SESSION_ENCRYPTION(
        status = CborUInt(10), errorDescription = "Error: session encryption", requiredAction = "The session shall be terminated."
    ),
    ERROR_CBOR_DECODING(
        status = CborUInt(11), errorDescription = "Error: CBOR decoding", requiredAction = "The session shall be terminated."
    ),
    SESSION_TERMINATION(status = CborUInt(20), errorDescription = "Session termination", requiredAction = "The session shall be terminated.");

    fun getCode(): Long = status.value

    fun isError(): Boolean = status.value != 20L

}


/**
 * Interface representing the result of a session decryption operation in CBOR format.
 *
 * @property encryptedSessionEstablishment Contains the CBOR data related to session establishment (only present in the first message).
 *      The data in the establishment is still encrypted. Want to access the unencrypted version, then access the sessionData objects.
 * @property sessionData Contains the CBOR data related to the session itself.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DecryptResult", exact = true)
interface DecryptResult {
    val encryptedSessionEstablishment: SessionEstablishment?
    val sessionData: SessionData
}


private fun ByteStringBuilder.appendCounter(value: UInt) = apply {
    append((value shr 24).and(0xffU).toByte())
    append((value shr 16).and(0xffU).toByte())
    append((value shr 8).and(0xffU).toByte())
    append((value shr 0).and(0xffU).toByte())
}
