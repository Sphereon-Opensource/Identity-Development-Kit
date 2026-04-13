/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core.generic

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.compat.JsExportCompat
/**
 * Defines the mask generation function, an algorithm used in certain cryptographic operations
 * such as signing and key generation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MaskGenFunction", exact = true)
@JsExportCompat
@kotlinx.serialization.Serializable
enum class MaskGenFunction {
    /**
     * Represents the MGF1 (Mask Generation Function 1) algorithm enumeration.
     *
     * It is commonly used in cryptographic operations such as in the PKCS#1 standard for RSA-OAEP and RSA-PSS.
     */
    MGF1
}
