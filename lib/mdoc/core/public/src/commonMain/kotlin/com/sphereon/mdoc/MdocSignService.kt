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

package com.sphereon.mdoc

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.CoseSign1Result
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.mdoc.data.device.DeviceAuthentication
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedNameSpaces
import com.sphereon.mdoc.data.mso.MobileSecurityObject

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("for", exact = true)
@JsExportCompat
 * Public interface for the MDOC signing service.
 */
interface MdocSignService {
    suspend fun issuerSignMso(
        mso: MobileSecurityObject,
        issuerKeyInfo: ManagedKeyInfoType<*>,
        signatureAlgorithm: SignatureAlgorithm? = issuerKeyInfo.signatureAlgorithm,
        unprotectedHeader: CoseHeaderCbor? = null,
        protectedHeader: CoseHeaderCbor? = null,
        requireDeviceX5Chain: Boolean = false,
    ): CoseSign1Result<MobileSecurityObject>

    suspend fun issuerSignIssuerSigned(
        mso: MobileSecurityObject,
        issuerSignedNameSpaces: IssuerSignedNameSpaces,
        issuerKeyInfo: ManagedKeyInfoType<*>,
        signatureAlgorithm: SignatureAlgorithm? = issuerKeyInfo.signatureAlgorithm,
        unprotectedHeader: CoseHeaderCbor? = null,
        protectedHeader: CoseHeaderCbor? = null,
        requireDeviceX5Chain: Boolean = false,
    ): IssuerSigned

    suspend fun issuerSignDocument(
        mso: MobileSecurityObject,
        issuerSignedNameSpaces: IssuerSignedNameSpaces,
        issuerKeyInfo: ManagedKeyInfoType<*>,
        signatureAlgorithm: SignatureAlgorithm? = issuerKeyInfo.signatureAlgorithm,
        unprotectedHeader: CoseHeaderCbor? = null,
        protectedHeader: CoseHeaderCbor? = null,
        requireDeviceX5Chain: Boolean = false,
    ): Document

    suspend fun deviceSignDocument(
        request: DocRequest,
        document: Document,
        deviceAuthentication: DeviceAuthentication,
        deviceKeyInfo: KeyInfoType<*>? = null,
        unprotectedHeader: CoseHeaderCbor? = null,
        protectedHeader: CoseHeaderCbor? = null,
        requireDeviceX5Chain: Boolean = false,
    ): Document
}
