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

package com.sphereon.crypto.core

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.x509.X509VerifyPlatformCallback
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.Volatile
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The main object used by code to be calling into the platform specific callbacks for X509 Certificates and signature creation/verification
 *
 * Non Kotlin code could still extend their implementations.
 * This object is available directly as well except for JS, which has to use its actual implementations this session depends on.
 * Any internal code calling into the JS implementation will automatically wrap that implementation
 */

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CryptoServicesImpl", exact = true)
class CryptoServicesImpl(
    override val x509: X509VerifyService,
    override val cose: CoseCryptoService,
) : CryptoServices {
    override val mappings = CoseJoseKeyMappingService

    // TODO: JOSE
}

/**
 * Default implementation of CryptoCallbacks that delegates to the static DefaultCallbacks object.
 * This is injected as a singleton in AppScope.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CryptoCallbacksImpl : CryptoCallbacks {
    override fun x509(): X509VerifyPlatformCallback<*> = DefaultCallbacks.x509()

    override fun hasX509Default(): Boolean = DefaultCallbacks.hasX509Default()

    override fun coseCrypto(): CoseCryptoCallbackCoroutines = DefaultCallbacks.coseCrypto()

    override fun hasCoseCryptoDefault(): Boolean = DefaultCallbacks.hasCoseCryptoDefault()
}

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultCallbacks", exact = true)
object DefaultCallbacks {
    @Volatile
    private var x509Callback: X509VerifyPlatformCallback<*>? = null

    @Volatile
    private var coseCryptoCallback: CoseCryptoCallbackCoroutines? = null

    fun x509(): X509VerifyPlatformCallback<*> {
        val callback = x509Callback
        checkNotNull(callback) { "No default X509 Platform Callback implementation was registered" }
        return callback
    }

    fun setX509Default(x509Callback: X509VerifyPlatformCallback<*>? = null) {
        this.x509Callback = x509Callback
    }

    fun hasX509Default(): Boolean = this.x509Callback != null

    fun coseCrypto(): CoseCryptoCallbackCoroutines {
        val callback = coseCryptoCallback
        checkNotNull(callback) { "No default Cose Crypto Platform Callback implementation was registered" }
        return callback
    }

    fun hasCoseCryptoDefault(): Boolean = this.coseCryptoCallback != null

    fun setCoseCryptoDefault(coseCryptoCallback: CoseCryptoCallbackCoroutines? = null) {
        this.coseCryptoCallback = coseCryptoCallback
    }
}
