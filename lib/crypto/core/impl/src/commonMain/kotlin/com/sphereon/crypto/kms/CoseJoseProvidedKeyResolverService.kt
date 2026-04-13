/*
 * © 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.kms

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.kms.AbstractKeyResolverService
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.PublicKeyResolver
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.di.session.SessionScope

/**
 * A session for resolving provided keys specifically for JOSE (JSON Object Signing and Encryption)
 * and COSE (CBOR Object Signing and Encryption).
 *
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<KeyResolverService>())
@ContributesIntoSet(SessionScope::class, binding = binding<PublicKeyResolver>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("CoseJoseProvidedKeyResolverServiceImpl", exact = true)
class CoseJoseProvidedKeyResolverServiceImpl(val x509VerifyService: X509VerifyService) : AbstractKeyResolverService(
    id = "jose_cose_resolver", supported = mapOf(
        Pair(IdentifierMethod.jwk, KeyTypeMapping.asList.toTypedArray()),
        Pair(IdentifierMethod.cose_key, KeyTypeMapping.asList.toTypedArray())
    )
), KeyResolverService {
    override suspend fun <KT : KeyType> resolvePublicKey(
        keyInfo: KeyInfoType<KT>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?
    ): ResolvedKeyInfoType<KT> {
        require(keyInfo.key !== null && keyInfo.key is KT) { "Jose-cose key resolver only accepts Jwk or cose keys in the key info object" }
        require(identifierMethod === null || identifierMethod == IdentifierMethod.jwk || identifierMethod == IdentifierMethod.cose_key) { "Cannot use an identifier method other than jwk for the jwk key resolver" }

        val resolvedKeyInfo = ResolvedKeyInfo.fromKeyInfo<KT>(keyInfo).toResolvedPublicKeyInfo()
        val certs = trustedCerts ?: x509VerifyService.getTrustedCerts()
        val chain = resolvedKeyInfo.key.getX509CertificateChain()
        if (verifyX509CertificateChain == true && !certs.isNullOrEmpty() && !chain.isNullOrEmpty()) {

            val x509Result = x509VerifyService.verifyCertificateChain(
                X509VerificationRequest(
                    chainDER = chain.map { it.decodeFrom(Encoding.BASE64) }.toTypedArray(),
                    trustedCerts = certs
                )
            )
            return resolvedKeyInfo.copy( x5c = x509Result.certificateChain.map { it.derToBase64() }.toTypedArray())
        }
        return resolvedKeyInfo
    }
}