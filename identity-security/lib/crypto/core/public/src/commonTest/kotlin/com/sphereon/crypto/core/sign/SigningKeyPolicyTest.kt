package com.sphereon.crypto.core.sign

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyOperations
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class SigningKeyPolicyTest {
    @Test
    fun rsaPssAndPkcs1AreDistinctWhenJwkAlgIsExplicit() {
        assertNull(rsa(JwaAlgorithm.PS256).signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1))
        assertNotNull(rsa(JwaAlgorithm.PS256).signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SHA256))
        assertNull(rsa(JwaAlgorithm.RS256).signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SHA256))
        assertNotNull(rsa(JwaAlgorithm.RS256).signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1))
    }

    @Test
    fun rsaWithoutOptionalAlgorithmMetadataRemainsUsable() {
        assertNull(rsa(null).signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SHA256))
        assertNull(rsa(null).signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1))
    }

    @Test
    fun rsaJwkWithoutAlgStillHonorsExplicitResolvedAlgorithm() {
        val resolvedPss = rsa(null).copy(signatureAlgorithm = SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1)
        val resolvedPkcs1 = rsa(null).copy(signatureAlgorithm = SignatureAlgorithm.RSA_SHA256)

        assertNull(resolvedPss.signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1))
        assertNotNull(resolvedPss.signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SHA256))
        assertNull(resolvedPkcs1.signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SHA256))
        assertNotNull(resolvedPkcs1.signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1))
    }

    @Test
    fun hmacRequiresSymmetricKeyFamily() {
        val hmac = SignatureAlgorithm.HMAC_SHA256
        assertNotNull(rsa(null).signingKeyCompatibilityFailure(hmac))
        assertNull(
            KeyInfo(
                key = Jwk(kty = JwaKeyType.oct, k = "AQ"),
            ).signingKeyCompatibilityFailure(hmac),
        )
    }

    @Test
    fun metadataOnlyProviderKeyNeedsAuthoritativeTypeAndAlgorithm() {
        val providerKey = { type: KeyTypeMapping?, algorithm: SignatureAlgorithm? ->
            ManagedKeyReference(
                alias = "kms-signing-key",
                providerId = "kms",
                keyType = type,
                signatureAlgorithm = algorithm,
            )
        }

        assertNotNull(providerKey(null, SignatureAlgorithm.ECDSA_SHA256).signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))
        assertNotNull(providerKey(KeyTypeMapping.EC, null).signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))
        assertNull(providerKey(KeyTypeMapping.EC, SignatureAlgorithm.ECDSA_SHA256).signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))

        assertNotNull(KeyInfo<Nothing>(signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256).signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))
    }

    @Test
    fun coseVerificationRequiresVerifyOperationAndResolvedKeyFamily() {
        val signingOnly = KeyInfo(
            key = CoseKeyJson(
                kty = CoseKeyTypeEnum.EC2,
                alg = CoseAlgorithm.ES256,
                crv = CoseCurve.P_256,
                key_ops = arrayOf(CoseKeyOperations.SIGN),
            ),
        )
        assertNotNull(
            signingOnly.keyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256, KeyOperations.VERIFY),
        )

        val rsaKey = KeyInfo(
            key = CoseKeyJson(kty = CoseKeyTypeEnum.RSA, alg = CoseAlgorithm.RS256),
        )
        assertNotNull(
            rsaKey.keyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256, KeyOperations.VERIFY),
        )
    }

    @Test
    fun nonRsaJwkWithoutOptionalAlgorithmMetadataCannotOverrideResolvedAlgorithm() {
        val ecWithConflictingResolvedAlgorithm =
            KeyInfo(
                key = Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256, x = "AQ", y = "AQ", d = "AQ"),
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA384,
            )
        val okpWithConflictingResolvedAlgorithm =
            KeyInfo(
                key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = "AQ", d = "AQ"),
                signatureAlgorithm = SignatureAlgorithm.ED448,
            )

        assertNotNull(ecWithConflictingResolvedAlgorithm.signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))
        assertNotNull(okpWithConflictingResolvedAlgorithm.signingKeyCompatibilityFailure(SignatureAlgorithm.ED25519))
    }

    @Test
    fun ecCurveMustMatchRequestedAlgorithm() {
        assertNull(ec(JwaCurve.P_256).signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))
        assertNotNull(ec(JwaCurve.P_384).signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))
        assertNotNull(ec(JwaCurve.P_256).signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA384))
    }

    @Test
    fun actualJwkAndCoseKeyMaterialMustDeclareRequiredCurve() {
        val missingJwkCurve = KeyInfo(
            key = Jwk(kty = JwaKeyType.EC, x = "AQ", y = "AQ", d = "AQ"),
        )
        val missingOkpCurve = KeyInfo(
            key = Jwk(kty = JwaKeyType.OKP, x = "AQ", d = "AQ"),
        )
        val missingCoseJson = CoseKeyJson(kty = CoseKeyTypeEnum.EC2, alg = CoseAlgorithm.ES256, x = "AQ", y = "AQ", d = "AQ")
        val missingCoseJsonCurve = KeyInfo(key = missingCoseJson)
        val missingCoseCurve = KeyInfo(key = missingCoseJson.toCbor())

        assertNotNull(missingJwkCurve.signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))
        assertNotNull(missingOkpCurve.signingKeyCompatibilityFailure(SignatureAlgorithm.ED25519))
        assertNotNull(missingCoseJsonCurve.signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))
        assertNotNull(missingCoseCurve.signingKeyCompatibilityFailure(SignatureAlgorithm.ECDSA_SHA256))
    }

    @Test
    fun explicitKeyTypeAndUseCannotAuthorizeSigning() {
        val encryptionUse = KeyInfo(
            key = Jwk(
                kty = JwaKeyType.RSA,
                alg = JwaAlgorithm.RS256,
                use = "enc",
                n = "AQ",
                e = "AQ",
                d = "AQ",
            ),
            keyType = KeyTypeMapping.RSA,
        )
        assertNotNull(encryptionUse.signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SHA256))

        val wrongType = KeyInfo(
            key = rsa(JwaAlgorithm.RS256).key,
            keyType = KeyTypeMapping.EC,
        )
        assertNotNull(wrongType.signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SHA256))
    }

    @Test
    fun keyOpsMustContainSignWhenDeclared() {
        val verifyOnly = KeyInfo(
            key = Jwk(
                kty = JwaKeyType.RSA,
                alg = JwaAlgorithm.RS256,
                key_ops = arrayOf(JoseKeyOperations.VERIFY),
            ),
        )
        assertNotNull(verifyOnly.signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SHA256))
    }

    @Test
    fun explicitProviderAndAliasDoNotOverrideConflictingAlgorithm() {
        val selected = KeyInfo(
            alias = "issuer-signing",
            providerId = "hsm-provider",
            key = Jwk(
                kty = JwaKeyType.RSA,
                alg = JwaAlgorithm.RS256,
                n = "AQ",
                e = "AQ",
                d = "AQ",
            ),
        )
        assertNotNull(selected.signingKeyCompatibilityFailure(SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1))
    }

    private fun rsa(alg: JwaAlgorithm?): KeyInfo<*> = KeyInfo(
        key = Jwk(kty = JwaKeyType.RSA, alg = alg, n = "AQ", e = "AQ", d = "AQ"),
    )

    private fun ec(curve: JwaCurve): KeyInfo<*> = KeyInfo(
        key = Jwk(kty = JwaKeyType.EC, crv = curve, x = "AQ", y = "AQ", d = "AQ"),
    )
}
