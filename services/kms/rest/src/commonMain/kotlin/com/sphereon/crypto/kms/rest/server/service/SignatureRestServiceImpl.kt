package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.kms.KeyManagerService
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
@ContributesBinding(SessionScope::class, binding = binding<SignatureRestService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("SignatureRestServiceImpl", exact = true)
class SignatureRestServiceImpl(
    private val kms: KeyManagerService,
) : SignatureRestService {
    override suspend fun createRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
    ): ByteArray {
        val signature =
            kms.createRawSignature(
                keyInfo = keyInfo,
                input = input,
                requireX5Chain = true,
            )

        return signature
    }

    override suspend fun isValidRawSignature(
        keyInfo: KeyInfoType<*>,
        input: ByteArray,
        signature: ByteArray,
    ): Boolean =
        runCatching {
            kms.isValidRawSignature(
                keyInfo = keyInfo,
                input = input,
                signature = signature,
            )
        }.getOrElse { exception ->
            when (exception) {
                is IllegalStateException -> false
                else -> throw exception
            }
        }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val signatureRestService: SignatureRestService
    }
}
