/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc.transfer

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.engagement.QREngagementMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodec
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocTransferServiceImpl", exact = true)
class MdocTransferServiceImpl(
    keyManager: KeyManagerService,
    retrievalMethods: Array<DeviceRetrievalMethod>,
    coseKeyCborCodec: CoseKeyCborCodec? = null,
    deviceEngagementCborCodec: DeviceEngagementCborCodec? = null,
    readerEngagementCborCodec: ReaderEngagementCborCodec? = null,
    listeners: MutableSet<MdocEngagementEvent.Listener> = mutableSetOf<MdocEngagementEvent.Listener>(),
//    holderCentral: MdocHolderCentralClientTransfer,
    context: Any? = null,
) : AbstractMdocTransferService(
        keyManager = keyManager,
        retrievalMethods = retrievalMethods,
        coseKeyCborCodec = coseKeyCborCodec,
        deviceEngagementCborCodec = deviceEngagementCborCodec,
        readerEngagementCborCodec = readerEngagementCborCodec,
        listeners = listeners,
        context = context,
    ),
    MdocTransferService {
    private constructor(
        keyManager: KeyManagerService,
        retrievalMethods: Array<DeviceRetrievalMethod>,
        coseKeyCborCodec: CoseKeyCborCodec? = null,
        deviceEngagementCborCodec: DeviceEngagementCborCodec? = null,
        readerEngagementCborCodec: ReaderEngagementCborCodec? = null,
        context: Any? = null,
        listeners: MutableSet<MdocEngagementEvent.Listener> = mutableSetOf<MdocEngagementEvent.Listener>(),
        data: EngagementData,
        // holderCentral: MdocHolderCentralClientTransfer,  // OLD: Replaced by modular transports
        // FIXME: Central
    ) : this(keyManager, retrievalMethods, coseKeyCborCodec, deviceEngagementCborCodec, readerEngagementCborCodec, listeners, context) {
        initializeEngagementData(data)
    }

    override fun tryOps(): MdocTransferService.Try =
        object : MdocTransferService.Try {
            override suspend fun startQrEngagement(): IdkResult<String, IdkErrorType> =
                try {
                    IdkOkResult(startQrEngagementImpl())
                } catch (expected: Throwable) {
                    IdkErrorResult(
                        IdkError.Companion.UNKNOWN_ERROR(exception = expected),
                    )
                }
        }

    override fun throwingOps(): MdocTransferService.Throwing =
        object : MdocTransferService.Throwing {
            override suspend fun startQrEngagement(): String {
                val res = tryOps().startQrEngagement()
                return if (res.isOk) {
                    res.value
                } else {
                    throw (
                        (res.error as? IdkError)?.exception
                            ?: IllegalStateException(
                                (res.error as? IdkError)?.message?.defaultMessage
                                    ?: res.error.toString(),
                            )
                    )
                }
            }
        }

    override suspend fun startQrEngagement(): String = throwingOps().startQrEngagement()

    companion object {
        fun asVerifierFromQrEngagement(
            engagementUri: String,
            context: Any? = null,
            retrievalMethods: Array<DeviceRetrievalMethod> = arrayOf(),
            keyManager: KeyManagerService,
            deviceEngagementCborCodec: DeviceEngagementCborCodec,
            vararg listeners: MdocEngagementEvent.Listener,
        ): MdocTransferService {
            check(engagementUri.startsWith("mdoc:")) { "Device Engagement URI must start with 'mdoc:' per ISO 18013-5. We got: $engagementUri" }

            val deviceEngagementBytes = engagementUri.substring(5).decodeFromBase64Url()
            val decodedDeviceEngagement = deviceEngagementCborCodec.decode(deviceEngagementBytes).getOrThrow()
            val engagementService =
                EngagementData.verifierFromDeviceEngagement(
                    encodedEngagement = CborEncodedItem(decodedDeviceEngagement.originalBytes, decodedDeviceEngagement.value),
                    engagementMethods = setOf(QREngagementMethod()),
                )
            val ble = engagementService.isBleRetrievalSupported()
            check(ble) { "BLE is not enabled for the engagement" }

            val transferService =
                MdocTransferServiceImpl(
                    retrievalMethods = retrievalMethods,
                    keyManager = keyManager,
                    coseKeyCborCodec = null,
                    deviceEngagementCborCodec = deviceEngagementCborCodec,
                    readerEngagementCborCodec = null,
                    context = context,
                    listeners = mutableSetOf(*listeners),
                    data = engagementService,
                )

            if (ble) {
                checkNotNull(engagementService.getBleCentralClientModeUuid()) { "BLE central client mode UUID is missing from engagement" }
            }
            return transferService
        }
    }
}
