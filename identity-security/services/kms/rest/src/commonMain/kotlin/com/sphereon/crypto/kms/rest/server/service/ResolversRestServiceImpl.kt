package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.api.error.NotFoundException
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.PKIException
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.model.IdentifierMethod
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
@ContributesBinding(SessionScope::class, binding = binding<ResolversRestService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolversRestServiceImpl", exact = true)
class ResolversRestServiceImpl(
    private val kms: KeyManagerService,
) : ResolversRestService {
    override suspend fun listResolvers(): Array<KeyResolverService> {
        val resolverIds = kms.getResolverIds()

        return resolverIds
            .map { it -> kms.getResolverById(it) }
            .toTypedArray()
    }

    override suspend fun getResolver(resolverId: String): KeyResolverService =
        runCatching {
            kms.getResolverById(resolverId)
        }.getOrElse { exception ->
            when (exception) {
                is PKIException -> throw NotFoundException(resolverId)
                else -> throw exception
            }
        }

    override suspend fun resolveKey(
        resolverId: String,
        keyInfo: KeyInfoType<*>,
        identifierMethod: IdentifierMethod?,
        trustedCerts: Array<String>?,
        verifyX509CertificateChain: Boolean?,
    ): ResolvedKeyInfoType<*> {
        val resolver = getResolver(resolverId)

        return resolver.resolvePublicKey(
            keyInfo = keyInfo,
            identifierMethod = identifierMethod,
            trustedCerts = trustedCerts,
            verifyX509CertificateChain = verifyX509CertificateChain,
        )
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val resolversRestService: ResolversRestService
    }
}
