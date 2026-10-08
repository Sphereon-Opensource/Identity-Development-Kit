package com.sphereon.crypto.jose.jws.command

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.Jwk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ResolvedPublicJwkEdDsaResolutionTest {
    @Test
    fun resolvedEd25519JwkAcceptsJoseEdDsa() {
        val keyInfo = KeyInfo(key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519))

        assertNull(keyInfo.jwsAlgorithmCompatibilityFailure("EdDSA"))
    }

    @Test
    fun resolvedEd448JwkAcceptsJoseEdDsa() {
        val keyInfo = KeyInfo(key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed448))

        assertNull(keyInfo.jwsAlgorithmCompatibilityFailure("EdDSA"))
    }

    @Test
    fun unresolvedEdDsaJwkIsRejectedForMissingCurve() {
        val keyInfo = KeyInfo(key = Jwk(kty = JwaKeyType.OKP))

        assertContains(
            assertNotNull(keyInfo.jwsAlgorithmCompatibilityFailure("EdDSA")),
            "must declare an Ed25519 or Ed448 curve",
        )
    }

    @Test
    fun metadataOnlyEdDsaWithoutAuthoritativeAlgorithmIsRejectedAsAmbiguous() {
        val keyInfo = KeyInfo<KeyType>(keyType = KeyTypeMapping.OKP)

        assertContains(
            assertNotNull(keyInfo.jwsAlgorithmCompatibilityFailure("EdDSA")),
            "ambiguous",
        )
    }

    @Test
    fun resolvedEd448CannotBeLabeledEd25519() {
        val keyInfo = KeyInfo(
            key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed448),
            signatureAlgorithm = SignatureAlgorithm.ED25519,
        )

        assertContains(
            assertNotNull(keyInfo.jwsAlgorithmCompatibilityFailure("EdDSA")),
            "contradict",
        )
    }

    @Test
    fun edDsaVerificationRejectsEncryptionUseAndMissingVerifyOperation() {
        val encryptionUse = KeyInfo(
            key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed448, use = "enc"),
        )
        val signingOnly = KeyInfo(
            key = Jwk(
                kty = JwaKeyType.OKP,
                crv = JwaCurve.Ed448,
                key_ops = arrayOf(JoseKeyOperations.SIGN),
            ),
        )

        assertContains(
            assertNotNull(encryptionUse.jwsAlgorithmCompatibilityFailure("EdDSA")),
            "use must be 'sig'",
        )
        assertContains(
            assertNotNull(signingOnly.jwsAlgorithmCompatibilityFailure("EdDSA")),
            "key_ops must include 'verify'",
        )
    }
}
