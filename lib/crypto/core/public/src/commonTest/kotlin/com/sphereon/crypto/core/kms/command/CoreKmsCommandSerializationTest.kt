package com.sphereon.crypto.core.kms.command

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.json.cryptoJsonSerializer
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CoreKmsCommandSerializationTest {
    private val json = cryptoJsonSerializer

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
}
