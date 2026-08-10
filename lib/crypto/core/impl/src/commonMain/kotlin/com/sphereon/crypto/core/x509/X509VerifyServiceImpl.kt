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
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The X509 Service object that can be used to register the actual callback. It is not available for JS,
 * which has its own adapted version supporting Promises. Actual implementations can use this object or provide their own
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<X509VerifyVerifyPlatformCallbackCoroutines>())
@ContributesBinding(SessionScope::class, binding = binding<X509VerifyService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("X509VerifyServiceImpl", exact = true)
@Suppress("TooGenericExceptionCaught")
class X509VerifyServiceImpl : X509VerifyVerifyPlatformCallbackCoroutines {
    private var platformCallback: X509CoroutinesCallback? = null
    private var trustedCerts: Array<String> = emptyArray()

    private var disabled = false

    override fun isEnabled(): Boolean = !this.disabled

    override fun platform(): X509CoroutinesCallback {
        val callback = this.platformCallback
        if (callback != null) {
            return callback
        }
        val defaultCallback = DefaultCallbacks.x509() as X509CoroutinesCallback
        this.platformCallback = defaultCallback
        return defaultCallback
    }

    override fun hasPlatform(): Boolean = this.platformCallback !== null || DefaultCallbacks.hasX509Default()

    override fun setPlatform(platform: X509CoroutinesCallback): HasPlatformCallback<X509CoroutinesCallback> =
        apply {
            this.platformCallback = platform
        }

    override fun disable() =
        apply {
            this.disabled = true
        }

    override fun enable() =
        apply {
            this.disabled = false
        }

    override fun setTrustedCerts(trustedCerts: Array<String>?): X509VerifyService =
        apply {
            this.trustedCerts = trustedCerts?.copyOf() ?: emptyArray()
        }

    override fun getTrustedCerts(): Array<String>? = this.trustedCerts

    override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType {
        // A caller may disable anchor validation for key extraction while the service remains
        // globally enabled. A globally disabled service must still override every request.
        val request = X509VerificationRequest.fromDto(req, enable = this.isEnabled() && req.enabled)
        val context = request.validateToContext()
        if (context.isErr) {
            return context.error
        }
        return try {
            verifyCertificateChainUsingPlatformCallback(context.value)
        } catch (expected: Exception) {
            context.value.errorResult(
                message = "Certificate chain verification failed with an unexpected error: ${expected.message}",
                critical = true,
                detailMessage = expected.message,
            )
        }
    }

    override fun verifyCertificateChainUsingPlatformCallback(verifyContext: X509ValidationContext): X509VerificationResultType = platform().verifyCertificateChainUsingPlatformCallback(verifyContext)
}
