/*
 * Copyright 2026 Sphereon International B.V.
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
 */

package com.sphereon.wallet.credential.store

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.error.IdkError

internal object TestWalletCredentialBodyProtector : WalletCredentialBodyProtector {
    override suspend fun protect(
        walletUnitId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
        plaintext: ByteArray,
    ): IdkResult<ByteArray, IdkError> = Ok(("test-protected:" + plaintext.reversedArray().encodeTo(Encoding.BASE64URL)).encodeToByteArray())

    override suspend fun open(
        walletUnitId: String,
        credentialRecordId: String,
        credentialInstanceId: String,
        protectedBody: ByteArray,
    ): IdkResult<ByteArray, IdkError> {
        val encoded = protectedBody.decodeToString().removePrefix("test-protected:")
        if (encoded == protectedBody.decodeToString()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unsupported test credential body envelope"))
        }
        return Ok(encoded.decodeFrom(Encoding.BASE64URL).reversedArray())
    }
}
