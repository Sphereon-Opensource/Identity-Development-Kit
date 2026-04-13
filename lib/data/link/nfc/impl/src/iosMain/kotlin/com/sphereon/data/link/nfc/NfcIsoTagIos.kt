package com.sphereon.data.link.nfc

import com.sphereon.core.util.toByteArray
import com.sphereon.core.util.toKotlinError
import com.sphereon.core.util.toNSData
import com.sphereon.data.link.nfc.model.CommandApdu
import com.sphereon.data.link.nfc.model.NfcIsoTag
import com.sphereon.data.link.nfc.model.NfcTagLostException
import com.sphereon.data.link.nfc.model.ResponseApdu
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreNFC.NFCISO7816APDU
import platform.CoreNFC.NFCISO7816TagProtocol
import platform.CoreNFC.NFCErrorDomain
import platform.CoreNFC.NFCReaderTransceiveErrorTagResponseError
import kotlin.coroutines.resumeWithException

internal class NfcIsoTagIos(val tag: NFCISO7816TagProtocol): NfcIsoTag() {
    companion object {
        private const val TAG = "NfcIsoTagIos"
    }

    // Note: there's currently no API to obtain this value on iOS.
    //
    override val maxTransceiveLength: Int
        get() = 0xfeff

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun transceive(command: CommandApdu): ResponseApdu {
        return suspendCancellableCoroutine<ResponseApdu> { continuation ->
            val encodedCommand = command.encode()

            check(encodedCommand.size <= maxTransceiveLength) {
                "APDU is ${encodedCommand.size} bytes which exceeds maxTransceiveLength of $maxTransceiveLength bytes"
            }

            val apdu = NFCISO7816APDU(encodedCommand.toNSData())
            tag.sendCommandAPDU(apdu, { responseData, sw1, sw2, error ->
                if (error != null) {
                    val kotlinError = if (error.domain == NFCErrorDomain &&
                        error.code == NFCReaderTransceiveErrorTagResponseError) {
                        NfcTagLostException("Tag was lost during transceive", error.toKotlinError())
                    } else {
                        error.toKotlinError()
                    }
                    continuation.resumeWithException(kotlinError)
                } else {
                    val responseBytes = responseData?.toByteArray() ?: byteArrayOf()
                    val responseApduData = responseBytes + byteArrayOf(sw1.toByte(), sw2.toByte())
                    val responseApdu = ResponseApdu.decode(responseApduData)
                    continuation.resume(responseApdu, null)
                }
            })
        }
    }
}
