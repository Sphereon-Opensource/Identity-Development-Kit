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

package com.sphereon.openid.oid4vc.common

import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for generating QR codes.
 *
 * Implementations should generate QR codes as data URIs in the format:
 * `data:image/png;base64,...`
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("QrCodeService", exact = true)
@JsExportCompat
interface QrCodeService {
    /**
     * Generate a QR code as a data URI.
     *
     * @param content The content to encode in the QR code
     * @param options QR code generation options
     * @return Data URI string (data:image/png;base64,...)
     */
    fun generateDataUri(
        content: String,
        options: QrCodeOptions = QrCodeOptions(),
    ): String
}
