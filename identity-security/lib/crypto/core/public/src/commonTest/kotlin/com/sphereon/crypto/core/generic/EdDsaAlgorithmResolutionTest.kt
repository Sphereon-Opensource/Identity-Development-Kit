package com.sphereon.crypto.core.generic

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EdDsaAlgorithmResolutionTest {
    @Test
    fun bothEdDsaVariantsAreAdvertisedWithoutChangingGenericFamilyMapping() {
        assertEquals(true, SignatureAlgorithm.asList.contains(SignatureAlgorithm.ED25519))
        assertEquals(true, SignatureAlgorithm.asList.contains(SignatureAlgorithm.ED448))
        // The no-key mapping remains intentionally ambiguous/legacy and is not the secure resolver.
        assertEquals(SignatureAlgorithm.ED25519, SignatureAlgorithm.fromJose(JwaAlgorithm.EdDSA))
    }

    @Test
    fun joseEdDsaUsesResolvedEd25519Curve() {
        val keyInfo = KeyInfo(key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519))

        assertEquals(
            SignatureAlgorithm.ED25519,
            SignatureAlgorithm.fromJoseForKey(JwaAlgorithm.EdDSA, keyInfo),
        )
    }

    @Test
    fun joseEdDsaUsesResolvedEd448Curve() {
        val keyInfo = KeyInfo(key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed448))

        assertEquals(
            SignatureAlgorithm.ED448,
            SignatureAlgorithm.fromJoseForKey(JwaAlgorithm.EdDSA, keyInfo),
        )
    }

    @Test
    fun coseEdDsaUsesResolvedEd448Curve() {
        val keyInfo = KeyInfo(
            key = CoseKeyJson(kty = CoseKeyTypeEnum.OKP, crv = CoseCurve.Ed448),
        )

        assertEquals(
            SignatureAlgorithm.ED448,
            SignatureAlgorithm.fromCoseForKey(CoseAlgorithm.EdDSA, keyInfo),
        )
    }

    @Test
    fun coseDtoEdDsaUsesResolvedEd448Curve() {
        val cose = Jwk(
            kty = JwaKeyType.OKP,
            crv = JwaCurve.Ed448,
            alg = JwaAlgorithm.EdDSA,
        ).jwkToCoseKey()

        assertEquals(
            SignatureAlgorithm.ED448,
            SignatureAlgorithm.fromCoseForKey(CoseAlgorithm.EdDSA, KeyInfo(key = cose)),
        )
    }

    @Test
    fun coseDtoEdDsaRejectsContradictoryResolvedAlgorithm() {
        val cose =
            CoseKeyJson(
                kty = CoseKeyTypeEnum.OKP,
                crv = CoseCurve.Ed448,
                alg = CoseAlgorithm.RS256,
            ).toCbor()

        assertFailsWith<IllegalArgumentException> {
            SignatureAlgorithm.fromCoseForKey(CoseAlgorithm.EdDSA, KeyInfo(key = cose))
        }
    }

    @Test
    fun keyAlgorithmInferencePreservesEd448() {
        val jose = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed448, alg = JwaAlgorithm.EdDSA)
        val cose = CoseKeyJson(kty = CoseKeyTypeEnum.OKP, crv = CoseCurve.Ed448, alg = CoseAlgorithm.EdDSA)

        assertEquals(SignatureAlgorithm.ED448, jose.getSignatureAlgorithm())
        assertEquals(SignatureAlgorithm.ED448, cose.getSignatureAlgorithm())
    }

    @Test
    fun metadataOnlyAuthoritativeEd448RequiresResolvedOkpType() {
        assertEquals(
            SignatureAlgorithm.ED448,
            SignatureAlgorithm.fromJoseForKey(
                JwaAlgorithm.EdDSA,
                KeyInfo<Nothing>(
                    keyType = KeyTypeMapping.OKP,
                    signatureAlgorithm = SignatureAlgorithm.ED448,
                ),
            ),
        )
    }

    @Test
    fun metadataOnlyAuthoritativeEd448ResolvesForCoseWithResolvedOkpType() {
        assertEquals(
            SignatureAlgorithm.ED448,
            SignatureAlgorithm.fromCoseForKey(
                CoseAlgorithm.EdDSA,
                KeyInfo<Nothing>(
                    keyType = KeyTypeMapping.OKP,
                    signatureAlgorithm = SignatureAlgorithm.ED448,
                ),
            ),
        )
    }

    @Test
    fun metadataOnlyAuthoritativeEd448RejectsMissingKeyType() {
        assertFailsWith<IllegalArgumentException> {
            SignatureAlgorithm.fromJoseForKey(
                JwaAlgorithm.EdDSA,
                KeyInfo<Nothing>(signatureAlgorithm = SignatureAlgorithm.ED448),
            )
        }
    }

    @Test
    fun metadataOnlyAuthoritativeEd448RejectsWrongKeyType() {
        assertFailsWith<IllegalArgumentException> {
            SignatureAlgorithm.fromCoseForKey(
                CoseAlgorithm.EdDSA,
                KeyInfo<Nothing>(keyType = KeyTypeMapping.EC, signatureAlgorithm = SignatureAlgorithm.ED448),
            )
        }
    }

    @Test
    fun actualJwkEdDsaMaterialWithoutCurveRejectsAuthoritativeAlgorithm() {
        assertFailsWith<IllegalArgumentException> {
            SignatureAlgorithm.fromJoseForKey(
                JwaAlgorithm.EdDSA,
                KeyInfo(
                    key = Jwk(kty = JwaKeyType.OKP, x = "AQ"),
                    signatureAlgorithm = SignatureAlgorithm.ED448,
                ),
            )
        }
    }

    @Test
    fun actualCoseEdDsaMaterialWithoutCurveRejectsAuthoritativeAlgorithm() {
        assertFailsWith<IllegalArgumentException> {
            SignatureAlgorithm.fromCoseForKey(
                CoseAlgorithm.EdDSA,
                KeyInfo(
                    key = CoseKeyJson(kty = CoseKeyTypeEnum.OKP, x = "AQ"),
                    signatureAlgorithm = SignatureAlgorithm.ED448,
                ),
            )
        }
    }

    @Test
    fun edDsaWithoutCurveOrAuthoritativeAlgorithmIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SignatureAlgorithm.fromJoseForKey(
                JwaAlgorithm.EdDSA,
                KeyInfo(key = Jwk(kty = JwaKeyType.OKP)),
            )
        }
    }

    @Test
    fun contradictoryEdDsaCurveAndAuthoritativeAlgorithmIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SignatureAlgorithm.fromJoseForKey(
                JwaAlgorithm.EdDSA,
                KeyInfo(
                    key = Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed448),
                    signatureAlgorithm = SignatureAlgorithm.ED25519,
                ),
            )
        }
    }

    @Test
    fun contradictoryEdDsaKeyTypeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SignatureAlgorithm.fromJoseForKey(
                JwaAlgorithm.EdDSA,
                KeyInfo(key = Jwk(kty = JwaKeyType.EC, crv = JwaCurve.P_256)),
            )
        }
    }
}
