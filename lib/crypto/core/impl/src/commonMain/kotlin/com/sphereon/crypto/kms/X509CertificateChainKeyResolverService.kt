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
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.kms.AbstractKeyResolverService
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.PublicKeyResolver
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.di.session.SessionScope

/**
 * Service for resolving public keys from X.509 certificate chains.
 *
 * This session is used to resolve and verify the leaf public key contained within
 * X.509 certificate chains.
 *
 */

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<KeyResolverService>())
@ContributesIntoSet(SessionScope::class, binding = binding<PublicKeyResolver>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("X509CertificateChainKeyResolverServiceImpl", exact = true)
class X509CertificateChainKeyResolverServiceImpl(val x509VerifyService: X509VerifyService) : AbstractKeyResolverService(
    id = "x5c", supported = mapOf(
        Pair(IdentifierMethod.x5c, arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA)),
        Pair(IdentifierMethod.jwk, arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA)),
        Pair(IdentifierMethod.cose_key, arrayOf(KeyTypeMapping.EC, KeyTypeMapping.RSA)),
    )
), KeyResolverService {
    @Suppress("UNCHECKED_CAST")
    override suspend fun <KT : KeyType> resolvePublicKey(
        keyInfo: KeyInfoType<KT>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?
    ): ResolvedKeyInfoType<KT> {
        val x509 = keyInfo.x5c ?: keyInfo.key?.getX509CertificateChain() ?: throw IllegalArgumentException("X509 chain not present")
        val x509Result = x509VerifyService.verifyCertificateChain(
            X509VerificationRequest(
                chainDER = x509.map { it.decodeFrom(Encoding.BASE64) }.toTypedArray()
            )
        )
        val leafKeyJwk = x509Result.publicKey ?: throw PKIException("No public key could be extracted from the provided certification chain")
        val leafKey = if (keyInfo.keyEncoding === KeyEncoding.COSE) CoseJoseKeyMappingService.toCoseKey(leafKeyJwk) else leafKeyJwk
        val updateKeyInfo = KeyInfo.fromDTO(keyInfo).copy(kid = keyInfo.kid ?: leafKey.getKeyId(true), key = leafKey as KT)
        return ResolvedKeyInfo.fromKeyInfo(updateKeyInfo, leafKey)
    }
}


