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

package com.sphereon.crypto.core.kms

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.x509.Certificate
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CertificateStoreService", exact = true)
interface CertificateStoreService {
    suspend fun storeCertificateChain(
        alias: String,
        certificates: Array<Certificate>,
        keyInfo: ResolvedKeyInfoType<*>? = null,
    )

    suspend fun listCertificateChainAliases(): Array<String>

    suspend fun getCertificateChain(alias: String): Array<Certificate>

    suspend fun deleteCertificateChain(alias: String): Boolean

    suspend fun storeTrustedCertificate(
        alias: String,
        certificate: Certificate,
    )

    suspend fun listCertificateAliases(): Array<String>

    suspend fun getCertificate(alias: String): Certificate

    suspend fun deleteCertificate(alias: String): Boolean
}
