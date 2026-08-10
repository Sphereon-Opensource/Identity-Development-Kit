package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ManagedKeyReferenceFilter
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateChainFromX5c
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KmsRestService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("KmsRestServiceImpl", exact = true)
class KmsRestServiceImpl(
    private val kms: KeyManagerService,
) : KmsRestService {
    override suspend fun getKey(
        aliasOrKid: String,
        providerId: String?,
    ): ManagedKeyInfoType<*> =
        runCatching {
            kms.getKey(KeyInfo<Jwk>(alias = aliasOrKid, providerId = providerId))
        }.recoverCatching {
            kms.getKey(KeyInfo<Jwk>(kid = aliasOrKid, providerId = providerId))
        }.getOrElse { exception ->
            when (exception) {
                is PKIException,
                is IllegalArgumentException,
                is NotFoundException,
                -> throw NotFoundException(resource = aliasOrKid)

                else -> throw exception
            }
        }

    override suspend fun listKeys(providerId: String?): Array<ManagedKeyReference> {
        if (providerId != null) {
            // Validate the provider exists before filtering
            runCatching { kms.getProviderById(providerId) }.getOrElse { exception ->
                when (exception) {
                    is PKIException -> throw NotFoundException(resource = providerId)
                    else -> throw exception
                }
            }
            return kms.listKeys(ManagedKeyReferenceFilter(providerId = providerId))
        }

        return kms.listKeys()
    }

    override suspend fun storeKey(
        keyInfo: ResolvedKeyInfoType<*>,
        certChain: Array<String>?,
    ): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: keyInfo.kid ?: (keyInfo.key as? Jwk)?.kid
        if (alias == null) {
            throw IllegalArgumentException(
                "Either an alias or kid needs to be provided.",
            )
        }

        val certChain: Array<Certificate>? = certChain?.let { certificateChainFromX5c(it) }

        return kms.storeKey(
            keyInfo = keyInfo,
            providerId = keyInfo.providerId ?: kms.defaultProviderId(),
            alias = alias,
            certChain = certChain,
        )
    }

    override suspend fun generateKey(
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<KeyOperations>?,
        alg: SignatureAlgorithm?,
        providerId: String?,
    ): ManagedKeyPair =
        kms.generateKeyAsync(
            alias = alias,
            providerId = providerId ?: kms.defaultProviderId(),
            use = use ?: JwkUse.sig,
            keyOperations = keyOperations,
            alg = alg,
        )

    override suspend fun deleteKey(
        aliasOrKid: String,
        providerId: String?,
    ): Boolean {
        val key = getKey(aliasOrKid, providerId)
        val keyInfo: KeyInfoType<Jwk> =
            KeyInfo(
                kid = key.kid,
                alias = key.alias,
                providerId = key.providerId,
            )

        return kms.deleteKey(keyInfo)
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val kmsRestService: KmsRestService
    }
}
