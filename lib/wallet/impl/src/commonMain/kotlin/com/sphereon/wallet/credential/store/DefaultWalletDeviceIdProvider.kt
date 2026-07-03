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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.Uuid
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.credential.WalletDeviceIdProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

private const val DEVICE_ID_CONTENT_TYPE = "text/plain"

/**
 * Blob-backed device identity used by hybrid operation logs.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletDeviceIdProvider>())
class BlobWalletDeviceIdProvider(
    private val blobService: BlobService,
) : WalletDeviceIdProvider {
    private var cachedDeviceId: String? = null

    override suspend fun deviceId(): IdkResult<String, IdkError> {
        cachedDeviceId?.let { return Ok(it) }

        val existing = blobService.getBlob(BlobInfo(path = walletDeviceIdPath()))
        if (existing.isOk) {
            val stored =
                existing.value.data
                    .decodeToString()
                    .trim()
            if (stored.isNotBlank()) {
                cachedDeviceId = stored
                return Ok(stored)
            }
        } else if (existing.error.code != "BLOB_NOT_FOUND") {
            return Err(existing.error)
        }

        val generated = "wallet-device-${Uuid.v4String()}"
        val stored =
            blobService.storeBlob(
                target =
                    BlobInfo(
                        path = walletDeviceIdPath(),
                        contentType = DEVICE_ID_CONTENT_TYPE,
                        metadata = mapOf("walletDeviceId" to generated),
                    ),
                data = generated.encodeToByteArray(),
            )
        if (stored.isErr) return Err(stored.error)
        cachedDeviceId = generated
        return Ok(generated)
    }
}

internal fun walletDeviceIdPath(): String = "wallet-device/device-id"
