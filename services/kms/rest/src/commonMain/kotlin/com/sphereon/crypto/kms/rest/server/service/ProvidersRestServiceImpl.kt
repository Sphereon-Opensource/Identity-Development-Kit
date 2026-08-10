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
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateChainFromX5c
import com.sphereon.crypto.core.x509.certificateFromDer
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
@ContributesBinding(SessionScope::class, binding = binding<ProvidersRestService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProvidersRestServiceImpl", exact = true)
class ProvidersRestServiceImpl(
    private val kms: KeyManagerService,
    private val exposure: KmsProviderExposure,
) : ProvidersRestService {
    override suspend fun listKeyProviders(): Array<KmsProvider> {
        val providerIds = exposure.visibleProviderIds(kms.getProviderIds())

        return providerIds
            .map { it -> kms.getProviderById(it) }
            .toTypedArray()
    }

    override suspend fun getKeyProvider(providerId: String): KmsProvider =
        runCatching {
            if (!exposure.isPublic(providerId)) {
                throw NotFoundException(
                    resource = providerId,
                    message = "Provider with id '$providerId' not found.",
                )
            }
            kms.getProviderById(providerId)
        }.getOrElse { exception ->
            when (exception) {
                is PKIException -> throw NotFoundException(
                    resource = providerId,
                    message = "Provider with id '$providerId' not found.",
                )

                else -> throw exception
            }
        }

    override suspend fun providerListKeys(providerId: String): Array<ManagedKeyReference> {
        getKeyProvider(providerId)
        return kms.listKeys(ManagedKeyReferenceFilter(providerId = providerId))
    }

    override suspend fun providerImportKey(
        providerId: String,
        keyInfo: ResolvedKeyInfoType<*>,
        certChain: Array<String>?,
    ): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: keyInfo.kid ?: (keyInfo.key as? Jwk)?.kid
        require(alias != null) { "Either an alias or kid needs to be provided." }

        getKeyProvider(providerId)
        val certChain: Array<Certificate>? = certChain?.let { certificateChainFromX5c(it) }

        return kms.storeKey(
            keyInfo = keyInfo,
            providerId = providerId,
            alias = alias,
            certChain = certChain,
        )
    }

    override suspend fun providerGenerateKey(
        providerId: String,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<KeyOperations>?,
        alg: SignatureAlgorithm?,
    ): ManagedKeyPair {
        getKeyProvider(providerId)
        return kms.generateKey(
            providerId = providerId,
            alias = alias,
            use = use ?: JwkUse.sig,
            keyOperations = keyOperations,
            alg = alg,
        )
    }

    override suspend fun providerGetKey(
        providerId: String,
        aliasOrKid: String,
    ): ManagedKeyInfoType<*> {
        getKeyProvider(providerId)

        // Always search by alias first as that is a required value in the Managed/Stored key info. The kid is application-specific.
        return runCatching {
            val keyInfo: KeyInfoType<Jwk> =
                KeyInfo(
                    alias = aliasOrKid,
                    providerId = providerId,
                )
            kms.getKey(keyInfo)
        }.getOrElse { exception ->
            return runCatching {
                val keyInfo: KeyInfoType<Jwk> =
                    KeyInfo(
                        kid = aliasOrKid,
                        providerId = providerId,
                    )
                kms.getKey(keyInfo)
            }.getOrElse { exception ->
                when (exception) {
                    is IllegalArgumentException,
                    is NotFoundException,
                    is PKIException,
                    -> throw NotFoundException(
                        resource = aliasOrKid,
                        message = "Key with alias or kid '$aliasOrKid' not found.",
                    )

                    else -> throw exception
                }
            }
        }
    }

    override suspend fun providerDeleteKey(
        providerId: String,
        aliasOrKid: String,
    ): Boolean {
        val key = providerGetKey(providerId, aliasOrKid)
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
        val providersRestService: ProvidersRestService
    }
}
