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

package com.sphereon.crypto.core

import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for serialization round-trip of key types and related classes.
 */
class SerializationRoundTripTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // ==================== Basic Type Serialization ====================

    @Test
    fun testKeyIdentitySerialization() {
        val identity = KeyIdentity(
            kid = "test-kid-123",
            providerId = "test-provider",
            alias = "test-alias",
            keyType = KeyTypeMapping.EC
        )

        val serialized = json.encodeToString(identity)
        val deserialized = json.decodeFromString<KeyIdentity>(serialized)

        assertEquals(identity.kid, deserialized.kid)
        assertEquals(identity.providerId, deserialized.providerId)
        assertEquals(identity.alias, deserialized.alias)
        assertEquals(identity.keyType, deserialized.keyType)
    }

    @Test
    fun testKeyIdentityHasIdentity() {
        val withKid = KeyIdentity(kid = "test-kid")
        val withAlias = KeyIdentity(alias = "test-alias")
        val empty = KeyIdentity()

        assertEquals(true, withKid.hasIdentity())
        assertEquals(true, withAlias.hasIdentity())
        assertEquals(false, empty.hasIdentity())
    }

    @Test
    fun testKeyVisibilitySerialization() {
        val visibility = KeyVisibility.PRIVATE

        val serialized = json.encodeToString(visibility)
        val deserialized = json.decodeFromString<KeyVisibility>(serialized)

        assertEquals(visibility, deserialized)
    }

    @Test
    fun testKeyEncodingSerialization() {
        val encoding = KeyEncoding.JOSE

        val serialized = json.encodeToString(encoding)
        val deserialized = json.decodeFromString<KeyEncoding>(serialized)

        assertEquals(encoding, deserialized)
    }

    @Test
    fun testSignatureAlgorithmSerialization() {
        val algorithm = SignatureAlgorithm.ECDSA_SHA256

        // Use explicit serializer for sealed class
        val serialized = json.encodeToString(SignatureAlgorithm.serializer(), algorithm)
        val deserialized = json.decodeFromString(SignatureAlgorithm.serializer(), serialized)

        assertEquals(algorithm, deserialized)
    }

    @Test
    fun testKeyTypeMappingSerialization() {
        val keyType = KeyTypeMapping.EC

        // Use explicit serializer for sealed class
        val serialized = json.encodeToString(KeyTypeMapping.serializer(), keyType)
        val deserialized = json.decodeFromString(KeyTypeMapping.serializer(), serialized)

        assertEquals(keyType, deserialized)
    }

    // ==================== JOSE Key Serialization ====================

    @Test
    fun testJwkEC256Serialization() {
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "test-ec-key"
        )

        val serialized = json.encodeToString(jwk)
        val deserialized = json.decodeFromString<Jwk>(serialized)

        assertEquals(jwk.kty, deserialized.kty)
        assertEquals(jwk.crv, deserialized.crv)
        assertEquals(jwk.x, deserialized.x)
        assertEquals(jwk.y, deserialized.y)
        assertEquals(jwk.kid, deserialized.kid)
    }

    @Test
    fun testJwkEC384Serialization() {
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_384,
            x = "iGnmKXM6uYIFwDsX1j-C-FkXXYcBPMXF0K8PkeWkzV7O0FqU_E3JUMIBCtLqKo5e",
            y = "xGgR2-X-pMsKe3N8HMr5t-OVPWxyZl7u9g8KxBJiuE-cqnMPJpYgLDhAfNnmvlR3",
            kid = "test-ec384-key"
        )

        val serialized = json.encodeToString(jwk)
        val deserialized = json.decodeFromString<Jwk>(serialized)

        assertEquals(jwk.kty, deserialized.kty)
        assertEquals(jwk.crv, deserialized.crv)
        assertEquals(jwk.x, deserialized.x)
        assertEquals(jwk.y, deserialized.y)
    }

    @Test
    fun testJwkRSASerialization() {
        val jwk = Jwk(
            kty = JwaKeyType.RSA,
            n = "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
            e = "AQAB",
            kid = "test-rsa-key"
        )

        val serialized = json.encodeToString(jwk)
        val deserialized = json.decodeFromString<Jwk>(serialized)

        assertEquals(jwk.kty, deserialized.kty)
        assertEquals(jwk.n, deserialized.n)
        assertEquals(jwk.e, deserialized.e)
        assertEquals(jwk.kid, deserialized.kid)
    }

    @Test
    fun testJwkOKPSerialization() {
        val jwk = Jwk(
            kty = JwaKeyType.OKP,
            crv = JwaCurve.Ed25519,
            x = "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo",
            kid = "test-okp-key"
        )

        val serialized = json.encodeToString(jwk)
        val deserialized = json.decodeFromString<Jwk>(serialized)

        assertEquals(jwk.kty, deserialized.kty)
        assertEquals(jwk.crv, deserialized.crv)
        assertEquals(jwk.x, deserialized.x)
        assertEquals(jwk.kid, deserialized.kid)
    }

    @Test
    fun testJwkWithPrivateKeySerialization() {
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            d = "2nqEH-JrPC98gJMsEYFVykqWLMqO_6URt9eZGH19_K0",
            kid = "test-ec-private-key"
        )

        val serialized = json.encodeToString(jwk)
        val deserialized = json.decodeFromString<Jwk>(serialized)

        assertEquals(jwk.kty, deserialized.kty)
        assertEquals(jwk.d, deserialized.d)
        assertNotNull(deserialized.d)
    }

    // ==================== COSE Key Serialization ====================

    @Test
    fun testCoseKeyJsonEC256Serialization() {
        val coseKey = CoseKeyJson(
            kty = CoseKeyTypeEnum.EC2,
            crv = CoseCurve.P_256,
            alg = CoseAlgorithm.ES256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "test-cose-ec-key"
        )

        val serialized = json.encodeToString(coseKey)
        val deserialized = json.decodeFromString<CoseKeyJson>(serialized)

        assertEquals(coseKey.kty, deserialized.kty)
        assertEquals(coseKey.crv, deserialized.crv)
        assertEquals(coseKey.alg, deserialized.alg)
        assertEquals(coseKey.x, deserialized.x)
        assertEquals(coseKey.y, deserialized.y)
        assertEquals(coseKey.kid, deserialized.kid)
    }

    @Test
    fun testCoseKeyJsonEC384Serialization() {
        val coseKey = CoseKeyJson(
            kty = CoseKeyTypeEnum.EC2,
            crv = CoseCurve.P_384,
            alg = CoseAlgorithm.ES384,
            x = "iGnmKXM6uYIFwDsX1j-C-FkXXYcBPMXF0K8PkeWkzV7O0FqU_E3JUMIBCtLqKo5e",
            y = "xGgR2-X-pMsKe3N8HMr5t-OVPWxyZl7u9g8KxBJiuE-cqnMPJpYgLDhAfNnmvlR3",
            kid = "test-cose-ec384-key"
        )

        val serialized = json.encodeToString(coseKey)
        val deserialized = json.decodeFromString<CoseKeyJson>(serialized)

        assertEquals(coseKey.kty, deserialized.kty)
        assertEquals(coseKey.crv, deserialized.crv)
        assertEquals(coseKey.alg, deserialized.alg)
    }

    @Test
    fun testCoseKeyJsonOKPSerialization() {
        val coseKey = CoseKeyJson(
            kty = CoseKeyTypeEnum.OKP,
            crv = CoseCurve.Ed25519,
            alg = CoseAlgorithm.EdDSA,
            x = "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo",
            kid = "test-cose-okp-key"
        )

        val serialized = json.encodeToString(coseKey)
        val deserialized = json.decodeFromString<CoseKeyJson>(serialized)

        assertEquals(coseKey.kty, deserialized.kty)
        assertEquals(coseKey.crv, deserialized.crv)
        assertEquals(coseKey.alg, deserialized.alg)
        assertEquals(coseKey.x, deserialized.x)
    }

    @Test
    fun testCoseKeyJsonWithPrivateKeySerialization() {
        val coseKey = CoseKeyJson(
            kty = CoseKeyTypeEnum.EC2,
            crv = CoseCurve.P_256,
            alg = CoseAlgorithm.ES256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            d = "2nqEH-JrPC98gJMsEYFVykqWLMqO_6URt9eZGH19_K0",
            kid = "test-cose-ec-private-key"
        )

        val serialized = json.encodeToString(coseKey)
        val deserialized = json.decodeFromString<CoseKeyJson>(serialized)

        assertEquals(coseKey.kty, deserialized.kty)
        assertEquals(coseKey.d, deserialized.d)
        assertNotNull(deserialized.d)
    }

    // ==================== KeyInfo Serialization with JOSE Keys ====================

    @Test
    fun testKeyInfoWithJwkSerialization() {
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "jose-key-in-keyinfo"
        )

        val keyInfo = KeyInfo(
            kid = jwk.kid,
            key = jwk,
            keyVisibility = KeyVisibility.PUBLIC,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            alias = "test-alias",
            providerId = "test-provider",
            keyType = KeyTypeMapping.EC,
            keyEncoding = KeyEncoding.JOSE
        )

        val serialized = json.encodeToString(keyInfo)
        val deserialized = json.decodeFromString<KeyInfo<Jwk>>(serialized)

        assertEquals(keyInfo.kid, deserialized.kid)
        assertEquals(keyInfo.alias, deserialized.alias)
        assertEquals(keyInfo.providerId, deserialized.providerId)
        assertEquals(keyInfo.keyVisibility, deserialized.keyVisibility)
        assertEquals(keyInfo.keyEncoding, deserialized.keyEncoding)
        assertNotNull(deserialized.key)
        assertEquals(jwk.kty, deserialized.key?.kty)
        assertEquals(jwk.x, deserialized.key?.x)
    }

    @Test
    fun testKeyInfoWithoutKeySerialization() {
        val keyInfo = KeyInfo<Jwk>(
            kid = "reference-only-key",
            keyVisibility = KeyVisibility.PUBLIC,
            alias = "reference-alias",
            providerId = "reference-provider"
        )

        val serialized = json.encodeToString(keyInfo)
        val deserialized = json.decodeFromString<KeyInfo<Jwk>>(serialized)

        assertEquals(keyInfo.kid, deserialized.kid)
        assertEquals(keyInfo.alias, deserialized.alias)
        assertEquals(keyInfo.providerId, deserialized.providerId)
        assertEquals(null, deserialized.key)
    }

    // ==================== KeyInfo Serialization with COSE Keys ====================

    @Test
    fun testKeyInfoWithCoseKeySerialization() {
        val coseKey = CoseKeyJson(
            kty = CoseKeyTypeEnum.EC2,
            crv = CoseCurve.P_256,
            alg = CoseAlgorithm.ES256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "cose-key-in-keyinfo"
        )

        val keyInfo = KeyInfo(
            kid = coseKey.kid,
            key = coseKey,
            keyVisibility = KeyVisibility.PUBLIC,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            alias = "cose-test-alias",
            providerId = "cose-test-provider",
            keyType = KeyTypeMapping.EC,
            keyEncoding = KeyEncoding.COSE
        )

        val serialized = json.encodeToString(keyInfo)
        val deserialized = json.decodeFromString<KeyInfo<CoseKeyJson>>(serialized)

        assertEquals(keyInfo.kid, deserialized.kid)
        assertEquals(keyInfo.alias, deserialized.alias)
        assertEquals(keyInfo.keyEncoding, deserialized.keyEncoding)
        assertNotNull(deserialized.key)
        assertEquals(coseKey.kty, deserialized.key?.kty)
        assertEquals(coseKey.x, deserialized.key?.x)
    }

    // ==================== ResolvedKeyInfo Serialization ====================

    @Test
    fun testResolvedKeyInfoWithJwkSerialization() {
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "resolved-jose-key"
        )

        val resolvedKeyInfo = ResolvedKeyInfo(
            kid = jwk.kid,
            key = jwk,
            keyVisibility = KeyVisibility.PUBLIC,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            alias = "resolved-alias",
            providerId = "resolved-provider",
            keyType = KeyTypeMapping.EC,
            keyEncoding = KeyEncoding.JOSE
        )

        val serialized = json.encodeToString(resolvedKeyInfo)
        val deserialized = json.decodeFromString<ResolvedKeyInfo<Jwk>>(serialized)

        assertEquals(resolvedKeyInfo.kid, deserialized.kid)
        assertEquals(resolvedKeyInfo.alias, deserialized.alias)
        assertEquals(resolvedKeyInfo.providerId, deserialized.providerId)
        assertEquals(resolvedKeyInfo.keyVisibility, deserialized.keyVisibility)
        assertEquals(resolvedKeyInfo.signatureAlgorithm, deserialized.signatureAlgorithm)
        assertEquals(jwk.kty, deserialized.key.kty)
        assertEquals(jwk.x, deserialized.key.x)
        assertEquals(jwk.y, deserialized.key.y)
    }

    @Test
    fun testResolvedKeyInfoWithCoseKeySerialization() {
        val coseKey = CoseKeyJson(
            kty = CoseKeyTypeEnum.EC2,
            crv = CoseCurve.P_256,
            alg = CoseAlgorithm.ES256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "resolved-cose-key"
        )

        val resolvedKeyInfo = ResolvedKeyInfo(
            kid = coseKey.kid,
            key = coseKey,
            keyVisibility = KeyVisibility.PUBLIC,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            alias = "resolved-cose-alias",
            providerId = "resolved-cose-provider",
            keyType = KeyTypeMapping.EC,
            keyEncoding = KeyEncoding.COSE
        )

        val serialized = json.encodeToString(resolvedKeyInfo)
        val deserialized = json.decodeFromString<ResolvedKeyInfo<CoseKeyJson>>(serialized)

        assertEquals(resolvedKeyInfo.kid, deserialized.kid)
        assertEquals(resolvedKeyInfo.alias, deserialized.alias)
        assertEquals(resolvedKeyInfo.keyEncoding, deserialized.keyEncoding)
        assertEquals(coseKey.kty, deserialized.key.kty)
        assertEquals(coseKey.alg, deserialized.key.alg)
    }

    @Test
    fun testResolvedKeyInfoWithPrivateJwkSerialization() {
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            d = "2nqEH-JrPC98gJMsEYFVykqWLMqO_6URt9eZGH19_K0",
            kid = "resolved-private-jose-key"
        )

        val resolvedKeyInfo = ResolvedKeyInfo(
            kid = jwk.kid,
            key = jwk,
            keyVisibility = KeyVisibility.PRIVATE,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            alias = "private-key-alias",
            providerId = "private-key-provider"
        )

        val serialized = json.encodeToString(resolvedKeyInfo)
        val deserialized = json.decodeFromString<ResolvedKeyInfo<Jwk>>(serialized)

        assertEquals(KeyVisibility.PRIVATE, deserialized.keyVisibility)
        assertNotNull(deserialized.key.d)
        assertEquals(jwk.d, deserialized.key.d)
    }

    // ==================== ManagedKeyInfo Serialization ====================

    @Test
    fun testManagedKeyInfoWithJwkSerialization() {
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "managed-jose-key"
        )

        val resolvedKeyInfo = ResolvedKeyInfo(
            kid = jwk.kid,
            key = jwk,
            keyVisibility = KeyVisibility.PUBLIC,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            alias = "managed-alias",
            providerId = "managed-provider"
        )

        val managedKeyInfo = ManagedKeyInfo(
            alias = "managed-alias",
            providerId = "managed-provider",
            resolvedKeyInfo = resolvedKeyInfo
        )

        // Use explicit serializer to avoid polymorphic naming conflict
        val serializer = ManagedKeyInfo.serializer(Jwk.serializer())
        val serialized = json.encodeToString(serializer, managedKeyInfo)
        val deserialized = json.decodeFromString(serializer, serialized)

        assertEquals(managedKeyInfo.alias, deserialized.alias)
        assertEquals(managedKeyInfo.providerId, deserialized.providerId)
        assertEquals(jwk.kty, deserialized.key.kty)
        assertEquals(jwk.x, deserialized.key.x)
    }

    @Test
    fun testManagedKeyInfoWithCoseKeySerialization() {
        val coseKey = CoseKeyJson(
            kty = CoseKeyTypeEnum.EC2,
            crv = CoseCurve.P_256,
            alg = CoseAlgorithm.ES256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "managed-cose-key"
        )

        val resolvedKeyInfo = ResolvedKeyInfo(
            kid = coseKey.kid,
            key = coseKey,
            keyVisibility = KeyVisibility.PUBLIC,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            alias = "managed-cose-alias",
            providerId = "managed-cose-provider",
            keyEncoding = KeyEncoding.COSE
        )

        val managedKeyInfo = ManagedKeyInfo(
            alias = "managed-cose-alias",
            providerId = "managed-cose-provider",
            resolvedKeyInfo = resolvedKeyInfo
        )

        // Use explicit serializer to avoid polymorphic naming conflict
        val serializer = ManagedKeyInfo.serializer(CoseKeyJson.serializer())
        val serialized = json.encodeToString(serializer, managedKeyInfo)
        val deserialized = json.decodeFromString(serializer, serialized)

        assertEquals(managedKeyInfo.alias, deserialized.alias)
        assertEquals(managedKeyInfo.providerId, deserialized.providerId)
        assertEquals(coseKey.kty, deserialized.key.kty)
        assertEquals(coseKey.alg, deserialized.key.alg)
    }

    // ==================== Cross-Format Conversion Tests ====================

    @Test
    fun testResolvedKeyInfoToKeyInfoConversion() {
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "conversion-test-key"
        )

        val resolvedKeyInfo = ResolvedKeyInfo(
            kid = jwk.kid,
            key = jwk,
            keyVisibility = KeyVisibility.PUBLIC,
            signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
            alias = "conversion-alias",
            providerId = "conversion-provider"
        )

        val keyInfo = resolvedKeyInfo.toKeyInfo()

        assertEquals(resolvedKeyInfo.kid, keyInfo.kid)
        assertEquals(resolvedKeyInfo.alias, keyInfo.alias)
        assertEquals(resolvedKeyInfo.providerId, keyInfo.providerId)
        assertNotNull(keyInfo.key)
    }

    @Test
    fun testKeyInfoFromDTOWithJwk() {
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            kid = "dto-test-key"
        )

        val original = KeyInfo(
            kid = jwk.kid,
            key = jwk,
            keyVisibility = KeyVisibility.PUBLIC,
            alias = "dto-alias",
            providerId = "dto-provider"
        )

        val fromDTO = KeyInfo.fromDTO(original)

        assertEquals(original.kid, fromDTO.kid)
        assertEquals(original.alias, fromDTO.alias)
        assertNotNull(fromDTO.key)
    }

    // ==================== All Signature Algorithm Variants ====================

    @Test
    fun testAllSignatureAlgorithmsSerialization() {
        val algorithms = listOf(
            SignatureAlgorithm.ECDSA_SHA256,
            SignatureAlgorithm.ECDSA_SHA384,
            SignatureAlgorithm.ECDSA_SHA512,
            SignatureAlgorithm.RSA_SHA256,
            SignatureAlgorithm.RSA_SHA384,
            SignatureAlgorithm.RSA_SHA512,
            SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1,
            SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1,
            SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1
        )

        for (algorithm in algorithms) {
            val serialized = json.encodeToString(SignatureAlgorithm.serializer(), algorithm)
            val deserialized = json.decodeFromString(SignatureAlgorithm.serializer(), serialized)
            assertEquals(algorithm, deserialized, "Failed for algorithm: $algorithm")
        }
    }

    // ==================== All Key Type Mapping Variants ====================

    @Test
    fun testAllKeyTypeMappingsSerialization() {
        val keyTypes = listOf(
            KeyTypeMapping.EC,
            KeyTypeMapping.RSA,
            KeyTypeMapping.OKP
        )

        for (keyType in keyTypes) {
            val serialized = json.encodeToString(KeyTypeMapping.serializer(), keyType)
            val deserialized = json.decodeFromString(KeyTypeMapping.serializer(), serialized)
            assertEquals(keyType, deserialized, "Failed for keyType: $keyType")
        }
    }

    // ==================== Edge Cases ====================

    @Test
    fun testJwkWithX5cCertificateChain() {
        // Test Jwk serialization with x5c array (certificate chain)
        // Using a simple base64 string - actual certificate validation is done elsewhere
        val sampleCertBase64 = "SGVsbG9Xb3JsZENlcnRpZmljYXRl"  // Base64 of "HelloWorldCertificate"
        val jwk = Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "WbbEfK3hWXcRbFJLuVf1JOXU3VqlJvq7xQ_KMiLfdHQ",
            y = "ZC1XxhNDxR4lFMJqHQON3QmJXCHbERnp-S4y2pLxT-4",
            x5c = arrayOf(sampleCertBase64),
            kid = "x5c-test-key"
        )

        // Serialize and deserialize just the Jwk with x5c
        val serialized = json.encodeToString(jwk)
        val deserialized = json.decodeFromString<Jwk>(serialized)

        assertNotNull(deserialized.x5c)
        assertTrue(deserialized.x5c!!.isNotEmpty())
        assertEquals(sampleCertBase64, deserialized.x5c!![0])
    }

    @Test
    fun testEmptyKeyIdentitySerialization() {
        val emptyIdentity = KeyIdentity()

        val serialized = json.encodeToString(emptyIdentity)
        val deserialized = json.decodeFromString<KeyIdentity>(serialized)

        assertEquals(null, deserialized.kid)
        assertEquals(null, deserialized.alias)
        assertEquals(null, deserialized.providerId)
        assertEquals(null, deserialized.keyType)
    }
}
