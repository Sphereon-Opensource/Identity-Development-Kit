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

package com.sphereon.crypto.core.x509

import com.sphereon.crypto.core.CryptoConst
import com.sphereon.crypto.core.DefaultCallbacks
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXCertPathValidatorResult
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/*actual fun <PlatformCallback : IX509VerifyPlatformCallback> x509Service(
    platformCallback: PlatformCallback,
    trustedCerts: Set<String>?
): IX509VerifyServiceUsingCallbacks<PlatformCallback> {
    return X509VerifyServiceJvmAdapterImpl(platformCallback = platformCallback as IX509Service, trustedCerts = trustedCerts?.toTypedArray()) as IX509VerifyServiceUsingCallbacks<PlatformCallback>
}*/

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import software.amazon.app.platform.scope.Scope
import software.amazon.app.platform.scope.Scoped

fun X509CoroutinesCallback.register() = DefaultCallbacks.setX509Default(X509VerifyServiceJvmAdapter())


fun registerCoseCryptoCoroutinesCallbackAsDefault(callback: X509CoroutinesCallback) {
    return callback.register()
}

/**
 * Marker interface extending X509CoroutinesCallback for injection purposes.
 * This avoids generic type issues with kotlin-inject.
 */
interface X509CoroutinesCallbackJvm : X509CoroutinesCallback

/**
 * Automatic registration for JVM X509 callback.
 *
 * This class is automatically discovered and initialized when the SessionScope is created,
 * ensuring that the X509 callback is available even when using legacy DefaultCallbacks directly.
 */
@Inject
@SingleIn(com.sphereon.di.session.SessionScope::class)
@ContributesBinding(com.sphereon.di.session.SessionScope::class, binding = binding<X509CoroutinesCallbackJvm>())
@ContributesBinding(com.sphereon.di.session.SessionScope::class, binding = binding<X509CoroutinesCallback>())
class X509VerifyServiceJvmAdapter() : X509CoroutinesCallbackJvm, Scoped {

    override fun onEnterScope(scope: Scope) {
        DefaultCallbacks.setX509Default(this)
    }

    override fun verifyCertificateChainUsingPlatformCallback(verifyContext: X509ValidationContext): X509VerificationResultType {
        // Note: The disabled check is already handled in validateToContext(), but we keep it here
        // as a safeguard for direct callback invocation
        if (!verifyContext.request.enabled) {
            return verifyContext.disabledResult()
        }

        return try {
            val verifiedAt = verifyContext.verificationAt
            val certificateChain = verifyContext.certificateChain

            // Prepare validation date
            val zdt = ZonedDateTime.of(
                verifiedAt.year,
                verifiedAt.month,
                verifiedAt.day,
                verifiedAt.hour,
                verifiedAt.minute,
                verifiedAt.second,
                verifiedAt.nanosecond,
                ZoneId.systemDefault()
            )
            val validationDate = Date.from(zdt.toInstant())

            // Parse certificate chain
            val cf = CertificateFactory.getInstance("X.509")
            require(certificateChain.isNotEmpty()) { "No certificate chain provided" }

            // Determine trust anchors
            val pemAnchors = pemAndDerToCertificateChain(pemChain = verifyContext.request.chainPEM, derChain = verifyContext.request.chainDER)
            val anchors: Set<TrustAnchor> = if (pemAnchors.isNotEmpty()) {
                // parse passed-in trusted certs
                pemAnchors.map { pem ->
                    val tc = cf.generateCertificate(
                        ByteArrayInputStream(pem.der)
                    ) as X509Certificate
                    TrustAnchor(tc, null)
                }.toSet()
            } else {
                // use default JVM trust store (cacerts)
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as KeyStore?)
                tmf.trustManagers
                    .filterIsInstance<X509TrustManager>()
                    .flatMap { it.acceptedIssuers.toList() }
                    .map { TrustAnchor(it, null) }
                    .toSet()
            }

            // Build and validate CertPath
            val params = PKIXParameters(anchors).apply {
                isRevocationEnabled = false
                date = validationDate
            }
            val certPath = cf.generateCertPath(certificateChain.map { cf.generateCertificate(ByteArrayInputStream(it.der)) })
            val validator = CertPathValidator.getInstance("PKIX")
            validator.validate(certPath, params) as PKIXCertPathValidatorResult

            // Successful verification
            verifyContext.successResult()
        } catch (ex: Exception) {
            verifyContext.errorResult(ex)
        }
    }

    @ContributesTo(com.sphereon.di.session.SessionScope::class)
    interface X509VerifyServiceJvmAdapterScopedModule {
        @Provides
        @IntoSet
        @ForScope(com.sphereon.di.session.SessionScope::class)
        fun provideScoped(impl: X509VerifyServiceJvmAdapter): Scoped = impl
    }
}

