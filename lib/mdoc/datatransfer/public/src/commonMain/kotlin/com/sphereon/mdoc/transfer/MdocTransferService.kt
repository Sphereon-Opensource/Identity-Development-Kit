/*
 * © 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.mdoc.transfer

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.engagement.MdocEngagementEvent
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodec
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Type of engagement (QR, NFC, or TO_APP for reverse/app-to-app).
 *
 * ## URI Schemes by Engagement Type
 *
 * Different engagement types use different URI schemes per ISO specifications:
 *
 * ### QR Code Engagement (ISO 18013-5)
 * - **Scheme**: `mdoc:` (opaque URI, no slashes)
 * - **Format**: `mdoc:<base64url-of-DeviceEngagement>`
 * - **Use case**: Holder displays QR code for reader to scan
 * - **Data**: Contains DeviceEngagement (holder's ephemeral key and retrieval methods)
 *
 * ### App-to-App / Reverse Engagement
 * - **Scheme**:
 *   - `mdoc:` for classic reverse engagement (ISO 18013-5 BLE/NFC)
 *   - `mdoc://` for website retrieval (ISO 18013-7 Annex A)
 * - **Format**: `mdoc:<base64url-of-ReaderEngagement>` or `mdoc://<base64url-of-ReaderEngagement>`
 * - **Use case**: Reader displays QR code / deep link for holder to scan/invoke
 * - **Data**: Contains ReaderEngagement (reader's ephemeral key and retrieval methods)
 *
 * **Important**: These two schemes are NOT interchangeable. Each part of the standard
 * uses a specific one based on who initiates the engagement.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EngagementType", exact = true)
enum class EngagementType {
    /** ISO 18013-5 - NFC proximity-based engagement */
    NFC,

    /**
     * ISO 18013-5 - QR code-based engagement (holder displays QR).
     * Uses `mdoc:` scheme (no slashes).
     */
    QR,

    /**
     * App-to-app / Reverse engagement (reader displays QR).
     * Uses `mdoc:` for classic reverse engagement or `mdoc://` for website retrieval.
     */
    TO_APP,
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEngagementMethod", exact = true)
interface MdocEngagementMethod {
    val type: EngagementType
}

/**
 * QR code engagement method for holder-initiated engagement.
 *
 * Per ISO/IEC 18013-5, QR code engagement uses the opaque URI scheme `mdoc:` (no slashes).
 * This is used when the holder displays a QR code containing their DeviceEngagement.
 *
 * Format: `mdoc:<base64url-of-DeviceEngagement>`
 *
 * @param scheme The URI scheme (default: "mdoc:" per ISO 18013-5)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("QREngagementMethod", exact = true)
data class QREngagementMethod(
    val scheme: String = "mdoc:",
) : MdocEngagementMethod {
    override val type: EngagementType = EngagementType.QR
}

class NfcEngagementMethod : MdocEngagementMethod {
    override val type: EngagementType = EngagementType.NFC
}

/**
 * OID4VP engagement method for OAuth 2.0 based engagement (ISO 18013-7 Annex B).
 *
 * Per ISO 18013-7 B.3.1.3.2, OID4VP uses the `mdoc-openid4vp://` URI scheme for wallet invocation.
 * The URI format is:
 * ```
 * mdoc-openid4vp://?client_id=example.com&request_uri=https://example.com/request&response_uri=...&nonce=...
 * ```
 *
 * This differs from other engagement methods in that:
 * - It doesn't use CBOR-encoded device/reader engagement
 * - Parameters are passed via query parameters instead of base64url-encoded payload
 * - Protocol uses OAuth 2.0 / OpenID4VP flows with JWT instead of CBOR
 *
 * @param authorizationRequestUri The full `mdoc-openid4vp://` URI from the verifier's QR code or deep link
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpEngagementMethod", exact = true)
data class Oid4vpEngagementMethod(
    val authorizationRequestUri: String,
) : MdocEngagementMethod {
    override val type: EngagementType = EngagementType.TO_APP

    init {
        require(authorizationRequestUri.startsWith("mdoc-openid4vp://")) {
            "OID4VP Authorization Request URI must start with 'mdoc-openid4vp://' per ISO 18013-7 B.3.1.3.2"
        }
    }
}

/**
 * Reader engagement method for reader-initiated (reverse) engagement.
 *
 * Reader engagement uses the URI scheme based on retrieval type:
 * - `mdoc:` for classic reverse engagement (ISO 18013-5 BLE/NFC)
 * - `mdoc://` for website retrieval (ISO 18013-7 Annex A)
 *
 * This is used when the reader displays a QR code or deep link containing their ReaderEngagement.
 *
 * Format: `mdoc:<base64url-of-ReaderEngagement>` or `mdoc://<base64url-of-ReaderEngagement>`
 *
 * @param readerEngagement The reader engagement containing reader's ephemeral key and retrieval methods
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ReaderEngagementMethod", exact = true)
data class ReaderEngagementMethod(
    val readerEngagement: ReaderEngagement,
    private val readerEngagementCborCodec: ReaderEngagementCborCodec? = null,
) : MdocEngagementMethod {
    override val type: EngagementType = EngagementType.TO_APP

    init {
        require(!readerEngagement.deviceRetrievalMethods.isNullOrEmpty()) { "Reader engagement does not have retrieval methods" }
    }

    /**
     * Get the engagement URI for QR code display or deep linking.
     *
     * @return URI starting with `mdoc:` (classic reverse engagement) or `mdoc://` (website retrieval)
     */
    fun getEngagementDataUri(): String {
        val scheme =
            if (readerEngagement.hasWebsiteRetrievalMethod) {
                "mdoc://"
            } else {
                "mdoc:"
            }
        return requireNotNull(readerEngagementCborCodec) {
            "ReaderEngagementCborCodec must be provided to generate a ReaderEngagement URI"
        }.encodeUri(readerEngagement, scheme).getOrThrow()
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocTransferService", exact = true)
interface MdocTransferService {
    /**
     * Monad-first API: starts a QR engagement and returns an IdkResult with the engagement URI on success.
     * This is the preferred entry point for Swift/Objective‑C and cross-platform callers.
     */
    interface Try {
        suspend fun startQrEngagement(): IdkResult<String, IdkErrorType>
    }

    /**
     * Throwing API: exception-based convenience for Kotlin callers. Delegates to Try under the hood.
     */
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Throwing", exact = true)
    interface Throwing {
        suspend fun startQrEngagement(): String
    }

    fun tryOps(): Try

    fun throwingOps(): Throwing

    /**
     * Kotlin convenience API: starts a QR engagement and returns the URI, throwing on error.
     * Prefer Try in public/cross-platform code; use this in idiomatic Kotlin when exceptions are desired.
     */
    suspend fun startQrEngagement(): String

    fun isStarted(): Boolean

    fun getMdocRole(): MdocRole
}

//
//
@OptIn(ExperimentalObjCName::class)
@ObjCName("TransferState", exact = true)
// enum class TransferState() {
//    INIT, CONNECTING, DATA, ERROR, DONE
// }
//

enum class DataInstanceType {
    SINGLETON,
    PER_ENGAGEMENT,
}
/*
internal object SingletonTransfer : TransferInstance {
    override val engagement: EngagementInstance
        get() = TODO("Not yet implemented")
    override val type: DataInstanceType = DataInstanceType.SINGLETON
    override val id: Uuid = Uuid.random()
    override val transmissionTypesSupported: Set<DataRetrievalTransmissionType>
        get() = TODO("Not yet implemented")
    override var transmissionTypeSelected: DataRetrievalTransmissionType?
        get() = TODO("Not yet implemented")
        set(value) {}
    override val states: Flow<TransferState>
        get() = TODO("Not yet implemented")


}*/

@OptIn(ExperimentalObjCName::class)
@ObjCName("AbstractMdocTransferService", exact = true)
abstract class AbstractMdocTransferService(
    val keyManager: KeyManagerService,
    val retrievalMethods: Array<DeviceRetrievalMethod>,
    private val coseKeyCborCodec: CoseKeyCborCodec?,
    private val deviceEngagementCborCodec: DeviceEngagementCborCodec?,
    private val readerEngagementCborCodec: ReaderEngagementCborCodec?,
    val listeners: MutableSet<MdocEngagementEvent.Listener> = mutableSetOf<MdocEngagementEvent.Listener>(),
    val context: Any? = null,
//    holderCentral: MdocHolderCentralClientTransfer,
) : MdocEngagementEvent.Handlers {
    private constructor(
        keyManager: KeyManagerService,
        retrievalMethods: Array<DeviceRetrievalMethod>,
        coseKeyCborCodec: CoseKeyCborCodec?,
        deviceEngagementCborCodec: DeviceEngagementCborCodec?,
        readerEngagementCborCodec: ReaderEngagementCborCodec?,
        context: Any? = null,
        listeners: MutableSet<MdocEngagementEvent.Listener> = mutableSetOf<MdocEngagementEvent.Listener>(),
        engagementDataService: EngagementData,
//        holderCentral: MdocHolderCentralClientTransfer,
    ) : this(keyManager, retrievalMethods, coseKeyCborCodec, deviceEngagementCborCodec, readerEngagementCborCodec, listeners, context) {
        this.engagementDataService = engagementDataService
        this.started = true
    }

    //    var mdocBleService: MdocBleService? = null
    private var engagementDataService: EngagementData? = null

    private var started = false

    protected fun initializeEngagementData(engagementDataService: EngagementData) {
        this.engagementDataService = engagementDataService
        this.started = true
    }

    // TODO: Why not return the engagement service to begin with?
    protected suspend fun startQrEngagementImpl(): String {
        check(!started) { "Transfer has already been started" }
        val ephemeralDeviceKey =
            keyManager.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256).toManagedKeyInfo<CoseKeyType>(visibility = KeyVisibility.PRIVATE, keyEncoding = KeyEncoding.COSE)
        val engagementDataService =
            this.engagementDataService ?: EngagementData
                .holderBuilder(
                    coseKeyCborCodec = requireNotNull(coseKeyCborCodec) { "CoseKeyCborCodec must be provided to start QR engagement" },
                    deviceEngagementCborCodec = requireNotNull(deviceEngagementCborCodec) { "DeviceEngagementCborCodec must be provided to start QR engagement" },
                    readerEngagementCborCodec = readerEngagementCborCodec,
                ).withRetrievalMethods(retrievalMethods.toSet())
                .withEphemeralKey(ephemeralDeviceKey)
                .build()
        this.engagementDataService = engagementDataService
        check(engagementDataService.getRole() == MdocRole.MDOC) { "Only the holder can start a QR engagement" }
        val ble = engagementDataService.isBleRetrievalSupported()
        check(ble) { "BLE is not enabled for the engagement" }

        val engagementUri = engagementDataService.generateEngagementUri().also { this.started = true }
        return engagementUri
    }

    fun isStarted() = started

    fun getMdocRole() = engagementDataService?.getRole() ?: MdocRole.MDOC

    override fun addEngagementEventListener(vararg listener: MdocEngagementEvent.Listener) =
        apply {
            listeners.addAll(listener)
        }

    override fun removeEngagementEventListener(listener: MdocEngagementEvent.Listener) =
        apply {
            listeners.remove(listener)
        }

    override fun clearEngagementEventListeners() =
        apply {
            listeners.clear()
        }

    override fun getEngagementEventListeners(): Set<MdocEngagementEvent.Listener> = listeners.toSet()
}
