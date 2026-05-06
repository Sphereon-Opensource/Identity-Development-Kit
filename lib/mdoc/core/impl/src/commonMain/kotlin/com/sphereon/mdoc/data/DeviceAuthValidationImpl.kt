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
 */

package com.sphereon.mdoc.data

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.toCborByteString
import com.sphereon.cbor.toCborItem
import com.sphereon.crypto.core.CoseCryptoService
import com.sphereon.crypto.core.CryptoConst
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.VerifySignatureResult
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocConst
import com.sphereon.mdoc.SessionTranscriptCborCodec
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceSignedItems
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodec
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Default implementation of [DeviceAuthValidation] for the OID4VP/ISO 18013-5 verification path.
 *
 * Signature mode (COSE_Sign1):
 *
 * 1. Extract `deviceSigned.deviceAuth.deviceSignature`. Reject if absent.
 * 2. Reject `deviceMac` (COSE_Mac0) — OID4VP has no shared secret to derive an EMacKey.
 * 3. Decode the MSO from the document's IssuerAuth and extract the device public key.
 * 4. Reconstruct the `DeviceAuthentication` CBOR payload that the holder signed:
 *    `["DeviceAuthentication", expectedSessionTranscript, docType, deviceNameSpaces]`,
 *    where `deviceNameSpaces` come straight from `document.deviceSigned.nameSpaces`.
 * 5. Re-attach the reconstructed payload bytes to the (detached) `deviceSignature` and pass to
 *    [CoseCryptoService.verify1] using the device key from the MSO. A signature mismatch fails
 *    the step.
 *
 * Re-attaching is necessary because [com.sphereon.mdoc.MdocSignServiceImpl] (the holder side)
 * calls `detachedPayloadCopy()` after signing, so the on-wire COSE_Sign1 carries no payload —
 * but the signature was computed over the same byte string we reconstruct here. Mismatched
 * session transcripts produce a different byte string and the signature check fails.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DeviceAuthValidation>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceAuthValidationImpl", exact = true)
class DeviceAuthValidationImpl(
    private val coseCryptoService: CoseCryptoService,
    private val sessionTranscriptCborCodec: SessionTranscriptCborCodec,
    private val mobileSecurityObjectCborCodec: MobileSecurityObjectCborCodec,
) : DeviceAuthValidation {
    override suspend fun verifyDeviceAuth(
        document: Document,
        expectedSessionTranscript: SessionTranscript,
    ): VerifySignatureResultType<CoseKeyType> {
        val deviceSigned =
            document.deviceSigned
                ?: return failure("Document has no deviceSigned structure; cannot verify device authentication.")

        val deviceAuth = deviceSigned.deviceAuth
        if (deviceAuth.deviceMac != null) {
            return failure(
                "Device authentication uses COSE_Mac0 (deviceMac); OID4VP requires deviceSignature " +
                    "(COSE_Sign1) because there is no shared secret to derive the EMacKey.",
            )
        }
        val deviceSignature =
            deviceAuth.deviceSignature
                ?: return failure("DeviceAuth has neither deviceSignature nor deviceMac present.")

        val mso = decodeMso(document)
        val deviceKeyInfo = mso.deviceKeyInfo.toKeyInfo()

        val reconstructedPayload =
            try {
                encodeDeviceAuthenticationPayload(
                    sessionTranscript = expectedSessionTranscript,
                    docType = document.docType.toString(),
                    deviceNamespaces = deviceSigned.nameSpaces,
                )
            } catch (expected: Exception) {
                return failure(
                    "Failed to reconstruct expected DeviceAuthentication payload for signature verification: ${expected.message}",
                )
            }

        // The holder signs with `withEncodePayloadAsDataItem(true)`, which wraps the raw
        // DeviceAuthentication CBOR-array bytes inside a tag-24 (`encoded-cbor-data-item`)
        // bstr before computing the Sig_structure. The verifier MUST reconstruct that same
        // wrapped form, otherwise the Sig_structure differs by even one byte and verify1
        // returns "Signature invalid" even for a perfectly valid signature.
        val reattachedPayload = CborEncodedItem(reconstructedPayload, reconstructedPayload).value.toBstr()

        // The holder also calls detachedPayloadCopy() after signing so the on-wire COSE_Sign1
        // has no payload bytes; we re-attach the reconstructed wrapped bytes. If the holder
        // used a different session transcript, docType, or namespaces these bytes won't match
        // what the holder signed and verify1 will fail — the precise outcome we want for a
        // tampered request.
        val reattached = deviceSignature.copy(payload = reattachedPayload)

        val verifyResult =
            try {
                coseCryptoService.verify1(
                    input = reattached,
                    keyInfo = deviceKeyInfo,
                    requireX5Chain = false,
                )
            } catch (expected: Throwable) {
                return failure(
                    message = "DeviceAuthentication signature verification threw: ${expected.message ?: expected::class.simpleName}",
                    detailMessage = expected.message,
                )
            }

        return VerifySignatureResult(
            error = verifyResult.error,
            critical = verifyResult.critical,
            message =
                verifyResult.message
                    ?: if (verifyResult.error) {
                        "DeviceAuthentication signature failed to verify against the device key in the MSO."
                    } else {
                        "DeviceAuthentication signature verified against the device key in the MSO; session transcript matches the verifier-reconstructed value."
                    },
            detailMessage = verifyResult.detailMessage,
            name = DEVICE_AUTH_NAME,
            keyInfo = verifyResult.keyInfo,
        )
    }

    private fun decodeMso(document: Document): MobileSecurityObject {
        val payload =
            document.issuerSigned.issuerAuth.payload
                ?.value
                ?: throw IllegalArgumentException("MSO payload missing on IssuerAuth.")
        return mobileSecurityObjectCborCodec.decode(payload).getOrThrow().value
    }

    /**
     * Mirrors [com.sphereon.mdoc.MdocSignServiceImpl.encodeDeviceAuthentication]: holder and
     * verifier MUST encode the same byte sequence for signature verification to succeed.
     *
     * `DeviceAuthenticationBytes` (the bstr-wrapped tag-24 form per ISO 18013-5 §9.1.3) is
     * what [CoseCryptoService.sign1] takes care of itself when `withEncodePayloadAsDataItem(true)`
     * is set; the holder feeds it the inner CBOR-array bytes built below, and we feed the
     * verifier the same.
     */
    private fun encodeDeviceAuthenticationPayload(
        sessionTranscript: SessionTranscript,
        docType: String,
        deviceNamespaces: DeviceNameSpaces,
    ): ByteArray {
        val sessionTranscriptItem: CborItem<*> =
            Cbor
                .tryDecode(
                    sessionTranscriptCborCodec.encode(sessionTranscript).getOrThrow(),
                ).getOrThrow()

        return Cbor.encode(
            CborArray(
                mutableListOf(
                    CborString("DeviceAuthentication"),
                    sessionTranscriptItem,
                    CborString(docType),
                    CborEncodedItem<CborMap<CborString, CborMap<CborString, CborItem<*>>>>(
                        Cbor.encode(encodeDeviceNameSpaces(deviceNamespaces)),
                    ),
                ),
            ),
        )
    }

    private fun encodeDeviceNameSpaces(value: DeviceNameSpaces): CborMap<CborString, CborMap<CborString, CborItem<*>>> =
        CborMap(
            value.value.entries
                .associate { (nameSpace: NameSpace, items: DeviceSignedItems) ->
                    CborString(nameSpace.toString()) to encodeDeviceSignedItems(items)
                }.toMutableMap(),
        )

    private fun encodeDeviceSignedItems(value: DeviceSignedItems): CborMap<CborString, CborItem<*>> =
        CborMap(
            value.value.entries
                .associate { (identifier, elementValue) ->
                    CborString(identifier.toString()) to elementValue.toCborItem()
                }.toMutableMap(),
        )

    private fun failure(
        message: String,
        detailMessage: String? = null,
    ): VerifySignatureResult<CoseKeyType> =
        VerifySignatureResult(
            error = true,
            critical = true,
            message = message,
            detailMessage = detailMessage,
            name = DEVICE_AUTH_NAME,
        )

    private companion object {
        const val DEVICE_AUTH_NAME = CryptoConst.COSE_LITERAL + ":" + MdocConst.MDOC_LITERAL + ":DeviceAuthentication"
    }
}
