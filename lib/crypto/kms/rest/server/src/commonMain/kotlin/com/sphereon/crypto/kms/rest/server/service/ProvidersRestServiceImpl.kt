package com.sphereon.crypto.kms.rest.server.service

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.error.NotFoundException
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.PKIException
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
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ProvidersRestService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("ProvidersRestServiceImpl", exact = true)
class ProvidersRestServiceImpl(
    private val kms: KeyManagerService
) : ProvidersRestService {
    override suspend fun listKeyProviders(): Array<KmsProvider> {
        val providerIds = kms.getProviderIds()

        return providerIds
            .map { it -> kms.getProviderById(it) }
            .toTypedArray()
    }

    override suspend fun getKeyProvider(providerId: String): KmsProvider {
        return runCatching {
            kms.getProviderById(providerId)
        }.getOrElse { exception ->
            when (exception) {
                is PKIException -> throw NotFoundException(
                    resource = providerId,
                    message = "Provider with id '$providerId' not found."
                )
                else -> throw exception
            }
        }
    }

    override suspend fun providerListKeys(providerId: String): Array<ManagedKeyInfoType<*>> {
        val provider = getKeyProvider(providerId)

        return provider.listKeys()
    }

    override suspend fun providerStoreKey(
        providerId: String,
        keyInfo: ResolvedKeyInfoType<*>,
        certChain: Array<String>?
    ): ManagedKeyInfoType<*> {
        val alias = keyInfo.alias ?: keyInfo.kid ?: (keyInfo.key as? Jwk)?.kid
        if (alias == null) {
            throw IllegalArgumentException("Either an alias or kid needs to be provided.")
        }

        val provider = getKeyProvider(providerId)
        val certChain: Array<Certificate>? = certChain?.let { certificateChainFromX5c(it) }


        return provider.storeKey(
            keyInfo = keyInfo,
            providerId = providerId,
            alias = alias,
            certChain = certChain
        )
    }

    override suspend fun providerGenerateKey(
        providerId: String,
        alias: String?,
        use: JwkUse?,
        keyOperations: Array<KeyOperations>?,
        alg: SignatureAlgorithm?
    ): ManagedKeyPair {
        val provider = getKeyProvider(providerId)

        return provider.generateKeyAsync(
            alias = alias,
            use = use,
            keyOperations = keyOperations,
            alg = alg
        )
    }

    override suspend fun providerGetKey(
        providerId: String,
        aliasOrKid: String
    ): ManagedKeyInfoType<*> {
        val provider = getKeyProvider(providerId)

        // Always search by alias first as that is a required value in the Managed/Stored key info. The kid is application-specific.
        return runCatching {
            val keyInfo: KeyInfoType<Jwk> = KeyInfo(
                alias = aliasOrKid
            )
            provider.getKey(keyInfo)
        }.getOrElse { exception ->
            return runCatching {
                val keyInfo: KeyInfoType<Jwk> = KeyInfo(
                    kid = aliasOrKid
                )
                provider.getKey(keyInfo)
            }.getOrElse { exception ->
                when (exception) {
                    is IllegalArgumentException -> throw NotFoundException(
                        resource = aliasOrKid,
                        message = "Key with alias or kid '$aliasOrKid' not found."
                    )
                    else -> throw exception
                }
            }
        }
    }

    override suspend fun providerDeleteKey(providerId: String, aliasOrKid: String): Boolean {
        val key = providerGetKey(providerId, aliasOrKid)
        val provider = getKeyProvider(providerId)
        val keyInfo: KeyInfoType<Jwk> = KeyInfo(
            kid = key.kid,
            alias = key.alias,
            providerId = key.providerId,
        )

        return provider.deleteKey(keyInfo)
    }

    @ContributesTo(SessionScope::class)
    interface Component {
        val providersRestService: ProvidersRestService
    }
}

