/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.core.kms.KmsProviderRegistry
import com.sphereon.crypto.core.kms.ProviderCertificateLookup
import com.sphereon.crypto.core.kms.ProviderCertificateReference
import com.sphereon.crypto.core.kms.ProviderCertificateReferenceService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException

/**
 * Provider-native certificate reads are isolated behind this seam so enterprise can replace the
 * default provider-registry implementation without changing registration or REST behavior.
 */
interface ProviderCertificateReferenceInspector {
    suspend fun inspect(
        providerId: String,
        alias: String,
        providerCertificateId: String?,
        kind: CertificateReferenceKind,
    ): IdkResult<ProviderCertificateReference, IdkError>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ProviderCertificateReferenceInspector>())
class DefaultProviderCertificateReferenceInspector(
    private val providerRegistry: KmsProviderRegistry,
) : ProviderCertificateReferenceInspector {
    override suspend fun inspect(
        providerId: String,
        alias: String,
        providerCertificateId: String?,
        kind: CertificateReferenceKind,
    ): IdkResult<ProviderCertificateReference, IdkError> {
        if (providerId.isBlank() || alias.isBlank() || providerCertificateId?.isBlank() == true) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "providerId, alias, and providerCertificateId must not be blank"))
        }

        val provider =
            try {
                providerRegistry.getProviderById(providerId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return Err(
                    IdkError.fromString(
                        code = "KMS_PROVIDER_NOT_FOUND",
                        message = "The requested KMS provider is not available",
                    ),
                )
            }

        val certificateService = provider as? ProviderCertificateReferenceService
            ?: return Err(
                IdkError.fromString(
                    code = "KMS_PROVIDER_CERTIFICATE_REFERENCE_UNSUPPORTED",
                    message = "The requested KMS provider does not support certificate reference reads",
                ),
            )

        val result =
            try {
                certificateService.getCertificate(ProviderCertificateLookup(alias = alias, id = providerCertificateId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return Err(IdkError.UNKNOWN_ERROR(message = "Provider certificate read failed"))
            }

        val reference = result.getOrElse { error ->
            return Err(
                IdkError.fromString(
                    code = error.code,
                    message = safeProviderErrorMessage(error.code),
                ),
            )
        }

        if (reference.providerId != providerId || reference.alias != alias ||
            (providerCertificateId != null && reference.id != providerCertificateId)
        ) {
            return Err(
                IdkError.fromString(
                    code = "KMS_PROVIDER_CERTIFICATE_IDENTITY_MISMATCH",
                    message = "The provider certificate identity does not match the requested reference",
                ),
            )
        }
        return Ok(reference)
    }

    private fun safeProviderErrorMessage(code: String): String =
        when (code) {
            "NOT_FOUND_ERROR" -> "The provider certificate was not found"
            "UNSUPPORTED_OPERATION_ERROR" -> "The provider does not support this certificate read"
            "ILLEGAL_ARGUMENT_ERROR" -> "The provider certificate lookup is invalid"
            else -> "The provider certificate is not available"
        }
}
