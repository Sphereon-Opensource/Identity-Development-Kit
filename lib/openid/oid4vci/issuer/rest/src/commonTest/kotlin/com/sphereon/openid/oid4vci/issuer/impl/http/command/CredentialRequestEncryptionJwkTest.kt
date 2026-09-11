package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlin.test.Test
import kotlin.test.assertEquals

class CredentialRequestEncryptionJwkTest {
    @Test
    fun projectsKmsP256SigningAlgorithmToEcdhEsEncryptionMetadata() {
        val kmsPublicJwk =
            Jwk(
                kty = JwaKeyType.EC,
                crv = JwaCurve.P_256,
                x = "test-x",
                y = "test-y",
                kid = "issuer-request-decryption",
                use = "enc",
                alg = JwaAlgorithm.ES256,
            )

        val published = kmsPublicJwk.asCredentialRequestEncryptionJwk()

        assertEquals("enc", published.use)
        assertEquals(JwaAlgorithm.ECDH_ES, published.alg)
        assertEquals(kmsPublicJwk.kid, published.kid)
        assertEquals(kmsPublicJwk.x, published.x)
        assertEquals(kmsPublicJwk.y, published.y)
    }
}
