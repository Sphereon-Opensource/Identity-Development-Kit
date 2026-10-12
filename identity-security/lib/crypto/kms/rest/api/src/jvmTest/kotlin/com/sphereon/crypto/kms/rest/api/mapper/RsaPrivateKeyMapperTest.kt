package com.sphereon.crypto.kms.rest.api.mapper

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.x509.convertToJavaPrivateKey
import com.sphereon.crypto.kms.rest.api.generated.models.Jwk
import com.sphereon.crypto.kms.rest.api.generated.models.JwkKeyType
import com.sphereon.crypto.kms.rest.api.generated.models.KeyVisibility
import com.sphereon.crypto.kms.rest.api.generated.models.ResolvedKeyInfo
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RsaPrivateKeyMapperTest {
    @Test
    fun fullRsaCrtMaterialSurvivesRestSdkRoundTrip() {
        val original = fixture()
        val mapped = original.toSdk()
        assertEquals(original.dq, mapped.dQ)
        val returned = mapped.toRest()
        assertEquals(original.n, returned.n)
        assertEquals(original.e, returned.e)
        assertEquals(original.d, returned.d)
        assertEquals(original.p, returned.p)
        assertEquals(original.q, returned.q)
        assertEquals(original.dp, returned.dp)
        assertEquals(original.dq, returned.dq)
        assertEquals(original.qi, returned.qi)
    }

    @Test
    fun restImportMappingProducesUsableRsa3072PrivateKey() {
        val imported = ResolvedKeyInfo(key = fixture(), keyVisibility = KeyVisibility.PRIVATE).toSdk()
        val key = convertToJavaPrivateKey(imported)
        val message = "RSA REST import regression".toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(key)
            update(message)
            sign()
        }
        assertTrue(Signature.getInstance("SHA256withRSA").run {
            initVerify(keyPair.public)
            update(message)
            verify(signature)
        })
    }

    @Test
    fun genuinelyMissingCrtExponentIsNotInventedByMapper() {
        val incomplete = ResolvedKeyInfo(key = fixture().copy(dq = null), keyVisibility = KeyVisibility.PRIVATE).toSdk()
        assertFailsWith<IllegalStateException> { convertToJavaPrivateKey(incomplete) }
    }

    @Test
    fun managedPublicResponseExcludesEveryPrivateRsaComponent() {
        val public = ManagedKeyInfo.fromKeyInfo(
            KeyInfo(key = fixture().toSdk(), alias = "rsa-test", providerId = "test-provider"),
        ).toRest().key
        assertEquals(fixture().n, public.n)
        assertEquals(fixture().e, public.e)
        assertNull(public.d)
        assertNull(public.p)
        assertNull(public.q)
        assertNull(public.dp)
        assertNull(public.dq)
        assertNull(public.qi)
    }

    private fun fixture(): Jwk {
        val key = keyPair.private as RSAPrivateCrtKey
        return Jwk(
            kty = JwkKeyType.RSA,
            n = key.modulus.base64Url(), e = key.publicExponent.base64Url(), d = key.privateExponent.base64Url(),
            p = key.primeP.base64Url(), q = key.primeQ.base64Url(), dp = key.primeExponentP.base64Url(),
            dq = key.primeExponentQ.base64Url(), qi = key.crtCoefficient.base64Url(),
        )
    }

    private fun BigInteger.base64Url(): String {
        val signed = toByteArray()
        val unsigned = if (signed.size > 1 && signed[0] == 0.toByte()) signed.copyOfRange(1, signed.size) else signed
        return Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned)
    }

    private companion object {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    }
}
