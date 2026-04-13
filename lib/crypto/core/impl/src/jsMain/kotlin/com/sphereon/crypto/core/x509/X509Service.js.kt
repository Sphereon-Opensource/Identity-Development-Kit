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

package com.sphereon.crypto.core.x509

import com.sphereon.crypto.core.DefaultCallbacks
import com.sphereon.crypto.core.HasPlatformCallback
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * A version that resembles the internal X509Callbacks interface, but then using promises instead of coroutines to make it fit the JS world
 */
@JsExport
fun interface IX509JSCallback : X509VerifyPlatformCallback<Promise<X509VerificationResultType>>

fun IX509JSCallback.register() = DefaultCallbacks.setX509Default(this)

// fun registerCoseCryptoCoroutinesCallbackAsDefault(callback: IX509JSCallback) {
//    return callback.register()
// }

@JsExport
interface X509VerifyVerifyPlatformCallbackJSPromises :
    X509VerifyService,
    IX509JSCallback,
    X509VerifyServiceUsingCallbacks<IX509JSCallback> {
    @JsExport.Ignore
    override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType {
        val context = req.validateToContext()
        if (context.isErr) {
            return context.error
        }
        return verifyCertificateChainUsingPlatformCallback(context.value).await()
    }
}

/**
 * Marker interface for IX509JSCallback to enable injection.
 * Extends the callback interface to work around generic type injection issues.
 */
interface IX509JSCallbackMarker : IX509JSCallback

/**
 * Internal object that has the JS exposed session as its callback.
 *
 * The main responsibility it to convert the JS Promises into the Coroutines used in the X509Service.
 *
 * The crypto code will use this object as the actual implementation.
 * We do not want to expose its API to JS, as it is not meant to be called by external developers and
 * also the coroutines would not export nicely anyway.
 *
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<X509VerifyVerifyPlatformCallbackJSPromises>())
@ContributesBinding(SessionScope::class, binding = binding<X509VerifyService>(), replaces = [X509VerifyServiceImpl::class])
class X509VerifyServiceJSAdapterImpl : X509VerifyVerifyPlatformCallbackJSPromises {
    private var x509ServiceJS: IX509JSCallback? = null
    private var trustedCerts: Array<String> = emptyArray()
    private var disabled = false

    override fun getTrustedCerts(): Array<String>? = this.trustedCerts

    override fun setTrustedCerts(trustedCerts: Array<String>?) =
        apply {
            this.trustedCerts = trustedCerts?.copyOf() ?: emptyArray()
        }

    override fun disable() = apply { this.disabled = true }

    override fun enable() = apply { this.disabled = false }

    override fun isEnabled() = !disabled

    override fun platform(): IX509JSCallback = x509ServiceJS ?: throw IllegalStateException("No platform callback set")

    override fun hasPlatform(): Boolean = this.x509ServiceJS != null

    override fun setPlatform(platform: IX509JSCallback): HasPlatformCallback<IX509JSCallback> =
        apply {
            this.x509ServiceJS = platform
        }

    override fun verifyCertificateChainUsingPlatformCallback(verifyContext: X509ValidationContext): Promise<X509VerificationResultType> =
        platform().verifyCertificateChainUsingPlatformCallback(verifyContext)

    @JsExport.Ignore
    override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType {
        val request = X509VerificationRequest.fromDto(req, enable = this.isEnabled())
        val context = request.validateToContext()
        if (context.isErr) {
            return context.error
        }
        return try {
            verifyCertificateChainUsingPlatformCallback(context.value).await()
        } catch (expected: Exception) {
            context.value.errorResult(
                message = "Certificate chain verification failed with an unexpected error: ${expected.message}",
                critical = true,
                detailMessage = expected.message,
            )
        }
    }
}
