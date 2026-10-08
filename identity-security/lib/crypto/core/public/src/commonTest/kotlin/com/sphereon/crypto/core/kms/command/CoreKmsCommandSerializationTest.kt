package com.sphereon.crypto.core.kms.command

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class CoreKmsCommandSerializationTest {
    private val json = cryptoJsonSerializer

    @Test
    fun listKeysArgsPreservesExactAliasFilterForRemoteTransport() {
        val encoded = json.encodeToString(ListKeysArgs(providerId = "default", alias = "idfr:bi:tenant-a"))

        val decoded = json.decodeFromString<ListKeysArgs>(encoded)

        assertEquals("default", decoded.providerId)
        assertEquals("idfr:bi:tenant-a", decoded.alias)
    }

    @Test
    fun generateKeyResultTransportsOnlyMetadataReceipt() {
        val encoded =
            json.encodeToString(
                GenerateKeyResult(
                    keyReference =
                        ManagedKeyReference(
                            providerId = "default",
                            alias = "idfr:enc:tenant-a",
                        ),
                ),
            )

        val decoded = json.decodeFromString<GenerateKeyResult>(encoded)

        assertEquals("default", assertNotNull(decoded.keyReference).providerId)
        assertEquals("idfr:enc:tenant-a", decoded.keyReference?.alias)
        assertEquals(null, decoded.keyPair)
        assertFalse(encoded.contains("keyPair"))
    }

    @Test
    fun getKeyArgsSerializesKeyInfoForRemoteTransport() {
        val encoded =
            json.encodeToString(
                GetKeyArgs(
                    keyInfo =
                        KeyInfo<KeyType>(
                            providerId = "software",
                            alias = "acme-authentication",
                        ),
                ),
            )

        val decoded = json.decodeFromString<GetKeyArgs>(encoded)

        val keyInfo = assertNotNull(decoded.keyInfo)
        assertEquals("software", keyInfo.providerId)
        assertEquals("acme-authentication", keyInfo.alias)
    }

    @Test
    fun getKeyResultSerializesManagedKeyInfoForRemoteTransport() {
        val encoded =
            json.encodeToString(
                GetKeyResult(
                    key =
                        ManagedKeyInfo.fromKeyInfo(
                            KeyInfo(
                                key =
                                    Jwk(
                                        kty = JwaKeyType.EC,
                                        crv = JwaCurve.P_256,
                                        x = "f83OJ3D2xF4r0F1nA_9rQ6j0yD1h2CkI7Nn8aY7e1H0",
                                        y = "x_FEzRu9d0w8-QfO9WQY8QK2rHqspnV3FQf5X4G1zHo",
                                    ),
                                providerId = "software",
                                alias = "acme-authentication",
                            ),
                        ),
                ),
            )

        val decoded = json.decodeFromString<GetKeyResult>(encoded)

        val key = assertNotNull(decoded.key)
        assertEquals("software", key.providerId)
        assertEquals("acme-authentication", key.alias)
    }

    @Test
    fun getSymmetricKeyResultPreservesPublicWrappingMetadataForRemoteTransport() {
        val encoded =
            json.encodeToString(
                GetKeyResult(
                    key =
                        ManagedKeyInfo.fromKeyInfo(
                            KeyInfo(
                                key = Jwk(kty = JwaKeyType.oct, kid = "tenant-secret-kek"),
                                providerId = "acme",
                                alias = "tenant-secret-kek",
                                kid = "tenant-secret-kek",
                                keyType = KeyTypeMapping.Symmetric,
                            ),
                        ),
                ),
            )

        val key = assertNotNull(json.decodeFromString<GetKeyResult>(encoded).key)
        assertEquals("tenant-secret-kek", key.kid)
        assertEquals(KeyTypeMapping.Symmetric, key.keyType)
        assertEquals(JwaKeyType.oct, (key.key as Jwk).kty)
    }

    @Test
    fun createRawSignatureArgsSerializesManagedKeyReferenceForRemoteTransport() {
        val encoded =
            json.encodeToString(
                CreateRawSignatureArgs(
                    keyInfo =
                        ManagedKeyReference(
                            providerId = "software",
                            alias = "acme-assertion",
                            kid = "did:jwk:example#0",
                        ),
                    input = "payload".encodeToByteArray(),
                ),
            )

        val decoded = json.decodeFromString<CreateRawSignatureArgs>(encoded)

        val keyInfo = assertNotNull(decoded.keyInfo)
        assertEquals("software", keyInfo.providerId)
        assertEquals("acme-assertion", keyInfo.alias)
        assertEquals("did:jwk:example#0", keyInfo.kid)
    }

    @Test
    fun signDigestArgsSerializesManagedKeyReferenceForRemoteTransport() {
        val digest = "caller-supplied-digest".encodeToByteArray()
        val encoded =
            json.encodeToString(
                SignDigestArgs(
                    keyInfo =
                        ManagedKeyReference(
                            providerId = "software",
                            alias = "secdsa-signing",
                            kid = "did:jwk:example#digest",
                        ),
                    digest = digest,
                    signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                    signatureEncoding = SignatureEncoding.DER,
                ),
            )

        val decoded = json.decodeFromString<SignDigestArgs>(encoded)

        val keyInfo = assertNotNull(decoded.keyInfo)
        assertEquals("software", keyInfo.providerId)
        assertEquals("secdsa-signing", keyInfo.alias)
        assertEquals("did:jwk:example#digest", keyInfo.kid)
        assertEquals(SignatureAlgorithm.ECDSA_SHA256, decoded.signatureAlgorithm)
        assertEquals(SignatureEncoding.DER, decoded.signatureEncoding)
        assertContentEquals(digest, decoded.digest)
    }

    @Test
    fun ecdhDeriveArgsSerializesKeyReferencesForRemoteTransport() {
        val encoded =
            json.encodeToString(
                EcdhDeriveArgs(
                    privateKeyInfo = ManagedKeyReference(providerId = "software", alias = "alice-private"),
                    publicKeyInfo = ManagedKeyReference(providerId = "software", alias = "bob-public"),
                    algorithm = KeyAgreementAlgorithm.ECDH_ES_A256KW,
                    mode = EcdhDeriveMode.CONCAT_KDF,
                    keyDataLen = 256,
                    algorithmId = "A256KW",
                    partyUInfo = "apu".encodeToByteArray(),
                    partyVInfo = "apv".encodeToByteArray(),
                ),
            )

        val decoded = json.decodeFromString<EcdhDeriveArgs>(encoded)

        assertEquals("alice-private", assertNotNull(decoded.privateKeyInfo).alias)
        assertEquals("bob-public", assertNotNull(decoded.publicKeyInfo).alias)
        assertEquals(KeyAgreementAlgorithm.ECDH_ES_A256KW, decoded.algorithm)
        assertEquals(EcdhDeriveMode.CONCAT_KDF, decoded.mode)
        assertEquals(256, decoded.keyDataLen)
        assertEquals("A256KW", decoded.algorithmId)
        assertContentEquals("apu".encodeToByteArray(), decoded.partyUInfo)
        assertContentEquals("apv".encodeToByteArray(), decoded.partyVInfo)
    }

    @Test
    fun ecPointMultiplyArgsSerializesKeyReferencesForRemoteTransport() {
        val encoded =
            json.encodeToString(
                EcPointMultiplyArgs(
                    privateKeyInfo = ManagedKeyReference(providerId = "software", alias = "activation-scalar"),
                    publicKeyInfo = ManagedKeyReference(providerId = "software", alias = "activation-point"),
                ),
            )

        val decoded = json.decodeFromString<EcPointMultiplyArgs>(encoded)

        assertEquals("activation-scalar", assertNotNull(decoded.privateKeyInfo).alias)
        assertEquals("activation-point", assertNotNull(decoded.publicKeyInfo).alias)
        assertEquals(EcPointMultiplyOutput.RAW_X, decoded.output)
    }

    @Test
    fun unwrapKeyArgsPreservesResolvedKeyIdentifierForRemoteTransport() {
        val wrappedKey = "wrapped-data-encryption-key".encodeToByteArray()
        val encoded =
            json.encodeToString(
                UnwrapKeyArgs(
                    unwrappingKeyInfo =
                        KeyInfo<KeyType>(
                            providerId = "default",
                            kid = "tenant-secret-kek",
                            noCache = true,
                        ),
                    wrappedKey = wrappedKey,
                    algorithm = KeyWrapAlgorithm.A256KW,
                ),
            )

        val decoded = json.decodeFromString<UnwrapKeyArgs>(encoded)

        val keyInfo = assertNotNull(decoded.unwrappingKeyInfo)
        assertEquals("default", keyInfo.providerId)
        assertEquals("tenant-secret-kek", keyInfo.kid)
        assertEquals(null, keyInfo.alias)
        assertEquals(true, keyInfo.noCache)
        assertContentEquals(wrappedKey, decoded.wrappedKey)
        assertEquals(KeyWrapAlgorithm.A256KW, decoded.algorithm)
    }
}
