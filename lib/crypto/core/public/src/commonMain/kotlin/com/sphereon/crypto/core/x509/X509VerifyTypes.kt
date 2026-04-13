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

import at.asitplus.signum.indispensable.asn1.runRethrowing
import com.sphereon.cbor.json.HasToJsonString
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.CryptoConst
import com.sphereon.crypto.core.HasPlatformCallback
import com.sphereon.crypto.core.generic.VerifyResultType
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.json.CryptoJsonSupport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsName

@JsExportCompat
enum class X509VerificationProfile {
    ISO_18013_5,
    RFC_5280
}

/**
 * The X509 platform Service object that can be used to acting an actual platform specific callback. In JS for instance this would return a Promise
 *
 */
@JsExportCompat
fun interface X509VerifyPlatformCallback<T : Any> {
    fun verifyCertificateChainUsingPlatformCallback(verifyContext: X509ValidationContext): T
}


fun interface X509CoroutinesCallback : X509VerifyPlatformCallback<X509VerificationResultType>

/**
 * The X509 platform Service object that can be used to acting an actual platform specific callback. For Jvm platforms using coroutines
 *
 * @param T The type of the result returned by the platform specific callback
 */
interface X509VerifyVerifyPlatformCallbackCoroutines : X509VerifyService, X509CoroutinesCallback,
    X509VerifyServiceUsingCallbacks<X509CoroutinesCallback> {
    override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType =
        runRethrowing {
            val context = req.validateToContext()
            if (context.isErr) {
                return context.error
            }
            return verifyCertificateChainUsingPlatformCallback(context.value)
        }
}


interface X509VerifyService {
    suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType


    fun setTrustedCerts(trustedCerts: Array<String>? = null): X509VerifyService
    fun getTrustedCerts(): Array<String>?
}

@JsExportCompat
interface X509VerificationRequestType {
    @JsName("enabled")
    @SerialName("enabled")
    val enabled: Boolean

    @JsName("chainDER")
    @SerialName("chainDER")
    val chainDER: Array<ByteArray>?

    @JsName("chainPEM")
    @SerialName("chainPEM")
    val chainPEM: Array<String>?

    @JsName("trustedCerts")
    @SerialName("trustedCerts")
    val trustedCerts: Array<String>?

    @JsName("verificationProfile")
    @SerialName("verificationProfile")
    val verificationProfile: X509VerificationProfile?

    @JsName("verificationTime")
    @SerialName("verificationTime")
    val verificationTime: LocalDateTimeKMP?

    fun toJsonString(): String {
        return """{"chainDER":${chainDER?.joinToString(prefix = "[", postfix = "]") { it.toString() }}, "chainPEM":${
            chainPEM?.joinToString(
                prefix = "[",
                postfix = "]"
            ) { "\"$it\"" }
        }, "trustedCerts":${
            trustedCerts?.joinToString(
                prefix = "[",
                postfix = "]"
            ) { "\"$it\"" }
        }, "verificationProfile":"${verificationProfile.toString()}", "verificationTime":"${verificationTime.toString()}"}"""
    }

    fun validateToContext(): IdkResult<X509ValidationContext, X509VerificationResultType> {
        val verificationAt = verificationTime ?: LocalDateTimeKMP.now()

        // First check if there's any X509 data to operate on
        if (chainDER == null && chainPEM == null) {
            // No X509 data present - if disabled this is fine, if enabled it's an error
            return Err(
                X509VerificationResult(
                    name = CryptoConst.X509_LITERAL,
                    error = enabled,
                    message = if (enabled) "Please provide either a chain in DER format or PEM format" else "X509 verification has been disabled",
                    critical = enabled,
                    certificateChain = emptyArray(),
                    publicKey = null,
                    verificationTime = verificationAt
                )
            )
        }

        if (chainDER !== null && chainPEM !== null) {
            return Err(
                X509VerificationResult(
                    name = CryptoConst.X509_LITERAL,
                    error = true,
                    message = "Only one of chainDER or chainPEM can be provided",
                    critical = true,
                    certificateChain = emptyArray(),
                    publicKey = null,
                    verificationTime = verificationAt
                )
            )
        }

        // X509 data is present - parse it
        val certificateChain = pemAndDerToCertificateChain(pemChain = chainPEM, derChain = chainDER)
        if (certificateChain.isEmpty()) {
            return Err(
                X509VerificationResult(
                    name = CryptoConst.X509_LITERAL,
                    error = true,
                    message = "Certificate chain is empty",
                    critical = true,
                    certificateChain = emptyArray(),
                    publicKey = null,
                    verificationTime = verificationAt
                )
            )
        }

        val leafCertificate = certificateChain[0]
        val publicKey = leafCertificate.getPublicKeyJwk()

        // If verification is disabled but chain was provided, return the parsed data without validating
        if (!enabled) {
            return Err(
                X509VerificationResult(
                    name = CryptoConst.X509_LITERAL,
                    message = "X509 verification has been disabled",
                    error = false,
                    critical = false,
                    certificateChain = certificateChain,
                    publicKey = publicKey,
                    verificationTime = verificationAt
                )
            )
        }

        if (trustedCerts.isNullOrEmpty()) {
            return Err(
                X509VerificationResult(
                    error = true,
                    message = "No trusted certificates have been provided.",
                    critical = true,
                    name = CryptoConst.X509_LITERAL,
                    certificateChain = certificateChain,
                    publicKey = publicKey,
                    verificationTime = verificationAt
                )
            )
        }

        // on success, wrap everything in our new context
        return Ok(
            X509ValidationContext(
                request = this,
                verificationAt = verificationAt,
                certificateChain = certificateChain,
                leafCertificate = leafCertificate,
                publicKey = publicKey
            )
        )

    }


}

@JsExportCompat
data class X509VerificationRequest(
    override val enabled: Boolean = true,
    override val chainDER: Array<ByteArray>? = null,
    override val chainPEM: Array<String>? = null,
    override val trustedCerts: Array<String>? = null,
    override val verificationProfile: X509VerificationProfile? = null,
    override val verificationTime: LocalDateTimeKMP? = LocalDateTimeKMP.now()
) : X509VerificationRequestType {
    companion object {
        fun fromDto(request: X509VerificationRequestType, enable: Boolean = request.enabled): X509VerificationRequest {
            with(request) {
                return X509VerificationRequest(
                    enabled = enable,
                    chainDER = chainDER,
                    chainPEM = chainPEM,
                    trustedCerts = trustedCerts,
                    verificationProfile = verificationProfile,
                    verificationTime = verificationTime
                )
            }
        }
    }
}


@JsExportCompat
@Serializable
sealed interface X509VerificationResultType : VerifyResultType {
    @JsName("certificateChain")
    @SerialName("certificateChain")
    val certificateChain: Array<Certificate>

    @JsName("publicKey")
    @SerialName("publicKey")
    val publicKey: JwkType?

    @JsName("publicKeyAlgorithm")
    @SerialName("publicKeyAlgorithm")
    val publicKeyAlgorithm: String?

    @JsName("publicKeyParams")
    @SerialName("publicKeyParams")
    val publicKeyParams: Any?

    @JsName("verificationTime")
    @SerialName("verificationTime")
    val verificationTime: LocalDateTimeKMP
}

@JsExportCompat
@Serializable
data class X509VerificationResult(
    @SerialName("certificateChain")
    override val certificateChain: Array<Certificate>,
    @SerialName("publicKey")
    override val publicKey: JwkType? = null,
    @SerialName("publicKeyAlgorithm")
    override val publicKeyAlgorithm: String? = null,
    override val name: String = CryptoConst.X509_LITERAL,
    @SerialName("verificationTime")
    override val verificationTime: LocalDateTimeKMP = LocalDateTimeKMP.now(),
    @SerialName("publicKeyParams")
    @Transient // Transient because Any? cannot be serialized; platform-specific key params
    override val publicKeyParams: Any? = null,
    override val critical: Boolean,
    override val message: String?,
    override val detailMessage: String? = null,
    override val error: Boolean
) : X509VerificationResultType, HasToJsonString {
    override fun toJsonString(): String = CryptoJsonSupport.serializer.encodeToString(this)
}

/**
 * The main entry point for X509 Certificate validation, delegating to a platform specific callback implemented by external developers
 */
interface X509VerifyServiceUsingCallbacks<CallbackServiceType> : HasPlatformCallback<CallbackServiceType>, X509VerifyService


@JsExportCompat
data class X509ValidationContext(
    val request: X509VerificationRequestType,
    val verificationAt: LocalDateTimeKMP,
    val certificateChain: Array<Certificate>,
    val leafCertificate: Certificate,
    val publicKey: JwkType?
)

/**
 * Extension functions for X509ValidationContext to reduce duplication across platform adapters.
 * These provide consistent result construction for success, error, and disabled states.
 */

/**
 * Creates a successful verification result from this context.
 *
 * @param message Optional custom message (defaults to "Certificate chain validated")
 */
fun X509ValidationContext.successResult(
    message: String = "Certificate chain validated"
): X509VerificationResult = X509VerificationResult(
    name = CryptoConst.X509_LITERAL,
    message = message,
    error = false,
    critical = false,
    certificateChain = certificateChain,
    publicKey = publicKey,
    verificationTime = verificationAt
)

/**
 * Creates an error verification result from this context.
 *
 * @param message The error message describing what went wrong
 * @param critical Whether this error is critical (defaults to false)
 * @param detailMessage Optional additional detail about the error
 */
fun X509ValidationContext.errorResult(
    message: String,
    critical: Boolean = false,
    detailMessage: String? = null
): X509VerificationResult = X509VerificationResult(
    name = CryptoConst.X509_LITERAL,
    message = message,
    error = true,
    critical = critical,
    detailMessage = detailMessage,
    certificateChain = certificateChain,
    publicKey = publicKey,
    verificationTime = verificationAt
)

/**
 * Creates an error verification result from an exception.
 *
 * @param ex The exception that caused the verification failure
 * @param critical Whether this error is critical (defaults to false)
 */
fun X509ValidationContext.errorResult(
    ex: Throwable,
    critical: Boolean = false
): X509VerificationResult = X509VerificationResult(
    name = CryptoConst.X509_LITERAL,
    message = ex.message ?: ex.toString(),
    error = true,
    critical = critical,
    detailMessage = ex.stackTraceToString(),
    certificateChain = certificateChain,
    publicKey = publicKey,
    verificationTime = verificationAt
)

/**
 * Creates a "disabled" result indicating verification was skipped because it was disabled.
 * Note: This is typically handled in validateToContext(), but this helper is available
 * for cases where platform adapters need to check the disabled state again.
 */
fun X509ValidationContext.disabledResult(): X509VerificationResult = X509VerificationResult(
    name = CryptoConst.X509_LITERAL,
    message = "X509 verification has been disabled",
    error = false,
    critical = false,
    certificateChain = certificateChain,
    publicKey = publicKey,
    verificationTime = verificationAt
)
