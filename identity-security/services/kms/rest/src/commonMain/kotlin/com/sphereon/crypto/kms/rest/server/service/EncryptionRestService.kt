/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

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
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("EncryptionRestService", exact = true)
interface EncryptionRestService {
    suspend fun encrypt(request: EncryptRequest): EncryptResponse

    suspend fun decrypt(request: DecryptRequest): DecryptResponse

    suspend fun wrapKey(request: WrapKeyRequest): WrapKeyResponse

    suspend fun unwrapKey(request: UnwrapKeyRequest): UnwrapKeyResponse

    suspend fun performKeyAgreement(request: KeyAgreementRequest): KeyAgreementResponse
}
