package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.x509.certificateChainFromPem
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.impl.signing.IssuerKeyIdResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssuerSigningHeaderResolverTest {
    @Test
    fun configuredX5cIsUsedWhenKmsHasNoChain() = runTest {
        val configured = certificateChainFromPem(CERTIFICATE_PEM).map { it.derToBase64() }.toTypedArray()

        val result = resolveIssuerSigningHeader(
            kms = TestKmsMock().also { it.putTestJwk("issuer-signing", CERTIFICATE_JWK) },
            issuerKeyIdResolver = NoDidResolver,
            keyAlias = "issuer-signing",
            mode = SigningKeyMode.X5c,
            signingVerificationMethodId = null,
            configuredX5c = configured,
        )

        assertTrue(result.isOk)
        assertEquals(configured.toList(), result.getOrThrow()!!.get("x5c")!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun kmsX5cTakesPrecedenceOverConfiguredX5c() = runTest {
        val kmsChain = arrayOf(certificateChainFromPem(CERTIFICATE_PEM).single().derToBase64())
        val result = resolveIssuerSigningHeader(
            kms = TestKmsMock().also {
                it.putTestJwk(
                    "issuer-signing",
                    CERTIFICATE_JWK.copy(x5c = kmsChain),
                )
            },
            issuerKeyIdResolver = NoDidResolver,
            keyAlias = "issuer-signing",
            mode = SigningKeyMode.X5c,
            signingVerificationMethodId = null,
            configuredX5c = arrayOf("not-a-certificate"),
        )

        assertTrue(result.isOk)
        assertEquals(kmsChain.toList(), result.getOrThrow()!!.get("x5c")!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun malformedKmsX5cSingleEntryFailsClosedWithoutUsingConfiguredChain() = runTest {
        val configured = certificateChainFromPem(CERTIFICATE_PEM).single().derToBase64()
        val result = resolveIssuerSigningHeader(
            kms = TestKmsMock().also {
                it.putTestJwk(
                    "issuer-signing",
                    Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, x = "x", y = "y", x5c = arrayOf("not-a-certificate")),
                )
            },
            issuerKeyIdResolver = NoDidResolver,
            keyAlias = "issuer-signing",
            mode = SigningKeyMode.X5c,
            signingVerificationMethodId = null,
            configuredX5c = arrayOf(configured),
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_chain_invalid", result.error.code)
    }

    @Test
    fun malformedKmsX5cMultiEntryFailsClosedWithoutThrowingOrUsingConfiguredChain() = runTest {
        val configured = certificateChainFromPem(CERTIFICATE_PEM).single().derToBase64()
        val result = resolveIssuerSigningHeader(
            kms = TestKmsMock().also {
                it.putTestJwk(
                    "issuer-signing",
                    Jwk(
                        kty = JwaKeyType.EC,
                        crv = JwaCurve.P_256,
                        x = CERTIFICATE_JWK.x,
                        y = CERTIFICATE_JWK.y,
                        x5c = arrayOf(configured, "not-a-certificate"),
                    ),
                )
            },
            issuerKeyIdResolver = NoDidResolver,
            keyAlias = "issuer-signing",
            mode = SigningKeyMode.X5c,
            signingVerificationMethodId = null,
            configuredX5c = arrayOf(configured),
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_chain_invalid", result.error.code)
    }

    @Test
    fun malformedConfiguredX5cFailsClosed() = runTest {
        val result = resolveIssuerSigningHeader(
            kms = TestKmsMock(),
            issuerKeyIdResolver = NoDidResolver,
            keyAlias = "issuer-signing",
            mode = SigningKeyMode.X5c,
            signingVerificationMethodId = null,
            configuredX5c = arrayOf("not-a-certificate"),
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_chain_invalid", result.error.code)
    }

    @Test
    fun configuredX5cMustMatchKmsSigningKey() = runTest {
        val configured = certificateChainFromPem(CERTIFICATE_PEM).single().derToBase64()
        val result = resolveIssuerSigningHeader(
            kms = TestKmsMock().also {
                it.putTestJwk("issuer-signing", OTHER_EC_JWK)
            },
            issuerKeyIdResolver = NoDidResolver,
            keyAlias = "issuer-signing",
            mode = SigningKeyMode.X5c,
            signingVerificationMethodId = null,
            configuredX5c = arrayOf(configured),
        )

        assertTrue(result.isErr)
        assertEquals("signing_certificate_key_mismatch", result.error.code)
    }

    @Test
    fun x5cSigningRejectsOctKmsSigningKey() = runTest {
        val configured = certificateChainFromPem(CERTIFICATE_PEM).single().derToBase64()
        val result = resolveIssuerSigningHeader(
            kms = TestKmsMock().also {
                it.putTestJwk("issuer-signing", Jwk(kty = JwaKeyType.oct, k = "c2VjcmV0"))
            },
            issuerKeyIdResolver = NoDidResolver,
            keyAlias = "issuer-signing",
            mode = SigningKeyMode.X5c,
            signingVerificationMethodId = null,
            configuredX5c = arrayOf(configured),
        )

        assertTrue(result.isErr)
        assertEquals("signing_key_material_unavailable", result.error.code)
    }

    private companion object {
        private val CERTIFICATE_JWK: Jwk
            get() = Jwk.from(Jwk.fromX509CertificatePem(CERTIFICATE_PEM))
        private val OTHER_EC_JWK = Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, x = "AQ", y = "Ag")

        private val CERTIFICATE_PEM = """
-----BEGIN CERTIFICATE-----
MIIB3DCCAYMCFA6bjsh9CB8NbtINaWK8WNgBMx2iMAoGCCqGSM49BAMCMHExCzAJ
BgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlBbXN0
ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQdGVz
dC5leGFtcGxlLmNvbTAeFw0yNTA1MDEwOTE5NDFaFw0yNjA1MDEwOTE5NDFaMHEx
CzAJBgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlB
bXN0ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQ
dGVzdC5leGFtcGxlLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABP7W2xjU
4raapzyctjNDkRLGHP7RgAtVqAHRnS5LWz2oXhgKHyhCcwlLrfCOCEIHta+gajwz
2mxZ8j6ix1SNXvkwCgYIKoZIzj0EAwIDRwAwRAIgF9E2jWW+qMnmL3qpB5VvJ/8J
e/K96UVYWQ2T23OA1SYCIAaD8LNo+RgwA0rE7wKKOrogIfQUy+qFPVKjmcDTcHln
-----END CERTIFICATE-----
        """.trimIndent()
    }
}

private object NoDidResolver : IssuerKeyIdResolver {
    override suspend fun resolveDidVerificationMethodId(
        keyAlias: String,
        didMethod: String,
    ): IdkResult<String, IdkError> = Err(IdkError.fromString(message = "DID resolution is not used by this test"))

    override suspend fun resolvePublicJwk(keyAlias: String): IdkResult<JsonObject, IdkError> =
        Err(IdkError.fromString(message = "JWK resolution is not used by this test"))
}
