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

package com.sphereon.crypto.core

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.crypto.core.x509.X509VerifyPlatformCallback
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName

@OptIn(ExperimentalObjCName::class)
@ObjCName("CryptoServices", exact = true)
interface CryptoServices {
    val x509: X509VerifyService
    val cose: CoseCryptoService
    val mappings: CoseJoseKeyMappingService
}

/**
 * Injectable interface for crypto callbacks.
 * Uses marker interfaces to work around generic type injection limitations.
 */
interface CryptoCallbacks {
    fun x509(): X509VerifyPlatformCallback<*>
    fun hasX509Default(): Boolean
    fun coseCrypto(): CoseCryptoCallbackCoroutines
    fun hasCoseCryptoDefault(): Boolean
}

/**
 * The main entry point for platform callbacks (validation), delegating to a platform specific callback implemented by external developers
 */
@JsExportCompat
interface HasPlatformCallback<PlatformCallbackType> {
    /**
     * Disable callback
     */
    @JsName("disable")
    fun disable(): HasPlatformCallback<PlatformCallbackType>

    /**
     * Enable the callback
     */
    @JsName("enable")
    fun enable(): HasPlatformCallback<PlatformCallbackType>


    /**
     * Is the callback enabled or not
     */
    @JsName("isEnabled")
    fun isEnabled(): Boolean


    @JsName("platform")
    fun platform(): PlatformCallbackType

    @JsName("setPlatform")
    fun setPlatform(platform: PlatformCallbackType): HasPlatformCallback<PlatformCallbackType>


    /**
     * Whether the callback is registered or not
     */
    @JsName("hasPlatform")
    fun hasPlatform(): Boolean
}
