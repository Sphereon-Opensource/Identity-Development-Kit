package com.sphereon.crypto.kms.rest.api.mapper

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm as SdkSignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.kms.rest.api.generated.models.ImportKey
import com.sphereon.crypto.kms.rest.api.generated.models.Jwk
import com.sphereon.crypto.kms.rest.api.generated.models.JwkKeyType
import com.sphereon.crypto.kms.rest.api.generated.models.KeyInfo
import com.sphereon.crypto.kms.rest.api.generated.models.ManagedKeyInfo
import com.sphereon.crypto.kms.rest.api.generated.models.ResolvedKeyInfo
import com.sphereon.crypto.kms.rest.api.generated.models.SignatureAlgorithm
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SignatureAlgorithmMapperTest {
    private val cases = listOf(
        SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1 to "PS256",
        SignatureAlgorithm.RSA_SHA256 to "RS256",
        null to null,
    )

    @Test
    fun resolvedKeyAlgorithmsSurviveRestSdkRoundTrip() {
        for ((algorithm, jwsAlgorithm) in cases) {
            val sdk = ResolvedKeyInfo(key = key(jwsAlgorithm), signatureAlgorithm = algorithm).toSdk()
            assertAlgorithms(sdk, algorithm, jwsAlgorithm)
            val returned = sdk.toRest()
            assertEquals(algorithm, returned.signatureAlgorithm)
            assertEquals(jwsAlgorithm, returned.key.alg)
        }
    }

    @Test
    fun managedKeyAlgorithmsSurviveRestSdkRoundTrip() {
        for ((algorithm, jwsAlgorithm) in cases) {
            val sdk = ManagedKeyInfo(key = key(jwsAlgorithm), alias = "signing-key", providerId = "software", signatureAlgorithm = algorithm).toSdk()
            assertAlgorithms(sdk, algorithm, jwsAlgorithm)
            val returned = sdk.toRest()
            assertEquals(algorithm, returned.signatureAlgorithm)
            assertEquals(jwsAlgorithm, returned.key.alg)
        }
    }

    @Test
    fun genericKeyAlgorithmsSurviveRestSdkRoundTrip() {
        for ((algorithm, jwsAlgorithm) in cases) {
            val sdk = KeyInfo(key = key(jwsAlgorithm), signatureAlgorithm = algorithm).toSdk()
            assertAlgorithms(sdk, algorithm, jwsAlgorithm)
            val returned = sdk.toRest()
            assertEquals(algorithm, returned.signatureAlgorithm)
            assertEquals(jwsAlgorithm, returned.key?.alg)
        }
    }

    @Test
    fun declaredImportWireFieldsPreserveExplicitPs256Constraint() {
        val request = Json.decodeFromString<ImportKey>(
            """{"keyInfo":{"key":{"kty":"RSA","alg":"PS256","n":"public-modulus","e":"AQAB"},"signatureAlgorithm":"RSA_SSA_PSS_SHA256_MGF1"}}""",
        )
        assertEquals(SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1, request.keyInfo.signatureAlgorithm)
        assertEquals("PS256", request.keyInfo.key.alg)
        assertAlgorithms(request.keyInfo.toSdk(), SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1, "PS256")
    }

    private fun key(algorithm: String?) = Jwk(kty = JwkKeyType.RSA, alg = algorithm, n = "public-modulus", e = "AQAB")

    private fun assertAlgorithms(key: KeyInfoType<*>, algorithm: SignatureAlgorithm?, jwsAlgorithm: String?) {
        assertEquals(algorithm?.value?.let { SdkSignatureAlgorithm.fromValue(it) }, key.signatureAlgorithm)
        assertEquals(jwsAlgorithm, (key.key as JwkType).alg?.value)
    }
}
