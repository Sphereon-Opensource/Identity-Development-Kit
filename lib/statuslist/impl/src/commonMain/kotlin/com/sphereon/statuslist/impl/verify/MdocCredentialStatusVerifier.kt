/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.statuslist.impl.verify

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64
import com.sphereon.crypto.core.x509.x509DerOrPemToPem
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.CredentialStatusReference
import com.sphereon.statuslist.CredentialStatusInput
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.ResolvedStatus
import com.sphereon.statuslist.StatusListErrors
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import com.sphereon.statuslist.spi.StatusListResolver
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Verifier for status metadata extracted from an authenticated ISO/IEC 18013 MSO.
 *
 * mdoc revocation lists are binary COSE/CWT artifacts and are not the generic JWT
 * status-list claim shape. The resolver receives the session's configured trust
 * anchors explicitly; a protected x5chain in the fetched CWT is evidence, never a
 * new trust root.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<CredentialStatusVerifier>())
class MdocCredentialStatusVerifier(
    private val resolver: StatusListResolver,
    private val x509VerifyService: X509VerifyService,
) : CredentialStatusVerifier {
    override val mechanism: String = MECHANISM

    override fun references(input: CredentialStatusInput): List<CredentialStatusReference> =
        input.metadata?.mdoc?.references
            ?.filter { it.mechanism == MECHANISM }
            ?: references(input.claims)

    override fun references(credentialClaims: JsonObject): List<CredentialStatusReference> {
        val status = credentialClaims["status"] as? JsonObject ?: return emptyList()
        return status["mdoc_status_list"].asObjects().map { statusList ->
            val uri = statusList.stringValue("uri")?.takeIf { it.isNotBlank() }.orEmpty()
            val index = (statusList["idx"] as? JsonPrimitive)?.intOrNull ?: -1
            CredentialStatusReference(
                mechanism = MECHANISM,
                uri = uri,
                index = index,
                certificate = statusList.certificateBytes(),
            )
        } + status["mdoc_identifier_list"].asObjects().map { identifierList ->
            val candidateUri = identifierList.stringValue("uri")?.takeIf { it.isNotBlank() }
            val encodedIdentifier = identifierList.stringValue("id")
            val identifier = encodedIdentifier?.let { encoded ->
                runCatching { encoded.decodeFromBase64Url() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
            CredentialStatusReference(
                mechanism = MECHANISM,
                // An invalid identifier must not be represented as an empty identifier: an empty
                // value could otherwise be treated as a legitimate, non-revoked identifier.
                uri = if (candidateUri != null && identifier != null) candidateUri else "",
                index = 0,
                identifier = identifier ?: byteArrayOf(),
                certificate = identifierList.certificateBytes(),
            )
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.asObjects(): List<JsonObject> = when (this) {
        is JsonObject -> listOf(this)
        // Preserve malformed array members as an invalid reference so a mixed valid/malformed
        // status claim cannot silently pass by dropping the malformed member.
        is JsonArray -> this.map { it as? JsonObject ?: JsonObject(emptyMap()) }
        null -> emptyList()
        else -> listOf(JsonObject(emptyMap()))
    }

    private fun JsonObject.certificateBytes(): ByteArray? {
        if (!containsKey("certificate")) return null
        val encoded = stringValue("certificate") ?: return byteArrayOf()
        return runCatching { encoded.decodeFromBase64Url() }.getOrElse { byteArrayOf() }
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    override suspend fun resolve(reference: CredentialStatusReference): IdkResult<ResolvedStatus, IdkError> {
        if (reference.uri.isBlank()) {
            return Err(StatusListErrors.verificationFailed("", "mdoc status reference has an invalid URI or identifier"))
        }
        if (reference.certificate?.isEmpty() == true) {
            return Err(StatusListErrors.verificationFailed(reference.uri, "mdoc status reference has an invalid certificate"))
        }
        // The certificate carried by the MSO is an authenticated chain pin, not a trust anchor.
        // Never replace the tenant's configured trust roots with an arbitrary certificate fetched
        // from a credential or a status-list response.
        val trustedCerts = x509VerifyService.getTrustedCerts()
        if (trustedCerts.isNullOrEmpty()) {
            return Err(
                StatusListErrors.verificationFailed(
                    reference.uri,
                    "mdoc status verification requires configured trust anchors",
                ),
            )
        }
        try {
            reference.certificate?.let { certificate ->
                // Parse the supplied certificate here so malformed MSO metadata fails before any
                // network request. The parsed value is passed as a pin below; it is never trusted
                // by itself.
                x509DerOrPemToPem(certificate.encodeToBase64())
            }
        } catch (e: Exception) {
            return Err(
                StatusListErrors.verificationFailed(
                    reference.uri,
                    "mdoc status reference certificate cannot be decoded: ${e.message}",
                ),
            )
        }
        return resolver.resolveStatus(
            ResolveStatusArgs(
                uri = reference.uri,
                index = reference.index,
                // Identifier Lists are a separate mdoc CWT representation, not a generic
                // Token Status List. The resolver binds the protected typ to the payload.
                expectedSpec = if (reference.identifier != null) null else StatusListSpec.TOKEN_STATUS_LIST,
                expectedFormat = StatusProofFormat.CWT,
                identifier = reference.identifier,
                trustedCerts = trustedCerts,
                expectedCertificate = reference.certificate,
            ),
        )
    }

    companion object {
        const val MECHANISM: String = "mdoc_status"
    }
}
