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

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsJwk
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Crypto-correctness probes for [JweService.decryptJwe] under wrong-key conditions.
 *
 * Background: a JARM-encryption test in tests/oidf/conformance/oidc/op observed that supplying
 * an unrelated RSA-2048 private key as `decryptor` to a JWE encrypted under a DIFFERENT public
 * key produced [com.sphereon.core.api.IdkResult.Ok] with the EXACT correct plaintext on the JVM
 * cryptography-kotlin RSA-OAEP-256 path. That is mathematically impossible under proper RSA-OAEP
 * unwrap + AES-GCM tag verification.
 *
 * Reproduction strategy: encrypt with a KMS-managed RSA key (the recipient), then attempt to
 * decrypt with an UNRELATED [ManagedOptsJwk] holding a freshly generated, NEVER-stored RSA-2048
 * private JWK. The supplied JWK never reaches the cipher only if the resolver discards it; the
 * matching private key is reachable inside the KMS keystore by `kid`. If the resolver falls back
 * to the KMS-stored matching key when the supplied JWK can't be looked up, the call would
 * silently succeed.
 *
 * Variants exercised: same alg/enc the JARM test used (RSA-OAEP-256 + A256GCM), plus
 * RSA-OAEP + A128GCM, and a sequential same-process call ordering to flush any per-test
 * caching artefacts.
 */
class JweWrongKeyDecryptionTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jweService: JweService

    private val app = createCryptoTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session = context.sessionContextManager.createOrGetFromId("jwe-wrong-key-test")

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "jwe-wrong-key-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider =
            app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jweService = (session.graph as JweServiceImpl.Graph).jweService
    }

    @Test
    fun decryptingWithUnrelatedRsaPrivateKeyMustFail_RsaOaep256_A256GCM() =
        runTest {
            val recipientKey =
                keyManagerService
                    .generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
                    .joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val recipient =
                ManagedOptsKeyInfo(
                    identifier = recipientKey,
                    context = identifierContext(),
                )
            val matchingDecryptor =
                ManagedOptsKeyInfo(
                    identifier = recipientKey,
                    context = identifierContext(),
                )
            val unrelatedJwk = generateExternalRsaPrivateJwk(kid = "unrelated-1")

            val jweCompact = encryptToRecipient(recipient, alg = "RSA-OAEP-256", enc = "A256GCM")

            val resultMatching =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = matchingDecryptor,
                    ),
                )
            assertTrue(
                resultMatching.isOk,
                "sanity: decryption with the MATCHING KMS key MUST succeed; got error " +
                    "${if (resultMatching.isErr) resultMatching.error.message.defaultMessage else "<none>"}",
            )

            val resultWrong =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = ManagedOptsJwk(identifier = unrelatedJwk),
                    ),
                )
            assertTrue(
                resultWrong.isErr,
                "CRITICAL: RSA-OAEP-256+A256GCM decryption with an UNRELATED RSA-2048 private JWK " +
                    "MUST fail; instead got Ok with plaintext '${
                        if (resultWrong.isOk) resultWrong.value.plaintext?.decodeToString() else "<n/a>"
                    }'",
            )
        }

    @Test
    fun decryptingWithUnrelatedRsaPrivateKeyMustFail_RsaOaep_A128GCM() =
        runTest {
            val recipientKey =
                keyManagerService
                    .generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
                    .joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val recipient =
                ManagedOptsKeyInfo(
                    identifier = recipientKey,
                    context = identifierContext(),
                )
            val unrelatedJwk = generateExternalRsaPrivateJwk(kid = "unrelated-2")

            val jweCompact = encryptToRecipient(recipient, alg = "RSA-OAEP", enc = "A128GCM")

            val resultWrong =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = ManagedOptsJwk(identifier = unrelatedJwk),
                    ),
                )
            assertTrue(
                resultWrong.isErr,
                "CRITICAL: RSA-OAEP+A128GCM decryption with an UNRELATED RSA-2048 private JWK MUST fail",
            )
        }

    @Test
    fun decryptingTwiceWithMatchingThenUnrelatedKeyMustFailSecondCall() =
        runTest {
            // The original observation came from a multi-test class with multiple sequential calls.
            // This reproduces the ordering: a matching decrypt followed by an unrelated decrypt of
            // the SAME ciphertext, in the SAME session (so any per-session CEK cache would surface).
            val recipientKey =
                keyManagerService
                    .generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
                    .joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val recipient =
                ManagedOptsKeyInfo(
                    identifier = recipientKey,
                    context = identifierContext(),
                )
            val matchingDecryptor =
                ManagedOptsKeyInfo(
                    identifier = recipientKey,
                    context = identifierContext(),
                )
            val unrelatedJwk = generateExternalRsaPrivateJwk(kid = "unrelated-3")

            val jweCompact = encryptToRecipient(recipient, alg = "RSA-OAEP-256", enc = "A256GCM")

            val matched =
                jweService.decryptJwe(
                    DecryptJweArgs(jwe = jweCompact, decryptor = matchingDecryptor),
                )
            assertTrue(matched.isOk, "matched decrypt must succeed")

            val mismatched =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = ManagedOptsJwk(identifier = unrelatedJwk),
                    ),
                )
            assertTrue(
                mismatched.isErr,
                "after a successful decrypt, the SAME ciphertext MUST NOT decrypt under an unrelated key; " +
                    "got plaintext '${
                        if (mismatched.isOk) mismatched.value.plaintext?.decodeToString() else "<n/a>"
                    }'",
            )
        }

    @Test
    fun decryptingWithUnrelatedRsaPrivateKey_kidCollidesWithRecipient() =
        runTest {
            // Reproduces the JARM test conditions specifically: the supplied unrelated JWK has the
            // SAME kid as the original recipient. If the resolver does a kid-keyed lookup and falls
            // back to the keystore, this is where the silent-success would surface.
            val recipientKey =
                keyManagerService
                    .generateKeyAsync(alg = SignatureAlgorithm.RSA_SHA256)
                    .joseToManagedKeyInfo(KeyVisibility.PRIVATE)
            val recipientKid = recipientKey.kid ?: error("KMS-generated key must have a kid")
            val recipient =
                ManagedOptsKeyInfo(
                    identifier = recipientKey,
                    context = identifierContext(),
                )

            val unrelatedJwk = generateExternalRsaPrivateJwk(kid = recipientKid)

            val jweCompact = encryptToRecipient(recipient, alg = "RSA-OAEP-256", enc = "A256GCM")

            val resultWrong =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = jweCompact,
                        decryptor = ManagedOptsJwk(identifier = unrelatedJwk),
                    ),
                )
            assertTrue(
                resultWrong.isErr,
                "CRITICAL: even with a kid-collision, decryption with an UNRELATED private JWK MUST fail; " +
                    "got plaintext '${
                        if (resultWrong.isOk) resultWrong.value.plaintext?.decodeToString() else "<n/a>"
                    }'",
            )
        }

    private suspend fun encryptToRecipient(
        recipient: ManagedOptsKeyInfo,
        alg: String,
        enc: String,
    ): JweCompact {
        val plaintext = "wrong-key-decryption-canary".encodeToByteArray()
        val prepared =
            jweService.prepareJwe(
                PrepareJweArgs(
                    plaintext = plaintext,
                    recipient = recipient,
                    keyEncryptionAlg = alg,
                    contentEncryptionAlg = enc,
                ),
            )
        assertTrue(prepared.isOk, "encrypt-prepare must succeed: ${if (prepared.isErr) prepared.error else ""}")
        val created = jweService.createJweCompact(CreateJweCompactArgs(preparedJwe = prepared.value))
        assertTrue(created.isOk, "encrypt-finalise must succeed: ${if (created.isErr) created.error else ""}")
        return created.value
    }

    private fun identifierContext(): IdentifierContext =
        IdentifierContext(
            clientId = "wrong-key-test",
            clientIdScheme = "jwk",
            issuer = "https://wrong-key.test",
        )

    private fun generateExternalRsaPrivateJwk(kid: String): Jwk {
        val kp =
            KeyPairGenerator
                .getInstance("RSA")
                .apply { initialize(RSA_2048, SecureRandom()) }
                .generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val crt = kp.private as RSAPrivateCrtKey
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

    private fun stripLeadingZero(bytes: ByteArray): ByteArray =
        if (bytes.isNotEmpty() && bytes[0].toInt() == 0) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }

    private fun b64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    companion object {
        private const val RSA_2048 = 2048
    }
}
