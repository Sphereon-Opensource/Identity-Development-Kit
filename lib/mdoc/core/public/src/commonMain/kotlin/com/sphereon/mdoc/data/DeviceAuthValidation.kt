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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * ISO 18013-5 §9.1.3 mdoc authentication.
 *
 * The holder's wallet signs `DeviceAuthentication = ["DeviceAuthentication", SessionTranscript,
 * DocType, DeviceNameSpaces]` (or computes a `COSE_Mac0` over the equivalent structure with a
 * shared EMacKey) using its device key. The verifier MUST check that:
 *
 * 1. `DeviceAuth` carries either `deviceSignature` (COSE_Sign1) or `deviceMac` (COSE_Mac0).
 * 2. The session transcript embedded in the signed payload matches the one the verifier
 *    constructs from the protocol context (for OID4VP: `clientId`, `responseUri`,
 *    `mdoc_generated_nonce`, the verifier's authorization-request `nonce`).
 * 3. The COSE_Sign1 signature verifies against the device public key from
 *    `MobileSecurityObject.deviceKeyInfo.deviceKey`.
 *
 * MAC mode is not used in OID4VP (no shared secret between holder and verifier in JARM
 * direct_post); the impl rejects it with a clear error so a misconfigured wallet doesn't slip
 * through silently.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceAuthValidation", exact = true)
interface DeviceAuthValidation {
    /**
     * Verify the holder's `DeviceAuth` against [expectedSessionTranscript] using the device key
     * extracted from the document's MSO.
     *
     * @param document must carry a non-null [com.sphereon.mdoc.data.device.DeviceSigned] with a
     *   `deviceSignature` (COSE_Sign1). Mac0 returns a critical failure.
     * @param expectedSessionTranscript the verifier-side reconstruction (e.g. via
     *   [SessionTranscript.fromOid4vpClientIdAndResponseUri] for OID4VP).
     */
    suspend fun verifyDeviceAuth(
        document: Document,
        expectedSessionTranscript: SessionTranscript,
    ): VerifySignatureResultType<CoseKeyType>
}
