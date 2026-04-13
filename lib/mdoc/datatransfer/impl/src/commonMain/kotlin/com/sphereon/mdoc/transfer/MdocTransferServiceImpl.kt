package com.sphereon.mdoc.transfer

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.engagement.QREngagementMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocTransferServiceImpl", exact = true)
class MdocTransferServiceImpl(
    keyManager: KeyManagerService,
    retrievalMethods: Array<DeviceRetrievalMethod>,
    listeners: MutableSet<MdocEngagementEvent.Listener> = mutableSetOf<MdocEngagementEvent.Listener>(),
//    holderCentral: MdocHolderCentralClientTransfer,
    context: Any? = null,
) : AbstractMdocTransferService(keyManager = keyManager, retrievalMethods = retrievalMethods, listeners = listeners/*, holderCentral = holderCentral*/, context = context),
    MdocTransferService {

    private constructor(
        keyManager: KeyManagerService,
        retrievalMethods: Array<DeviceRetrievalMethod>,
        context: Any? = null,
        listeners: MutableSet<MdocEngagementEvent.Listener> = mutableSetOf<MdocEngagementEvent.Listener>(),
        data: EngagementData,
        // holderCentral: MdocHolderCentralClientTransfer,  // OLD: Replaced by modular transports
        // FIXME: Central
    ) : this(keyManager, retrievalMethods, listeners, /*holderCentral,*/ context)


    override fun tryOps(): MdocTransferService.Try = object : MdocTransferService.Try {
        override suspend fun startQrEngagement(): IdkResult<String, IdkErrorType> = try {
            IdkOkResult(startQrEngagementImpl())
        } catch (t: Throwable) {
            IdkErrorResult(
                IdkError.Companion.UNKNOWN_ERROR(exception = t)
            )
        }
    }

    override fun throwingOps(): MdocTransferService.Throwing = object : MdocTransferService.Throwing {
        override suspend fun startQrEngagement(): String {
            val res = tryOps().startQrEngagement()
            return if (res.isOk) res.value else throw ((res.error as? IdkError)?.exception
                ?: IllegalStateException(
                    (res.error as? IdkError)?.message?.defaultMessage
                        ?: res.error.toString()
                ))
        }
    }

    override suspend fun startQrEngagement(): String {
        return throwingOps().startQrEngagement()
    }

    companion object {

        fun asVerifierFromQrEngagement(
            engagementUri: String,
            context: Any? = null,
            // holderCentral: MdocHolderCentralClientTransfer, // OLD: Replaced by modular transports
            retrievalMethods: Array<DeviceRetrievalMethod> = arrayOf(),
            keyManager: KeyManagerService,
            vararg listeners: MdocEngagementEvent.Listener,
        ): MdocTransferService {
            val deviceEngagement = DeviceEngagement.Decoder.fromEngagementUri(engagementUri)
            val engagementService = EngagementData.Companion.verifierFromDeviceEngagement(CborEncodedItem.Companion.fromData(deviceEngagement), setOf(QREngagementMethod()))
            val ble = engagementService.isBleRetrievalSupported()
            check(ble) { "BLE is not enabled for the engagement" }

            val transferService = MdocTransferServiceImpl(
                retrievalMethods = retrievalMethods,
                keyManager = keyManager,
                context = context,
                listeners = mutableSetOf(*listeners),
                data = engagementService,
                // holderCentral = holderCentral  // OLD: Replaced by modular transports
            )


//            transferService.sendEvent(EngagementEvent.QrEngagement(MdocRole.MDOC_READER, qrCodeData, deviceEngagement, ble))
            if (ble) {
                checkNotNull(engagementService.getBleCentralClientModeUuid()) { "BLE central client mode UUID is missing from engagement" }
                // TODO: Enable GATT Server


            }
            // TODO check
            /*   transferService.sendEvent(
                   EngagementEvent.Connecting(
                       MdocRole.MDOC_READER, engagementService.getBleCentralClientModeUuid()?.toString() ?: throw IllegalStateException("Mdoc uuid expected in message: $qrCodeData")
                   )
               )*/
            return transferService
        }

    }

}