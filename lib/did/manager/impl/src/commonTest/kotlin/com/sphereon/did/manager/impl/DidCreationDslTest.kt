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

package com.sphereon.did.manager.impl

import com.sphereon.crypto.core.generic.Curve
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.did.manager.dsl.KeyConfig
import com.sphereon.did.manager.dsl.didCreateOptions
import com.sphereon.did.models.VerificationPurpose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the DID Creation DSL builder functions.
 *
 * These tests verify the DSL builds the correct options and key configurations.
 * For full E2E tests with DI and persistence, see DidCreationDslE2ETest.
 */
class DidCreationDslTest {
    @Test
    fun testDidKeyWithAutoGenerateKey() {
        val result =
            didCreateOptions {
                method("key")
                alias("my-signing-did")
                autoGenerateKey {
                    keyType(KeyTypeMapping.OKP)
                    curve(Curve.Ed25519)
                    purposes(VerificationPurpose.AUTHENTICATION, VerificationPurpose.ASSERTION_METHOD)
                }
            }

        // Verify options
        assertEquals("key", result.options.method)
        assertEquals("my-signing-did", result.options.alias)

        // Verify key config
        assertEquals(1, result.keyConfigs.size)
        val keyConfig = result.keyConfigs.first()
        assertTrue(keyConfig is KeyConfig.AutoGenerate)
        assertEquals(KeyTypeMapping.OKP, keyConfig.keyType)
        assertEquals(Curve.Ed25519, keyConfig.curve)
        assertEquals(2, keyConfig.purposes.size)
        assertTrue(VerificationPurpose.AUTHENTICATION in keyConfig.purposes)
        assertTrue(VerificationPurpose.ASSERTION_METHOD in keyConfig.purposes)
    }

    @Test
    fun testDidKeyWithEcKey() {
        val result =
            didCreateOptions {
                method("key")
                autoGenerateKey {
                    keyType(KeyTypeMapping.EC)
                    curve(Curve.P_256)
                    purposes(VerificationPurpose.AUTHENTICATION)
                }
            }

        assertEquals("key", result.options.method)

        val keyConfig = result.keyConfigs.first() as KeyConfig.AutoGenerate
        assertEquals(KeyTypeMapping.EC, keyConfig.keyType)
        assertEquals(Curve.P_256, keyConfig.curve)
    }

    @Test
    fun testDidJwkWithExistingJwk() {
        val jwk =
            Jwk(
                kty = JwaKeyType.OKP,
                crv = JwaCurve.Ed25519,
                x = "Q__LMeuER6M24T4DDbGAI6hLYgmqJJPvcT_87r4YWqs",
            )

        val result =
            didCreateOptions {
                method("jwk")
                useExistingKey(jwk)
            }

        assertEquals("jwk", result.options.method)
        assertEquals(jwk, result.options.publicKeyJwk)

        assertEquals(1, result.keyConfigs.size)
        val keyConfig = result.keyConfigs.first()
        assertTrue(keyConfig is KeyConfig.ExistingJwk)
        assertEquals(jwk, keyConfig.jwk)
    }

    @Test
    fun testDidWebWithMultipleKeys() {
        val result =
            didCreateOptions {
                method("web")
                domain("example.com")
                path("users", "alice")

                verificationMethod("key-1") {
                    autoGenerateKey {
                        keyType(KeyTypeMapping.OKP)
                        curve(Curve.Ed25519)
                    }
                    purposes(VerificationPurpose.AUTHENTICATION, VerificationPurpose.ASSERTION_METHOD)
                }

                verificationMethod("key-2") {
                    useExistingKey {
                        alias("my-encryption-key")
                        providerId("software-kms")
                    }
                    purposes(VerificationPurpose.KEY_AGREEMENT)
                }

                service("linked-domains") {
                    type("LinkedDomains")
                    endpoint("https://example.com/.well-known/did-configuration.json")
                }
            }

        // Verify options
        assertEquals("web", result.options.method)
        assertEquals("example.com", result.options.domain)
        assertEquals(listOf("users", "alice"), result.options.path)

        // Verify services
        assertEquals(1, result.options.services?.size)
        val service = result.options.services!!.first()
        assertEquals("linked-domains", service.id)
        assertEquals("LinkedDomains", service.type)

        // Verify key configs
        assertEquals(2, result.keyConfigs.size)

        val key1 = result.keyConfigs[0] as KeyConfig.AutoGenerate
        assertEquals("key-1", key1.verificationMethodId)
        assertEquals(KeyTypeMapping.OKP, key1.keyType)
        assertEquals(Curve.Ed25519, key1.curve)
        assertTrue(VerificationPurpose.AUTHENTICATION in key1.purposes)
        assertTrue(VerificationPurpose.ASSERTION_METHOD in key1.purposes)

        val key2 = result.keyConfigs[1] as KeyConfig.ExistingKeyByAlias
        assertEquals("key-2", key2.verificationMethodId)
        assertEquals("my-encryption-key", key2.alias)
        assertEquals("software-kms", key2.providerId)
        assertTrue(VerificationPurpose.KEY_AGREEMENT in key2.purposes)
    }

    @Test
    fun testExistingKeyByAlias() {
        val result =
            didCreateOptions {
                method("key")
                useExistingKey {
                    alias("my-existing-key")
                    providerId("aws-kms")
                    purposes(VerificationPurpose.AUTHENTICATION)
                    verificationMethodId("vm-1")
                }
            }

        assertEquals(1, result.keyConfigs.size)
        val keyConfig = result.keyConfigs.first()
        assertTrue(keyConfig is KeyConfig.ExistingKeyByAlias)
        assertEquals("my-existing-key", keyConfig.alias)
        assertEquals("aws-kms", keyConfig.providerId)
        assertEquals("vm-1", keyConfig.verificationMethodId)
        assertTrue(VerificationPurpose.AUTHENTICATION in keyConfig.purposes)
    }

    @Test
    fun testAutoGenerateWithKmsProvider() {
        val result =
            didCreateOptions {
                method("key")
                autoGenerateKey {
                    keyType(KeyTypeMapping.OKP)
                    curve(Curve.Ed25519)
                    kmsProvider("azure-kms")
                    verificationMethodId("signing-key")
                }
            }

        val keyConfig = result.keyConfigs.first() as KeyConfig.AutoGenerate
        assertEquals("azure-kms", keyConfig.kmsProvider)
        assertEquals("signing-key", keyConfig.verificationMethodId)
    }

    @Test
    fun testControllerAndVerificationMethodType() {
        val result =
            didCreateOptions {
                method("web")
                domain("example.com")
                controller("did:web:controller.example.com")
                verificationMethodType(com.sphereon.did.models.VerificationMethodType.JSON_WEB_KEY_2020)
                autoGenerateKey {
                    keyType(KeyTypeMapping.EC)
                    curve(Curve.P_256)
                }
            }

        assertEquals("did:web:controller.example.com", result.options.controller)
        assertEquals(
            com.sphereon.did.models.VerificationMethodType.JSON_WEB_KEY_2020,
            result.options.verificationMethodType,
        )
    }

    @Test
    fun testEmptyKeyConfigs() {
        // Test that legacy path (no DSL key configs) works
        val jwk =
            Jwk(
                kty = JwaKeyType.OKP,
                crv = JwaCurve.Ed25519,
                x = "Q__LMeuER6M24T4DDbGAI6hLYgmqJJPvcT_87r4YWqs",
            )

        val result =
            didCreateOptions {
                method("jwk")
                // Using direct JWK via useExistingKey
                useExistingKey(jwk)
            }

        // Should have one key config for the existing JWK
        assertEquals(1, result.keyConfigs.size)
        assertTrue(result.keyConfigs.first() is KeyConfig.ExistingJwk)

        // publicKeyJwk should be set from the first key config
        assertNotNull(result.options.publicKeyJwk)
    }
}
