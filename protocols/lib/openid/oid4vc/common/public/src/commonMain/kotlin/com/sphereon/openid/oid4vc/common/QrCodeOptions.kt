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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

/**
 * QR code generation options.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("QrCodeOptions", exact = true)
@JsExportCompat
@Serializable
data class QrCodeOptions
    @JvmOverloads
    constructor(
        /**
         * QR code size in pixels. Default: 400.
         */
        val size: Int = 400,
        /**
         * Dark color (foreground) in CSS format. Default: #000000.
         */
        @SerialName("color_dark")
        val colorDark: String = "#000000",
        /**
         * Light color (background) in CSS format. Default: #ffffff.
         */
        @SerialName("color_light")
        val colorLight: String = "#ffffff",
    )
