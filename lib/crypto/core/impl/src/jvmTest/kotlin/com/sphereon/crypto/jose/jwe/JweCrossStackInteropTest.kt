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

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDHDecrypter
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.crypto.RSADecrypter
import com.nimbusds.jose.crypto.RSAEncrypter
import com.nimbusds.jose.jwk.Curve
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsJwk
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-stack JWE interop probes against [nimbus-jose-jwt](https://connect2id.com/products/nimbus-jose-jwt).
 *
 * The IDK ships a JWE implementation atop cryptography-kotlin (which on the JVM dispatches to
 * the JCA provider configured by `dev.whyoleg.cryptography:cryptography-provider-jdk`). RFC 7516
 * fixes the wire format, but it does not fix every parameter choice (for example, OAEP MGF1 hash
 * algorithm, AES-GCM IV/tag lengths, EPK encoding for ECDH-ES). A bug in any of those choices
 * would make IDK-emitted JWEs unconsumable by independent JOSE clients (mobile wallets, OIDC
 * verifiers running spring-security/connect2id) and vice versa.
 *
 * These tests round-trip plaintext between IDK and nimbus in both directions, exercising the
 * algorithms IDK uses for OID4VCI credential offer encryption, OID4VP encrypted authorization
 * responses, JARM, and encrypted JARs:
 *
 *  - RSA-OAEP-256 + A256GCM (the JARM and OID4VP/OID4VCI default)
 *  - RSA-OAEP + A256GCM (RFC 7516 Appendix A.1 baseline)
 *  - ECDH-ES + A256GCM with P-256 (the IETF-recommended EC variant)
 *
 * RSA1_5 is intentionally omitted: it is deprecated by RFC 7518 and the IETF JOSE WG, and
 * cryptography-kotlin's JDK provider does not advertise it as a stable surface.
 */
class JweCrossStackInteropTest {
    private lateinit var jweService: JweService

    private val app = createCryptoTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("jwe-cross-stack-interop", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "jwe-cross-stack-interop-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider =
            app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        val keyManagerService: KeyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jweService = (session.graph as JweServiceImpl.Graph).jweService
    }

    // -------------------------------------------------------------------------
    // RSA-OAEP-256 + A256GCM (the JARM / OID4VP / OID4VCI default)
    // -------------------------------------------------------------------------

    @Test
    fun idkEncryptDecryptViaNimbus_RsaOaep256_A256GCM() =
        runTest {
            val keyPair = generateRsaKeyPair()
            val recipientPublicJwk = rsaPublicJwk(keyPair, kid = "interop-rsa-oaep-256-a256gcm")
            val plaintext = """{"iss":"https://idk.test","iat":1234567890,"data":"cross-stack-canary"}"""

            val jweCompact =
                idkEncrypt(
                    plaintext = plaintext.encodeToByteArray(),
                    recipientPublicJwk = recipientPublicJwk,
                    keyEncryptionAlg = "RSA-OAEP-256",
                    contentEncryptionAlg = "A256GCM",
                )

            // Sanity-check IDK's wire-format header before handing to nimbus.
            assertHeaderShape(jweCompact, expectedAlg = "RSA-OAEP-256", expectedEnc = "A256GCM")

            val parsed = JWEObject.parse(jweCompact.serialize())
            parsed.decrypt(RSADecrypter(keyPair.private as RSAPrivateCrtKey))
            assertEquals(
                plaintext,
                parsed.payload.toString(),
                "nimbus-jose-jwt MUST decrypt IDK-emitted RSA-OAEP-256+A256GCM JWEs to the original plaintext",
            )
        }

    @Test
    fun nimbusEncryptDecryptViaIdk_RsaOaep256_A256GCM() =
        runTest {
            val keyPair = generateRsaKeyPair()
            val privateJwk = rsaPrivateJwk(keyPair, kid = "interop-rsa-oaep-256-a256gcm")
            val plaintext = """{"iss":"https://nimbus.test","iat":1234567891,"data":"cross-stack-canary"}"""

            val nimbusHeader =
                JWEHeader
                    .Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A256GCM)
                    .type(JOSEObjectType.JWT)
                    .keyID("interop-rsa-oaep-256-a256gcm")
                    .build()
            val nimbusJwe = JWEObject(nimbusHeader, Payload(plaintext))
            nimbusJwe.encrypt(RSAEncrypter(keyPair.public as RSAPublicKey))
            val serialized = nimbusJwe.serialize()

            val plaintextOut = idkDecrypt(serialized, privateJwk)
            assertEquals(
                plaintext,
                plaintextOut,
                "IDK MUST decrypt nimbus-emitted RSA-OAEP-256+A256GCM JWEs to the original plaintext",
            )
        }

    // -------------------------------------------------------------------------
    // RSA-OAEP + A256GCM (RFC 7516 Appendix A.1 baseline; SHA-1 MGF1 vs SHA-256 trap)
    // -------------------------------------------------------------------------

    @Test
    fun idkEncryptDecryptViaNimbus_RsaOaep_A256GCM() =
        runTest {
            val keyPair = generateRsaKeyPair()
            val recipientPublicJwk = rsaPublicJwk(keyPair, kid = "interop-rsa-oaep-a256gcm").copy(alg = JwaAlgorithm.fromValue("RSA-OAEP"))
            val plaintext = "RFC 7516 Appendix A.1 round-trip"

            val jweCompact =
                idkEncrypt(
                    plaintext = plaintext.encodeToByteArray(),
                    recipientPublicJwk = recipientPublicJwk,
                    keyEncryptionAlg = "RSA-OAEP",
                    contentEncryptionAlg = "A256GCM",
                )
            assertHeaderShape(jweCompact, expectedAlg = "RSA-OAEP", expectedEnc = "A256GCM")

            val parsed = JWEObject.parse(jweCompact.serialize())
            parsed.decrypt(RSADecrypter(keyPair.private as RSAPrivateCrtKey))
            assertEquals(
                plaintext,
                parsed.payload.toString(),
                "nimbus MUST decrypt IDK-emitted RSA-OAEP+A256GCM JWEs (catches MGF1 SHA-1 vs SHA-256 mismatches)",
            )
        }

    @Test
    fun nimbusEncryptDecryptViaIdk_RsaOaep_A256GCM() =
        runTest {
            val keyPair = generateRsaKeyPair()
            val privateJwk = rsaPrivateJwk(keyPair, kid = "interop-rsa-oaep-a256gcm").copy(alg = JwaAlgorithm.fromValue("RSA-OAEP"))
            val plaintext = "RFC 7516 Appendix A.1 round-trip (nimbus->IDK)"

            val nimbusHeader =
                JWEHeader
                    .Builder(JWEAlgorithm.RSA_OAEP, EncryptionMethod.A256GCM)
                    .keyID("interop-rsa-oaep-a256gcm")
                    .build()
            val nimbusJwe = JWEObject(nimbusHeader, Payload(plaintext))
            nimbusJwe.encrypt(RSAEncrypter(keyPair.public as RSAPublicKey))

            val plaintextOut = idkDecrypt(nimbusJwe.serialize(), privateJwk)
            assertEquals(plaintext, plaintextOut, "IDK MUST decrypt nimbus-emitted RSA-OAEP+A256GCM JWEs")
        }

    // -------------------------------------------------------------------------
    // ECDH-ES + A256GCM with P-256 (IETF-recommended EC variant)
    // -------------------------------------------------------------------------

    @Test
    fun idkEncryptDecryptViaNimbus_EcdhEs_A256GCM_P256() =
        runTest {
            val keyPair = generateEcKeyPair("secp256r1")
            val publicJwk = ecPublicJwk(keyPair, kid = "interop-ecdh-es-p256", alg = "ECDH-ES")
            val plaintext = """{"iss":"https://idk.test","alg":"ECDH-ES","enc":"A256GCM"}"""

            val jweCompact =
                idkEncrypt(
                    plaintext = plaintext.encodeToByteArray(),
                    recipientPublicJwk = publicJwk,
                    keyEncryptionAlg = "ECDH-ES",
                    contentEncryptionAlg = "A256GCM",
                )
            assertHeaderShape(jweCompact, expectedAlg = "ECDH-ES", expectedEnc = "A256GCM")
            // For ECDH-ES (direct key agreement) the encrypted-key segment MUST be empty per RFC 7518 §4.6.
            assertEquals(
                0,
                jweCompact.encryptedKey.size,
                "ECDH-ES direct key agreement: encrypted_key MUST be empty (RFC 7518 §4.6); got ${jweCompact.encryptedKey.size} bytes",
            )

            val parsed = JWEObject.parse(jweCompact.serialize())
            assertNotNull(parsed.header.ephemeralPublicKey, "IDK MUST set the epk header (ephemeral public key) for ECDH-ES")
            parsed.decrypt(ECDHDecrypter(keyPair.private as ECPrivateKey))
            assertEquals(
                plaintext,
                parsed.payload.toString(),
                "nimbus MUST decrypt IDK-emitted ECDH-ES+A256GCM JWEs (catches epk encoding bugs)",
            )
        }

    @Test
    fun nimbusEncryptDecryptViaIdk_EcdhEs_A256GCM_P256() =
        runTest {
            val keyPair = generateEcKeyPair("secp256r1")
            val privateJwk = ecPrivateJwk(keyPair, kid = "interop-ecdh-es-p256", alg = "ECDH-ES")
            val plaintext = """{"iss":"https://nimbus.test","alg":"ECDH-ES","enc":"A256GCM"}"""

            val nimbusEcKey =
                com.nimbusds.jose.jwk.ECKey
                    .Builder(Curve.P_256, keyPair.public as ECPublicKey)
                    .keyID("interop-ecdh-es-p256")
                    .build()
            val nimbusHeader =
                JWEHeader
                    .Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                    .keyID("interop-ecdh-es-p256")
                    .build()
            val nimbusJwe = JWEObject(nimbusHeader, Payload(plaintext))
            nimbusJwe.encrypt(ECDHEncrypter(nimbusEcKey.toECPublicKey()))

            val plaintextOut = idkDecrypt(nimbusJwe.serialize(), privateJwk)
            assertEquals(plaintext, plaintextOut, "IDK MUST decrypt nimbus-emitted ECDH-ES+A256GCM JWEs")
        }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun idkEncrypt(
        plaintext: ByteArray,
        recipientPublicJwk: Jwk,
        keyEncryptionAlg: String,
        contentEncryptionAlg: String,
    ): JweCompact {
        val recipient = ManagedOptsJwk(identifier = recipientPublicJwk, context = ctx())
        val prepared =
            jweService.prepareJwe(
                PrepareJweArgs(
                    plaintext = plaintext,
                    recipient = recipient,
                    keyEncryptionAlg = keyEncryptionAlg,
                    contentEncryptionAlg = contentEncryptionAlg,
                ),
            )
        assertTrue(prepared.isOk, "idk prepare must succeed: ${if (prepared.isErr) prepared.error.message.defaultMessage else ""}")
        val created = jweService.createJweCompact(CreateJweCompactArgs(preparedJwe = prepared.value))
        assertTrue(created.isOk, "idk create must succeed: ${if (created.isErr) created.error.message.defaultMessage else ""}")
        return created.value
    }

    private suspend fun idkDecrypt(
        compactJwe: String,
        privateJwk: Jwk,
    ): String {
        val parsed = JweCompact.parse(compactJwe)
        val result =
            jweService.decryptJwe(
                DecryptJweArgs(
                    jwe = parsed,
                    decryptor = ManagedOptsJwk(identifier = privateJwk, context = ctx()),
                ),
            )
        assertTrue(
            result.isOk,
            "idk decrypt must succeed: ${if (result.isErr) result.error.message.defaultMessage else ""}",
        )
        val plaintext = result.value.plaintext ?: error("idk decrypt returned null plaintext")
        return plaintext.decodeToString()
    }

    private fun ctx(): IdentifierContext =
        IdentifierContext(
            clientId = "cross-stack-test",
            clientIdScheme = "jwk",
            issuer = "https://cross-stack.test",
        )

    private fun assertHeaderShape(
        jwe: JweCompact,
        expectedAlg: String,
        expectedEnc: String,
    ) {
        assertEquals(expectedAlg, jwe.header.alg, "alg in protected header must equal '$expectedAlg'")
        assertEquals(expectedEnc, jwe.header.enc, "enc in protected header must equal '$expectedEnc'")
        // Per RFC 7516 §4.1.1 the AEAD authentication tag size for A*GCM is 128 bits = 16 bytes;
        // an off-by-one length here would surface as a nimbus tag-mismatch failure.
        if (expectedEnc.endsWith("GCM")) {
            assertEquals(16, jwe.authTag.size, "AES-GCM auth tag MUST be 16 bytes per RFC 7518 §5.3")
            assertEquals(12, jwe.iv.size, "AES-GCM IV MUST be 12 bytes per RFC 7518 §5.3")
        }
    }

    private fun generateRsaKeyPair(): KeyPair =
        KeyPairGenerator
            .getInstance("RSA")
            .apply { initialize(RSA_2048, SecureRandom()) }
            .generateKeyPair()

    private fun generateEcKeyPair(curveName: String): KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec(curveName), SecureRandom()) }
            .generateKeyPair()

    private fun rsaPublicJwk(
        keyPair: KeyPair,
        kid: String,
    ): Jwk {
        val pub = keyPair.public as RSAPublicKey
        return Jwk(
            kty = JwaKeyType.RSA,
            alg = JwaAlgorithm.fromValue("RSA-OAEP-256"),
            use = "enc",
            kid = kid,
            n = b64Url(stripLeadingZero(pub.modulus.toByteArray())),
            e = b64Url(stripLeadingZero(pub.publicExponent.toByteArray())),
        )
    }

    private fun rsaPrivateJwk(
        keyPair: KeyPair,
        kid: String,
    ): Jwk {
        val pub = keyPair.public as RSAPublicKey
        val crt = keyPair.private as RSAPrivateCrtKey
        return Jwk(
            kty = JwaKeyType.RSA,
            alg = JwaAlgorithm.fromValue("RSA-OAEP-256"),
            use = "enc",
            kid = kid,
            n = b64Url(stripLeadingZero(pub.modulus.toByteArray())),
            e = b64Url(stripLeadingZero(pub.publicExponent.toByteArray())),
            d = b64Url(stripLeadingZero(crt.privateExponent.toByteArray())),
            p = b64Url(stripLeadingZero(crt.primeP.toByteArray())),
            q = b64Url(stripLeadingZero(crt.primeQ.toByteArray())),
            dP = b64Url(stripLeadingZero(crt.primeExponentP.toByteArray())),
            dQ = b64Url(stripLeadingZero(crt.primeExponentQ.toByteArray())),
            qInv = b64Url(stripLeadingZero(crt.crtCoefficient.toByteArray())),
        )
    }

    private fun ecPublicJwk(
        keyPair: KeyPair,
        kid: String,
        alg: String,
    ): Jwk {
        val pub = keyPair.public as ECPublicKey
        val coordinateLength = (pub.params.curve.field.fieldSize + 7) / 8
        val x = leftPad(stripLeadingZero(pub.w.affineX.toByteArray()), coordinateLength)
        val y = leftPad(stripLeadingZero(pub.w.affineY.toByteArray()), coordinateLength)
        return Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.fromValue("P-256"),
            alg = JwaAlgorithm.fromValue(alg),
            use = "enc",
            kid = kid,
            x = b64Url(x),
            y = b64Url(y),
        )
    }

    private fun ecPrivateJwk(
        keyPair: KeyPair,
        kid: String,
        alg: String,
    ): Jwk {
        val pub = keyPair.public as ECPublicKey
        val priv = keyPair.private as ECPrivateKey
        val coordinateLength = (pub.params.curve.field.fieldSize + 7) / 8
        val x = leftPad(stripLeadingZero(pub.w.affineX.toByteArray()), coordinateLength)
        val y = leftPad(stripLeadingZero(pub.w.affineY.toByteArray()), coordinateLength)
        val d = leftPad(stripLeadingZero(priv.s.toByteArray()), coordinateLength)
        return Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.fromValue("P-256"),
            alg = JwaAlgorithm.fromValue(alg),
            use = "enc",
            kid = kid,
            x = b64Url(x),
            y = b64Url(y),
            d = b64Url(d),
        )
    }

    private fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }

    /**
     * Left-pad an unsigned big-endian integer to a fixed byte length. EC JWK x/y/d coordinates
     * MUST be exactly `ceil(curveBitSize / 8)` bytes per RFC 7518 §6.2.1.2 / §6.2.2.1; missing
     * leading zero bytes from BigInteger.toByteArray() would otherwise produce shorter encodings
     * that some libraries reject as malformed.
     */
    private fun leftPad(
        bytes: ByteArray,
        targetLength: Int,
    ): ByteArray {
        if (bytes.size >= targetLength) {
            return bytes
        }
        val padded = ByteArray(targetLength)
        System.arraycopy(bytes, 0, padded, targetLength - bytes.size, bytes.size)
        return padded
    }

    private fun b64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        private const val RSA_2048 = 2048
    }
}
