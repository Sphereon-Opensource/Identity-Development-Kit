/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.holder

import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for encryption-related fields on holder command args.
 *
 * Validates that RequestCredentialArgs and RequestDeferredCredentialArgs
 * correctly carry request encryption fields and that defaults are null.
 */
class EncryptionArgsTest {
    private val testJwk =
        buildJsonObject {
            put("kty", JsonPrimitive("EC"))
            put("crv", JsonPrimitive("P-256"))
            put("x", JsonPrimitive("f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU"))
            put("y", JsonPrimitive("x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"))
        }

    private val testResponseEncryption =
        RequestedCredentialResponseEncryption(
            jwk = testJwk,
            alg = "ECDH-ES+A256KW",
            enc = "A256GCM",
        )

    // ========================================================================
    // RequestCredentialArgs with request encryption fields round-trip
    // ========================================================================

    @Test
    fun requestCredentialArgsWithEncryptionFields() {
        val args =
            RequestCredentialArgs(
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "eyJhbGciOiJSUzI1NiJ9.token.sig",
                credentialConfigurationId = "UniversityDegreeCredential",
                credentialResponseEncryption = testResponseEncryption,
                requestEncryptionJwk = testJwk,
                requestEncryptionAlg = "ECDH-ES+A256KW",
                requestEncryptionEnc = "A256GCM",
            )

        assertEquals("https://issuer.example.com/credential", args.credentialEndpoint)
        assertEquals("eyJhbGciOiJSUzI1NiJ9.token.sig", args.accessToken)
        assertEquals("UniversityDegreeCredential", args.credentialConfigurationId)
        assertNotNull(args.credentialResponseEncryption)
        assertEquals("A256GCM", args.credentialResponseEncryption!!.enc)
        assertEquals("ECDH-ES+A256KW", args.credentialResponseEncryption!!.alg)
        assertNotNull(args.requestEncryptionJwk)
        assertEquals("EC", args.requestEncryptionJwk!!["kty"]?.let { (it as? JsonPrimitive)?.content })
        assertEquals("ECDH-ES+A256KW", args.requestEncryptionAlg)
        assertEquals("A256GCM", args.requestEncryptionEnc)
    }

    @Test
    fun requestCredentialArgsCopyPreservesEncryption() {
        val original =
            RequestCredentialArgs(
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "token",
                credentialConfigurationId = "Degree",
                requestEncryptionJwk = testJwk,
                requestEncryptionAlg = "RSA-OAEP",
                requestEncryptionEnc = "A128GCM",
            )

        val copy = original.copy(accessToken = "new-token")

        assertEquals("new-token", copy.accessToken)
        // Encryption fields must be preserved through copy
        assertNotNull(copy.requestEncryptionJwk)
        assertEquals("RSA-OAEP", copy.requestEncryptionAlg)
        assertEquals("A128GCM", copy.requestEncryptionEnc)
    }

    // ========================================================================
    // RequestDeferredCredentialArgs with request encryption fields round-trip
    // ========================================================================

    @Test
    fun requestDeferredCredentialArgsWithEncryptionFields() {
        val args =
            RequestDeferredCredentialArgs(
                deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                accessToken = "eyJ.deferred-token.sig",
                transactionId = "txn-enc-001",
                credentialResponseEncryption = testResponseEncryption,
                requestEncryptionJwk = testJwk,
                requestEncryptionAlg = "RSA-OAEP",
                requestEncryptionEnc = "A256GCM",
            )

        assertEquals("https://issuer.example.com/deferred", args.deferredCredentialEndpoint)
        assertEquals("eyJ.deferred-token.sig", args.accessToken)
        assertEquals("txn-enc-001", args.transactionId)
        assertNotNull(args.credentialResponseEncryption)
        assertNotNull(args.requestEncryptionJwk)
        assertEquals("RSA-OAEP", args.requestEncryptionAlg)
        assertEquals("A256GCM", args.requestEncryptionEnc)
    }

    @Test
    fun requestDeferredCredentialArgsCopyPreservesEncryption() {
        val original =
            RequestDeferredCredentialArgs(
                deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                accessToken = "token",
                transactionId = "txn-001",
                requestEncryptionJwk = testJwk,
                requestEncryptionAlg = "ECDH-ES",
                requestEncryptionEnc = "A128GCM",
            )

        val copy = original.copy(transactionId = "txn-002")

        assertEquals("txn-002", copy.transactionId)
        assertNotNull(copy.requestEncryptionJwk)
        assertEquals("ECDH-ES", copy.requestEncryptionAlg)
        assertEquals("A128GCM", copy.requestEncryptionEnc)
    }

    // ========================================================================
    // Optional request values remain absent when the protocol does not supply them.
    // ========================================================================

    @Test
    fun requestCredentialArgsDefaultsAreNull() {
        val args =
            RequestCredentialArgs(
                credentialEndpoint = "https://issuer.example.com/credential",
                accessToken = "token",
            )

        assertNull(args.credentialConfigurationId)
        assertNull(args.credentialIdentifier)
        assertNull(args.proofs)
        assertNull(args.credentialResponseEncryption)
        assertNull(args.nonceEndpoint)
        assertNull(args.decryptionKey)
        assertNull(args.requestEncryptionJwk)
        assertNull(args.requestEncryptionAlg)
        assertNull(args.requestEncryptionEnc)
    }

    @Test
    fun requestDeferredCredentialArgsDefaultsAreNull() {
        val args =
            RequestDeferredCredentialArgs(
                deferredCredentialEndpoint = "https://issuer.example.com/deferred",
                accessToken = "token",
                transactionId = "txn-001",
            )

        assertNull(args.credentialResponseEncryption)
        assertNull(args.decryptionKey)
        assertNull(args.requestEncryptionJwk)
        assertNull(args.requestEncryptionAlg)
        assertNull(args.requestEncryptionEnc)
    }

    // ========================================================================
    // Response encryption model fields
    // ========================================================================

    @Test
    fun requestedCredentialResponseEncryptionFields() {
        val encryption =
            RequestedCredentialResponseEncryption(
                jwk = testJwk,
                alg = "ECDH-ES+A256KW",
                enc = "A256GCM",
                zip = "DEF",
            )

        assertEquals(testJwk, encryption.jwk)
        assertEquals("ECDH-ES+A256KW", encryption.alg)
        assertEquals("A256GCM", encryption.enc)
        assertEquals("DEF", encryption.zip)
    }

    @Test
    fun requestedCredentialResponseEncryptionAlgOptional() {
        // OID4VCI 1.1: alg is optional when key agreement comes from JWK
        val encryption =
            RequestedCredentialResponseEncryption(
                jwk = testJwk,
                enc = "A256GCM",
            )

        assertNull(encryption.alg, "alg should be optional in 1.1")
        assertNull(encryption.zip, "zip should default to null")
        assertEquals("A256GCM", encryption.enc)
    }
}
