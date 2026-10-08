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

package com.sphereon.mdoc.engagement

import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class HolderDeviceEngagementAssemblerTest {
    private val coseKeyCborCodec = CoseKeyCborCodecImpl()
    private val assembler = HolderDeviceEngagementAssembler(coseKeyCborCodec)

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()

    private fun createResolvedKeyInfo(): ResolvedKeyInfo<CoseKeyType> =
        ResolvedKeyInfo(
            key = createTestCoseKey(),
            keyVisibility = KeyVisibility.PUBLIC,
        )

    @Test
    fun createCombinesSplitBleMethodsAndUsesCodecEncodedPublicKey() {
        val uuid = Uuid.random()
        val engagementData =
            EngagementData
                .holderBuilder()
                .withEphemeralKey(createResolvedKeyInfo())
                .withRetrievalMethods(
                    setOf(
                        DeviceRetrievalMethod(
                            type = DeviceRetrievalMethodType.BLE,
                            retrievalOptions =
                                BleOptions(
                                    peripheralServerMode = true,
                                    centralClientMode = true,
                                    peripheralServerModeUuid = uuid,
                                    centralClientModeUuid = uuid,
                                ),
                        ),
                    ),
                ).withQrEngagement()
                .build()

        val prepared = assembler.create(engagementData)
        val deviceEngagement = prepared.engagement as DeviceEngagement.V1_0
        val bleMethod = deviceEngagement.deviceRetrievalMethods!!.single()
        val bleOptions = bleMethod.retrievalOptions as BleOptions
        val expectedPublicKeyBytes = coseKeyCborCodec.encode(CoseKey.fromDTO(engagementData.getEphemeralKey().key).toPublicKey()).getOrThrow()

        assertEquals(1, prepared.bleMethodCount)
        assertTrue(bleOptions.peripheralServerMode)
        assertTrue(bleOptions.centralClientMode)
        assertEquals(uuid, bleOptions.peripheralServerModeUuid)
        assertEquals(uuid, bleOptions.centralClientModeUuid)
        assertContentEquals(expectedPublicKeyBytes, deviceEngagement.security.eDeviceKeyBytes.value.taggedItem.value)
    }
}
