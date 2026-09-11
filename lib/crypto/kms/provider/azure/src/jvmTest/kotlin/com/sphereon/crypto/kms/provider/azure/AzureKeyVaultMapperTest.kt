package com.sphereon.crypto.kms.provider.azure

import com.azure.json.JsonProviders
import com.azure.security.keyvault.keys.models.KeyVaultKey
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AzureKeyVaultMapperTest {
    @Test
    fun ecAndEcHsmProjectionPreservesAllSupportedCurvesAndPublicMaterial() {
        listOf("EC", "EC-HSM").forEach { keyType ->
            listOf(
                "P-256" to (JwaCurve.P_256 to JwaAlgorithm.ES256),
                "P-384" to (JwaCurve.P_384 to JwaAlgorithm.ES384),
                "P-521" to (JwaCurve.P_521 to JwaAlgorithm.ES512),
            ).forEach { (curve, expected) ->
                val key = fixtureKey(keyType, curve)
                val jwk = key.toJwk()
                assertEquals(JwaKeyType.EC, jwk.kty)
                assertEquals(expected.first, jwk.crv)
                assertEquals(expected.second, jwk.alg)
                assertEquals(SignatureAlgorithm.fromJose(expected.second), key.toSignatureAlgorithm())
                assertEquals(listOf(JoseKeyOperations.SIGN, JoseKeyOperations.VERIFY), jwk.key_ops?.toList())
                assertEquals("AQ", jwk.x)
                assertEquals("Ag", jwk.y)
                assertNull(jwk.d)
                assertNull(jwk.k)
            }
        }
    }

    @Test
    fun rsaAndRsaHsmProjectionPreservesPublicModulusAndExponentWithoutAlgorithmSynthesis() {
        listOf("RSA", "RSA-HSM").forEach { keyType ->
            val key = fixtureKey(keyType)
            val jwk = key.toJwk()
            assertEquals(JwaKeyType.RSA, jwk.kty)
            assertEquals("AQID", jwk.n)
            assertEquals("AQ", jwk.e)
            assertNull(jwk.alg)
            assertNull(key.toSignatureAlgorithm())
            assertNull(jwk.d)
            assertNull(jwk.k)
        }
    }

    private fun fixtureKey(keyType: String, curve: String? = null): KeyVaultKey {
        val keyFields =
            buildString {
                append("\"kty\":\"").append(keyType).append("\"")
                append(",\"kid\":\"https://vault.example/keys/test/version-1\",\"d\":\"Aw\"")
                curve?.let { append(",\"crv\":\"").append(it).append("\",\"x\":\"AQ\",\"y\":\"Ag\"") }
                if (curve == null) append(",\"n\":\"AQID\",\"e\":\"AQ\"")
                append(",\"key_ops\":[\"sign\",\"verify\"]")
            }
        return JsonProviders.createReader("{\"key\":{$keyFields}}").use(KeyVaultKey::fromJson)
    }
}
