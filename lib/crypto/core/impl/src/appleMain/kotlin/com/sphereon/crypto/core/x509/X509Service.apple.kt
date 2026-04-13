/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.crypto.core.x509

import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.CryptoConst
import com.sphereon.crypto.core.DefaultCallbacks
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDateCreate
import platform.CoreFoundation.CFTypeRef
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateGraphs
import platform.Foundation.NSTimeZone
import platform.Foundation.systemTimeZone
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecPolicyCreateBasicX509
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.SecTrustSetVerifyDate
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.Scoped

fun X509CoroutinesCallback.register() = DefaultCallbacks.setX509Default(X509VerifyServiceAppleAdapterImpl())

fun registerCoseCryptoCoroutinesCallbackAsDefault(callback: X509CoroutinesCallback) = callback.register()

/**
 * Marker interface extending X509CoroutinesCallback for injection purposes on Apple platforms.
 * This avoids generic type issues with kotlin-inject.
 */
interface X509CoroutinesCallbackApple : X509CoroutinesCallback

/**
 * Automatic registration for Apple X509 callback.
 *
 * This class is automatically discovered and initialized when the SessionScope is created,
 * ensuring that the X509 callback is available even when using legacy DefaultCallbacks directly.
 */
@Inject
@SingleIn(com.sphereon.di.session.SessionScope::class)
@ContributesBinding(com.sphereon.di.session.SessionScope::class, binding = binding<X509CoroutinesCallbackApple>())
@ContributesBinding(com.sphereon.di.session.SessionScope::class, binding = binding<X509CoroutinesCallback>())
@OptIn(ExperimentalForeignApi::class)
class X509VerifyServiceAppleAdapterImpl :
    X509CoroutinesCallbackApple,
    Scoped {
    override fun onEnterScope(scope: Scope) {
        DefaultCallbacks.setX509Default(this)
    }

    override fun verifyCertificateChainUsingPlatformCallback(verifyContext: X509ValidationContext): X509VerificationResultType {
        // Note: The disabled check is already handled in validateToContext(), but we keep it here
        // as a safeguard for direct callback invocation
        if (!verifyContext.request.enabled) {
            return verifyContext.disabledResult()
        }

        return memScoped {
            try {
                val verifiedAt = verifyContext.verificationAt
                val certificateChain = verifyContext.certificateChain
                require(certificateChain.isNotEmpty()) { "No certificate chain provided" }

                // Convert chain DER to SecCertificate refs (leaf first)
                val certRefs = mutableListOf<CFTypeRef?>()
                for (cert in certificateChain) {
                    val sec =
                        derToSecCertificate(cert.der)
                            ?: return@memScoped verifyContext.errorResult("Failed to parse certificate DER")
                    certRefs.add(sec)
                }

                // Policy: basic X.509 (works for general chain validation)
                val policy = SecPolicyCreateBasicX509()

                // Build CFArray of certificates for SecTrustCreateWithCertificates
                val certsArray =
                    platform.CoreFoundation.CFArrayCreateMutable(
                        allocator = null,
                        capacity = 0,
                        callBacks = null,
                    )
                certRefs.forEach { sec ->
                    // CFArrayAppendValue expects a COpaquePointer (void*)
                    platform.CoreFoundation.CFArrayAppendValue(certsArray, sec)
                }

                // Build SecTrust from certs + policy
                val trustVar = alloc<platform.Security.SecTrustRefVar>()
                val trustCreateStatus =
                    SecTrustCreateWithCertificates(
                        certsArray,
                        policy,
                        trustVar.ptr,
                    )
                if (trustCreateStatus != 0) {
                    return@memScoped verifyContext.errorResult("SecTrustCreateWithCertificates failed: $trustCreateStatus")
                }
                val trust =
                    trustVar.value ?: run {
                        return@memScoped verifyContext.errorResult("SecTrust is null")
                    }

                // If explicit anchors provided, set them; otherwise use system trust store
                val pemAnchors =
                    pemAndDerToCertificateChain(
                        pemChain = verifyContext.request.chainPEM,
                        derChain = verifyContext.request.chainDER,
                    )

                if (pemAnchors.isNotEmpty()) {
                    val anchorArray =
                        platform.CoreFoundation.CFArrayCreateMutable(
                            allocator = null,
                            capacity = 0,
                            callBacks = null,
                        )
                    pemAnchors.forEach { pem ->
                        derToSecCertificate(pem.der)?.let { platform.CoreFoundation.CFArrayAppendValue(anchorArray, it) }
                    }
                    if (platform.CoreFoundation.CFArrayGetCount(anchorArray).toInt() == 0) {
                        return@memScoped verifyContext.errorResult("No valid anchors could be parsed")
                    }
                    val setAnchorsStatus = SecTrustSetAnchorCertificates(trust, anchorArray)
                    if (setAnchorsStatus != 0) {
                        return@memScoped verifyContext.errorResult("SecTrustSetAnchorCertificates failed: $setAnchorsStatus")
                    }
                    // Only trust the provided anchors (do not fall back to system roots)
                    SecTrustSetAnchorCertificatesOnly(trust, true)
                } else {
                    // Use system anchors; allow fallback
                    SecTrustSetAnchorCertificatesOnly(trust, false)
                }

                // Set verification date if provided
                dateFromLocalDateTimeKMP(verifiedAt)?.let { date ->
                    val cfDate = CFDateCreate(null, date.timeIntervalSinceReferenceDate)
                    SecTrustSetVerifyDate(trust, cfDate)
                }

                // Evaluate
                val errorVar = alloc<platform.CoreFoundation.CFErrorRefVar>()
                val ok = SecTrustEvaluateWithError(trust, errorVar.ptr)
                if (!ok) {
                    return@memScoped verifyContext.errorResult("Certificate chain validation failed")
                }

                // Success
                verifyContext.successResult()
            } catch (ex: Throwable) {
                verifyContext.errorResult(ex)
            }
        }
    }

    private fun derToSecCertificate(der: ByteArray): CFTypeRef? =
        memScoped {
            der.usePinned { pinned ->
                val cfData =
                    CFDataCreate(
                        allocator = null,
                        bytes = pinned.addressOf(0).reinterpret(),
                        length = der.size.toLong(),
                    )
                SecCertificateCreateWithData(null, cfData)
            }
        }

    private fun dateFromLocalDateTimeKMP(dt: LocalDateTimeKMP): NSDate? {
        val comps = NSDateGraphs()
        comps.year = dt.year.toLong()
        comps.month = dt.month.toLong()
        comps.day = dt.day.toLong()
        comps.hour = dt.hour.toLong()
        comps.minute = dt.minute.toLong()
        comps.second = dt.second.toLong()
        comps.nanosecond = dt.nanosecond.toLong()
        comps.timeZone = NSTimeZone.systemTimeZone
        return NSCalendar.currentCalendar.dateFromGraphs(comps)
    }

    @ContributesTo(com.sphereon.di.session.SessionScope::class)
    interface X509VerifyServiceAppleAdapterScopedModule {
        @Provides
        @IntoSet
        @ForScope(com.sphereon.di.session.SessionScope::class)
        fun provideScoped(impl: X509VerifyServiceAppleAdapterImpl): Scoped = impl
    }
}
