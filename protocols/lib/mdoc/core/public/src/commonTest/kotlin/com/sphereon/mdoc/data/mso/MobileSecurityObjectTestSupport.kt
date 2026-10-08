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

package com.sphereon.mdoc.data.mso

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.StringLabel
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.testutil.coseKeyAsCborMap

internal fun encodeMobileSecurityObjectForTest(value: MobileSecurityObject): ByteArray =
    Cbor.encode(
        CborMap(
            mutableMapOf(
                MobileSecurityObject.VERSION to CborString(value.version.toString()),
                MobileSecurityObject.DIGEST_ALGORITHM to CborString(value.digestAlgorithm.toString()),
                MobileSecurityObject.VALUE_DIGESTS to
                    CborMap(
                        value.valueDigests.entries
                            .associate { (nameSpace, digests) ->
                                StringLabel(nameSpace.toString()) to
                                    CborMap(
                                        digests.entries
                                            .associate { (digestId, digestValue) ->
                                                com.sphereon.cbor.NumberLabel(digestId.toString().toLong()) to com.sphereon.cbor.CborByteString(digestValue)
                                            }.toMutableMap(),
                                    )
                            }.toMutableMap(),
                    ),
                MobileSecurityObject.DEVICE_KEY_INFO to encodeDeviceKeyInfoForTest(value.deviceKeyInfo),
                MobileSecurityObject.DOC_TYPE to CborString(value.docType.toString()),
                MobileSecurityObject.VALIDITY_INFO to encodeValidityInfoForTest(value.validityInfo),
            ),
        ),
    )

internal fun encodeMobileSecurityObjectTag24ForTest(value: MobileSecurityObject): ByteArray {
    val encoded = encodeMobileSecurityObjectForTest(value)
    return CborEncodedItem(encoded, value.copy(original = encoded)).encodeCbor()
}

internal fun createIssuerAuthForTest(
    mso: MobileSecurityObject,
    alg: CoseAlgorithm = CoseAlgorithm.ES256,
    signature: ByteArray = ByteArray(64) { it.toByte() },
): CoseSign1<MobileSecurityObject> =
    CoseSign1(
        protectedHeader = CoseHeaderCbor(alg = alg),
        unprotectedHeader = null,
        payload = CborByteString(encodeMobileSecurityObjectTag24ForTest(mso)),
        signature = CborByteString(signature),
    )

private fun encodeDeviceKeyInfoForTest(value: DeviceKeyInfo): CborMap<StringLabel, CborItem<*>> {
    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            DeviceKeyInfo.DEVICE_KEY to coseKeyAsCborMap(value.deviceKey),
        )
    value.keyAuthorizations?.let { entries[DeviceKeyInfo.KEY_AUTHORIZATIONS] = encodeKeyAuthorizationsForTest(it) }
    value.keyInfo?.let { entries[DeviceKeyInfo.KEY_INFO] = it }
    return CborMap(entries)
}

private fun encodeKeyAuthorizationsForTest(value: KeyAuthorizations): CborMap<StringLabel, CborItem<*>> {
    val entries = mutableMapOf<StringLabel, CborItem<*>>()
    value.nameSpaces?.let { entries[KeyAuthorizations.NAME_SPACES] = it }
    value.dataElements?.let { entries[KeyAuthorizations.DATA_ELEMENTS] = it }
    return CborMap(entries)
}

private fun encodeValidityInfoForTest(value: ValidityInfo): CborMap<StringLabel, CborItem<*>> {
    val entries =
        mutableMapOf<StringLabel, CborItem<*>>(
            ValidityInfo.SIGNED to value.signed.toCborItem(),
            ValidityInfo.VALID_FROM to value.validFrom.toCborItem(),
            ValidityInfo.VALID_UNTIL to value.validUntil.toCborItem(),
        )
    value.expectedUpdate?.let { entries[ValidityInfo.EXPECTED_UPDATE] = it.toCborItem() }
    return CborMap(entries)
}
