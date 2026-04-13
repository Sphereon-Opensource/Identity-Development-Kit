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

package com.sphereon.openid.oid4vci.issuer.impl.encryption

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jwe.CreateJweCompactArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralArgs
import com.sphereon.crypto.jose.jwe.DecryptJweArgs
import com.sphereon.crypto.jose.jwe.JweCompact
import com.sphereon.crypto.jose.jwe.JweDecryptionResult
import com.sphereon.crypto.jose.jwe.JweHeader
import com.sphereon.crypto.jose.jwe.JweJsonFlattened
import com.sphereon.crypto.jose.jwe.JweJsonGeneral
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jwe.PrepareJweArgs
import com.sphereon.crypto.jose.jwe.PreparedJwe
import com.sphereon.openid.oid4vci.common.model.CredentialResponse
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialResponseEncryptorTest {
    private val sampleResponse =
        CredentialResponse(
            credential = JsonPrimitive("eyJhbGciOiJFUzI1NiJ9.test.signature"),
            cNonce = "test-nonce-123",
            cNonceExpiresIn = 86400,
            notificationId = "notif-456",
        )

    private val sampleJwk =
        buildJsonObject {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU")
            put("y", "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0")
        }

    @Test
    fun returnsResponseUnchangedWhenNoEncryptionRequested() =
        runTest {
            val encryptor = CredentialResponseEncryptor(jweService = FakeJweService())

            val result = encryptor.encryptIfRequested(sampleResponse, encryption = null)

            assertTrue(result.isOk)
            val output = result.getOrThrow()
            assertEquals(sampleResponse, output)
        }

    @Test
    fun returnsErrorForInvalidJwk() =
        runTest {
            val encryptor = CredentialResponseEncryptor(jweService = FakeJweService())

            val invalidJwk =
                buildJsonObject {
                    // Missing required "kty" field
                    put("crv", "P-256")
                }

            val encryption =
                RequestedCredentialResponseEncryption(
                    jwk = invalidJwk,
                    alg = "ECDH-ES+A128KW",
                    enc = "A256GCM",
                )

            val result = encryptor.encryptIfRequested(sampleResponse, encryption)

            assertTrue(result.isErr)
        }

    @Test
    fun returnsErrorWhenAlgMissingFromBothRequestAndJwk() =
        runTest {
            val encryptor = CredentialResponseEncryptor(jweService = FakeJweService())

            val encryption =
                RequestedCredentialResponseEncryption(
                    jwk = sampleJwk,
                    alg = null, // No alg in request
                    enc = "A256GCM",
                )
            // sampleJwk also has no "alg" field

            val result = encryptor.encryptIfRequested(sampleResponse, encryption)

            assertTrue(result.isErr)
        }

    @Test
    fun encryptsResponseWhenEncryptionRequested() =
        runTest {
            val fakeService = FakeJweService()
            val encryptor = CredentialResponseEncryptor(jweService = fakeService)

            val encryption =
                RequestedCredentialResponseEncryption(
                    jwk = sampleJwk,
                    alg = "ECDH-ES+A128KW",
                    enc = "A256GCM",
                )

            val result = encryptor.encryptIfRequested(sampleResponse, encryption)

            assertTrue(result.isOk)
            val output = result.getOrThrow()
            assertNotNull(output.credential)
            // The credential should be a JWE compact string (5 dot-separated parts)
            val credentialString =
                output.credential?.let {
                    assertTrue(it is JsonPrimitive)
                    (it as JsonPrimitive).content
                }
            assertNotNull(credentialString)
            val parts = credentialString.split(".")
            assertEquals(5, parts.size, "JWE compact serialization should have 5 parts")

            // Other response fields should be absent in encrypted response
            assertEquals(null, output.cNonce)
            assertEquals(null, output.cNonceExpiresIn)
            assertEquals(null, output.notificationId)
            assertEquals(null, output.transactionId)
        }

    /**
     * Fake JweService that produces deterministic JWE compact output for testing.
     * The encrypted content is not real encryption but produces valid JWE structure.
     */
    private class FakeJweService : JweService {
        override val commands: JweService.Commands
            get() = throw UnsupportedOperationException("not used in tests")

        override suspend fun prepareJwe(args: PrepareJweArgs): IdkResult<PreparedJwe, IdkError> {
            val header = JweHeader()
            header.alg = args.keyEncryptionAlg
            header.enc = args.contentEncryptionAlg
            return Ok(
                PreparedJwe(
                    header = header,
                    plaintext = args.plaintext,
                    cek = ByteArray(32) { 0x42 },
                    recipient = args.recipient,
                ),
            )
        }

        override suspend fun createJweCompact(args: CreateJweCompactArgs): IdkResult<JweCompact, IdkError> {
            val prepared =
                args.preparedJwe
                    ?: return Ok(
                        JweCompact(
                            header = JweHeader(),
                            encryptedKey = ByteArray(0),
                            iv = ByteArray(12),
                            ciphertext = ByteArray(0),
                            authTag = ByteArray(16),
                        ),
                    )
            return Ok(
                JweCompact(
                    header = prepared.header,
                    encryptedKey = ByteArray(32) { 0xAA.toByte() },
                    iv = ByteArray(12) { 0xBB.toByte() },
                    ciphertext = prepared.plaintext ?: ByteArray(0),
                    authTag = ByteArray(16) { 0xCC.toByte() },
                ),
            )
        }

        override suspend fun createJweJsonFlattened(args: CreateJweJsonArgs): IdkResult<JweJsonFlattened, IdkError> = throw UnsupportedOperationException("not used in tests")

        override suspend fun createJweJsonGeneral(args: CreateJweJsonGeneralArgs): IdkResult<JweJsonGeneral, IdkError> = throw UnsupportedOperationException("not used in tests")

        override suspend fun decryptJwe(args: DecryptJweArgs): IdkResult<JweDecryptionResult, IdkError> = throw UnsupportedOperationException("not used in tests")
    }
}
