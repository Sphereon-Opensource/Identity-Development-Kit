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

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.toCborByteString
import com.sphereon.core.api.decodeFromHex
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.mdoc.logging.IMdocDebugLogger
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transfer.reader.QrHandover
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for SessionEncryption class.
 *
 * Uses real P-256 keys from RDW test logs to test actual encryption/decryption operations.
 * The holder key pair is a valid mathematically correct P-256 key pair.
 * The reader public key is valid (from RDW logs), but we don't have its private key,
 * so tests that need a reader private key use pre-computed shared secrets.
 */
class SessionEncryptionTest {

    // Valid holder P-256 key pair from RDW logs (2025-11-27)
    private val holderPrivateD = "ea2e5e5d8bd2e7979a6bd21c4a768fe337fcd3b00b1d4bd26a1fd5a1c7902eb4".decodeFromHex()
    private val holderPublicX = "4690ca5b6db21133b30e4c821661f7f9ccd2cebd69a0f6096e560fdd86d7e064".decodeFromHex()
    private val holderPublicY = "bf92d1a5b1a30afbc50daf52918b8235e8dea3ce558c5323b81e90130c3b86c5".decodeFromHex()

    // Reader public key from RDW logs (private key not available)
    private val readerPublicX = "cf347b9f5de29542816918669c00296a028f8aa9f5a8e7c491643f4ca56ae546".decodeFromHex()
    private val readerPublicY = "5ce995b03248d52c09552112ef024b25234e048c956f528c3c107058f1b4a080".decodeFromHex()

    // Pre-computed shared secret from RDW logs (ECDH(holder, reader))
    private val preComputedSharedSecret = "8a10dbaf5f0704b46a91bb09bfed329b22aab4572d736118cb6950adf28ecdc5".decodeFromHex()

    /**
     * Create properly formatted SessionTranscript bytes (Tag 24 wrapped).
     * Per ISO 18013-5, SessionTranscript must be CBOR-encoded with Tag 24.
     */
    @Suppress("UNCHECKED_CAST")
    private fun createSessionTranscriptBytes(holderKey: CoseKey, readerKey: CoseKey): ByteArray {
        val eReaderKeyEncoded = CborEncodedItem.fromData(readerKey)

        val sessionTranscript = SessionTranscript(
            deviceEngagement = null,
            eReaderKey = eReaderKeyEncoded,
            handover = QrHandover() as Handover<*, CborItem<*>>,
            original = null
        )

        return sessionTranscript.toCborEncodedItem().encodeCbor()
    }

    private fun createHolderKey(): CoseKey {
        return CoseKey(
            generateKid = false,
            kty = KeyTypeMapping.EC.cose.toCbor(),
            crv = Curve.P_256.cose.toCbor(),
            d = holderPrivateD.toCborByteString(),
            x = holderPublicX.toCborByteString(),
            y = holderPublicY.toCborByteString()
        )
    }

    private fun createHolderPublicKey(): CoseKey {
        return CoseKey(
            generateKid = false,
            kty = KeyTypeMapping.EC.cose.toCbor(),
            crv = Curve.P_256.cose.toCbor(),
            x = holderPublicX.toCborByteString(),
            y = holderPublicY.toCborByteString()
        )
    }

    private fun createReaderPublicKey(): CoseKey {
        return CoseKey(
            generateKid = false,
            kty = KeyTypeMapping.EC.cose.toCbor(),
            crv = Curve.P_256.cose.toCbor(),
            x = readerPublicX.toCborByteString(),
            y = readerPublicY.toCborByteString()
        )
    }

    @Test
    fun testBuildSessionEncryptionAsMdoc() = runTest {
        val holderKey = createHolderKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

        assertNotNull(sessionEncryption)
        assertEquals(MdocRole.MDOC, sessionEncryption.selfRole)
        assertNotNull(sessionEncryption.selfPublicKey)
        assertNotNull(sessionEncryption.selfRawSessionKey)
        assertNotNull(sessionEncryption.remoteRawSessionKey)
    }

    @Test
    fun testBuildSessionEncryptionAsMdocReaderWithPreComputedSecret() = runTest {
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderPublicKey, readerPublicKey)

        // Use pre-computed shared secret since we don't have reader's private key
        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC_READER)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(preComputedSharedSecret)
            .build()

        assertNotNull(sessionEncryption)
        assertEquals(MdocRole.MDOC_READER, sessionEncryption.selfRole)
    }

    @Test
    fun testBuildWithPreComputedSharedSecret() = runTest {
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderPublicKey, readerPublicKey)

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(preComputedSharedSecret)
            .build()

        assertNotNull(sessionEncryption)
        assertEquals(MdocRole.MDOC, sessionEncryption.selfRole)
    }

    @Test
    fun testSessionEncryptionEncrypt() = runTest {
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderPublicKey, readerPublicKey)

        // Use unique shared secret to avoid IV reuse with same key across tests
        val uniqueSharedSecret = ByteArray(32) { (it * 11 + 1).toByte() }

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(uniqueSharedSecret)
            .build()

        val plainText = "Hello, World!".encodeToByteArray()
        val encrypted = sessionEncryption.encrypt(plainText)

        assertNotNull(encrypted)
        assertTrue(encrypted.isNotEmpty())
        assertFalse(encrypted.contentEquals(plainText))
    }

    @Test
    fun testSessionEncryptionEncryptWithStatus() = runTest {
        val holderKey = createHolderKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

        val plainText = "Test data".encodeToByteArray()
        val encrypted = sessionEncryption.encrypt(plainText, status = 20L)

        assertNotNull(encrypted)
        assertTrue(encrypted.isNotEmpty())
    }

    @Test
    fun testSessionEncryptionEncryptAsSessionData() = runTest {
        val holderKey = createHolderKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

        val plainText = "Session data test".encodeToByteArray()
        val sessionData = sessionEncryption.encryptAsSessionData(plainText)

        assertNotNull(sessionData)
        assertNotNull(sessionData.data)
    }

    @Test
    fun testSessionEncryptionEncryptAsSessionDataWithTermination() = runTest {
        val holderKey = createHolderKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

        val sessionData = sessionEncryption.encryptAsSessionData(null, SessionDataStatus.SESSION_TERMINATION.getCode())

        assertNotNull(sessionData)
        assertEquals(SessionDataStatus.SESSION_TERMINATION, sessionData.getStatus())
    }

    @Test
    fun testMdocReaderEncryptAsSessionEstablishment() = runTest {
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderPublicKey, readerPublicKey)

        // Use pre-computed shared secret for reader (no private key available)
        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC_READER)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(preComputedSharedSecret)
            .build()

        val plainText = "Initial request".encodeToByteArray()
        val sessionEstablishment = sessionEncryption.encryptAsSessionEstablishment(plainText)

        assertNotNull(sessionEstablishment)
        assertNotNull(sessionEstablishment.data)
        assertNotNull(sessionEstablishment.encodedReaderKey)
    }

    @Test
    fun testEncryptAsSessionEstablishmentFailsForMdoc() = runTest {
        val holderKey = createHolderKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

        val plainText = "Data".encodeToByteArray()

        assertFailsWith<IllegalStateException> {
            sessionEncryption.encryptAsSessionEstablishment(plainText)
        }
    }

    @Test
    fun testRoundTripEncryptDecrypt() = runTest {
        val holderKey = createHolderKey()
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        // Reader session with pre-computed shared secret
        val readerSession = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC_READER)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(preComputedSharedSecret)
            .build()

        // Holder session with actual ECDH
        val holderSession = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

        // Reader sends session establishment
        val originalMessage = "Request for mDL data".encodeToByteArray()
        val encryptedFromReader = readerSession.encryptAsSessionEstablishment(originalMessage)

        // Holder decrypts
        val decryptedAtHolder = holderSession.decryptSessionEstablishment(encryptedFromReader.encodeCbor())
        assertNotNull(decryptedAtHolder)

        // Holder sends response
        val responseMessage = "Here is my mDL data".encodeToByteArray()
        val encryptedFromHolder = holderSession.encrypt(responseMessage)

        // Reader decrypts
        val decryptedAtReader = readerSession.decrypt(encryptedFromHolder)
        assertNotNull(decryptedAtReader.sessionData)
        assertNotNull(decryptedAtReader.sessionData.data)

        val decryptedBytes = decryptedAtReader.sessionData.data!!.value
        assertTrue(decryptedBytes.contentEquals(responseMessage))
    }

    @Test
    fun testSessionEncryptionProperties() = runTest {
        // Test properties directly instead of toString() because encodedReaderKey is lateinit
        val holderKey = createHolderKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

        assertEquals(MdocRole.MDOC, sessionEncryption.selfRole)
        assertNotNull(sessionEncryption.selfPublicKey)
        assertNotNull(sessionEncryption.cryptoProvider)
        assertNotNull(sessionEncryption.selfRawSessionKey)
        assertNotNull(sessionEncryption.remoteRawSessionKey)
    }

    @Test
    fun testSessionEncryptionCryptoProvider() = runTest {
        val holderKey = createHolderKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        val customProvider = CryptographyProvider.Default

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withProvider(customProvider)
            .build()

        assertNotNull(sessionEncryption)
        assertEquals(customProvider, sessionEncryption.cryptoProvider)
    }

    @Test
    fun testMultipleEncryptions() = runTest {
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderPublicKey, readerPublicKey)

        // Use a unique shared secret for this test to avoid GCM IV reuse issues across tests
        val uniqueSharedSecret = ByteArray(32) { (it * 7 + 42).toByte() }

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(uniqueSharedSecret)
            .build()

        val plainText = "Same message".encodeToByteArray()
        val encrypted1 = sessionEncryption.encrypt(plainText)
        val encrypted2 = sessionEncryption.encrypt(plainText)

        assertNotNull(encrypted1)
        assertNotNull(encrypted2)
        // Different encryptions should produce different ciphertext (different IV from counter)
        assertFalse(encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun testDecryptSessionData() = runTest {
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderPublicKey, readerPublicKey)

        // Use unique shared secret to avoid IV reuse with same key across tests
        val uniqueSharedSecret = ByteArray(32) { (it * 19 + 5).toByte() }

        // Reader session with pre-computed shared secret
        val readerSession = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC_READER)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(uniqueSharedSecret)
            .build()

        // Holder session with the same shared secret
        val holderSession = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(uniqueSharedSecret)
            .build()

        // Reader sends session establishment first
        val establishment = readerSession.encryptAsSessionEstablishment("Initial request".encodeToByteArray())

        // Holder decrypts establishment
        val decrypted = holderSession.decrypt(establishment.encodeCbor())
        assertNotNull(decrypted.sessionData)
    }

    @Test
    fun testEncryptStatusOnlyMessage() = runTest {
        val holderKey = createHolderKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

        // First do a normal encrypt to increment counter (no longer session establishment)
        sessionEncryption.encrypt("First message".encodeToByteArray())

        // Now encrypt with null plaintext and status - should create status-only message
        val encrypted = sessionEncryption.encrypt(null, 20L)

        assertNotNull(encrypted)
        val decoded = SessionData.decodeCbor(encrypted)
        assertNull(decoded.data)
        assertEquals(SessionDataStatus.SESSION_TERMINATION, decoded.getStatus())
    }

    @Test
    fun testDecryptSessionDataThrowsForSessionEstablishment() = runTest {
        val holderKey = createHolderKey()
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        // Reader session
        val readerSession = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC_READER)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(preComputedSharedSecret)
            .build()

        // Holder session
        val holderSession = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .build()

        // Reader sends session establishment
        val establishment = readerSession.encryptAsSessionEstablishment("Initial request".encodeToByteArray())

        // Holder tries to decrypt as session data - should succeed since it still returns sessionData
        val result = holderSession.decryptSessionData(establishment.encodeCbor())
        assertNotNull(result)
    }

    @Test
    fun testDecryptSessionEstablishmentThrowsWhenNoEstablishment() = runTest {
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderPublicKey, readerPublicKey)

        // Setup unique shared secret to avoid IV reuse
        val uniqueSharedSecret = ByteArray(32) { (it * 13 + 7).toByte() }

        // Reader session - starts fresh, has NOT received any session establishment
        val readerSession = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC_READER)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(uniqueSharedSecret)
            .build()

        // Create a SessionData message (not a SessionEstablishment) directly
        val sessionData = SessionData(
            data = null,
            status = CborUInt(20L),
            original = null
        )

        // Reader tries to decrypt as session establishment - should throw because
        // the message doesn't contain eReaderKey and encodedReaderKey is not initialized
        assertFailsWith<IllegalStateException> {
            readerSession.decryptSessionEstablishment(sessionData.encodeCbor())
        }
    }

    @Test
    fun testDecryptStatusOnlyMessage() = runTest {
        val holderKey = createHolderKey()
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        // Setup unique shared secret
        val uniqueSharedSecret = ByteArray(32) { (it * 17 + 3).toByte() }

        // Reader session
        val readerSession = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC_READER)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(uniqueSharedSecret)
            .build()

        // Holder session
        val holderSession = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(uniqueSharedSecret)
            .build()

        // Reader sends session establishment first
        val establishment = readerSession.encryptAsSessionEstablishment("Init".encodeToByteArray())
        holderSession.decrypt(establishment.encodeCbor())

        // Holder responds with data
        val response = holderSession.encrypt("Response".encodeToByteArray())
        readerSession.decrypt(response)

        // Holder sends status-only termination message
        val termination = holderSession.encrypt(null, SessionDataStatus.SESSION_TERMINATION.getCode())

        // Reader decrypts status-only message
        val decrypted = readerSession.decrypt(termination)
        assertNotNull(decrypted.sessionData)
        assertEquals(SessionDataStatus.SESSION_TERMINATION, decrypted.sessionData.getStatus())
    }

    @Test
    fun testSessionEncryptionToStringAfterEstablishment() = runTest {
        val holderPublicKey = createHolderPublicKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderPublicKey, readerPublicKey)

        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC_READER)
            .withSelfPublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(holderPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withPreComputedSharedSecret(preComputedSharedSecret)
            .build()

        // Trigger initialization of encodedReaderKey by encrypting session establishment
        sessionEncryption.encryptAsSessionEstablishment("Test".encodeToByteArray())

        // Now toString should work without UninitializedPropertyAccessException
        val str = sessionEncryption.toString()
        assertTrue(str.contains("SessionEncryption"))
        assertTrue(str.contains("selfRole"))
    }

    @Test
    fun testBuildWithDebugLogger() = runTest {
        val holderKey = createHolderKey()
        val readerPublicKey = createReaderPublicKey()
        val sessionTranscriptBytes = createSessionTranscriptBytes(holderKey, readerPublicKey)

        // Create a simple mock debug logger that records calls
        val loggedCalls = mutableListOf<String>()
        val debugLogger = object : IMdocDebugLogger {
            override var enabled: Boolean = true
            override fun logDeviceEngagement(description: String, bytes: ByteArray) { loggedCalls.add("logDeviceEngagement") }
            override fun logDeviceEngagementBytes(description: String, bytes: ByteArray) { loggedCalls.add("logDeviceEngagementBytes") }
            override fun logEDeviceKey(description: String, bytes: ByteArray) { loggedCalls.add("logEDeviceKey") }
            override fun logEDeviceKeyForEcdh(description: String, bytes: ByteArray) { loggedCalls.add("logEDeviceKeyForEcdh") }
            override fun logQrCodeData(description: String, data: String) { loggedCalls.add("logQrCodeData") }
            override fun logSessionEstablishment(description: String, bytes: ByteArray) { loggedCalls.add("logSessionEstablishment") }
            override fun logEReaderKey(description: String, bytes: ByteArray) { loggedCalls.add("logEReaderKey") }
            override fun logEReaderKeyBytes(description: String, bytes: ByteArray) { loggedCalls.add("logEReaderKeyBytes") }
            override fun logSessionTranscript(description: String, bytes: ByteArray) { loggedCalls.add("logSessionTranscript") }
            override fun logSessionTranscriptBytes(description: String, bytes: ByteArray) { loggedCalls.add("logSessionTranscriptBytes") }
            override fun logHandover(description: String, bytes: ByteArray?) { loggedCalls.add("logHandover") }
            override fun logSessionKeyDerivation(
                description: String,
                sharedSecretZab: ByteArray,
                sessionTranscriptBytesHash: ByteArray,
                skDevice: ByteArray,
                skReader: ByteArray
            ) { loggedCalls.add("logSessionKeyDerivation") }
            override fun logDeviceRequest(description: String, bytes: ByteArray) { loggedCalls.add("logDeviceRequest") }
            override fun logDeviceResponse(description: String, bytes: ByteArray) { loggedCalls.add("logDeviceResponse") }
            override fun logSessionData(description: String, bytes: ByteArray, isEncrypted: Boolean) { loggedCalls.add("logSessionData") }
            override fun logBleSend(characteristicName: String, bytes: ByteArray) { loggedCalls.add("logBleSend") }
            override fun logBleReceive(characteristicName: String, bytes: ByteArray) { loggedCalls.add("logBleReceive") }
            override fun logBleConfig(description: String, serviceUuid: String, characteristics: Map<String, String>) { loggedCalls.add("logBleConfig") }
            override fun logBytes(eventType: String, description: String, bytes: ByteArray) { loggedCalls.add("logBytes") }
            override fun logText(eventType: String, description: String, text: String) { loggedCalls.add("logText") }
            override fun logEvent(eventType: String, description: String) { loggedCalls.add("logEvent") }
        }

        // Build with actual ECDH (not precomputed) to trigger debug logging
        val sessionEncryption = SessionEncryption.Builder()
            .withSelfRole(MdocRole.MDOC)
            .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(holderKey))
            .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(readerPublicKey))
            .withSessionTranscriptBytes(sessionTranscriptBytes)
            .withDebugLogger(debugLogger)
            .build()

        assertNotNull(sessionEncryption)
        // Verify that the debug logger was called during build
        assertTrue(loggedCalls.contains("logEDeviceKeyForEcdh"))
        assertTrue(loggedCalls.contains("logSessionTranscriptBytes"))
        assertTrue(loggedCalls.contains("logSessionKeyDerivation"))
    }
}
