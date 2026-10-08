/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.KeyAgreementAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyWrapAlgorithm
import com.sphereon.crypto.kms.rest.api.generated.infrastructure.Base64ByteArray
import com.sphereon.crypto.kms.rest.api.generated.models.DecryptRequest
import com.sphereon.crypto.kms.rest.api.generated.models.DecryptResponse
import com.sphereon.crypto.kms.rest.api.generated.models.EncryptRequest
import com.sphereon.crypto.kms.rest.api.generated.models.EncryptResponse
import com.sphereon.crypto.kms.rest.api.generated.models.KeyAgreementRequest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyAgreementResponse
import com.sphereon.crypto.kms.rest.api.generated.models.UnwrapKeyRequest
import com.sphereon.crypto.kms.rest.api.generated.models.UnwrapKeyResponse
import com.sphereon.crypto.kms.rest.api.generated.models.WrapKeyRequest
import com.sphereon.crypto.kms.rest.api.generated.models.WrapKeyResponse
import com.sphereon.crypto.kms.rest.api.mapper.toSdk
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
@ContributesBinding(SessionScope::class, binding = binding<EncryptionRestService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("EncryptionRestServiceImpl", exact = true)
class EncryptionRestServiceImpl(
    private val kms: KeyManagerService,
) : EncryptionRestService {
    override suspend fun encrypt(request: EncryptRequest): EncryptResponse {
        val result =
            kms.encryptResult(
                keyInfo = request.keyInfo.toSdk(),
                plaintext = request.plaintext.value,
                algorithm = ContentEncryptionAlgorithm.valueOf(request.algorithm.name),
                additionalAuthenticatedData = request.additionalAuthenticatedData?.value,
            )
        val encrypted = result.getOrElse { throw IllegalArgumentException(it.message.defaultMessage ?: "Encryption failed") }
        return EncryptResponse(
            ciphertext = Base64ByteArray(encrypted.ciphertext),
            iv = Base64ByteArray(encrypted.iv),
            authTag = Base64ByteArray(encrypted.authTag),
        )
    }

    override suspend fun decrypt(request: DecryptRequest): DecryptResponse {
        val result =
            kms.decryptResult(
                keyInfo = request.keyInfo.toSdk(),
                ciphertext = request.ciphertext.value,
                algorithm = ContentEncryptionAlgorithm.valueOf(request.algorithm.name),
                iv = request.iv.value,
                authTag = request.authTag.value,
                additionalAuthenticatedData = request.additionalAuthenticatedData?.value,
            )
        val decrypted = result.getOrElse { throw IllegalArgumentException(it.message.defaultMessage ?: "Decryption failed") }
        return DecryptResponse(plaintext = Base64ByteArray(decrypted.plaintext))
    }

    override suspend fun wrapKey(request: WrapKeyRequest): WrapKeyResponse {
        val result =
            kms.wrapKeyResult(
                wrappingKeyInfo = request.wrappingKeyInfo.toSdk(),
                keyToWrap = request.keyToWrap.value,
                algorithm = KeyWrapAlgorithm.valueOf(request.algorithm.name),
            )
        val wrapped = result.getOrElse { throw IllegalArgumentException(it.message.defaultMessage ?: "Key wrap failed") }
        return WrapKeyResponse(wrappedKey = Base64ByteArray(wrapped.wrappedKey))
    }

    override suspend fun unwrapKey(request: UnwrapKeyRequest): UnwrapKeyResponse {
        val result =
            kms.unwrapKeyResult(
                unwrappingKeyInfo = request.unwrappingKeyInfo.toSdk(),
                wrappedKey = request.wrappedKey.value,
                algorithm = KeyWrapAlgorithm.valueOf(request.algorithm.name),
            )
        val unwrapped = result.getOrElse { throw IllegalArgumentException(it.message.defaultMessage ?: "Key unwrap failed") }
        return UnwrapKeyResponse(unwrappedKey = Base64ByteArray(unwrapped.unwrappedKey))
    }

    override suspend fun performKeyAgreement(request: KeyAgreementRequest): KeyAgreementResponse {
        val result =
            kms.performKeyAgreementResult(
                privateKeyInfo = request.privateKeyInfo.toSdk(),
                publicKeyInfo = request.publicKeyInfo.toSdk(),
                algorithm = KeyAgreementAlgorithm.valueOf(request.algorithm.name),
                keyDataLen = request.keyDataLen,
            )
        val agreed = result.getOrElse { throw IllegalArgumentException(it.message.defaultMessage ?: "Key agreement failed") }
        return KeyAgreementResponse(sharedSecret = Base64ByteArray(agreed.sharedSecret))
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val encryptionRestService: EncryptionRestService
    }
}
