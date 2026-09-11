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
 */

package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.cbor.CborFullDate
import com.sphereon.cbor.encodeToCborByteArray
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedCborCodec
import com.sphereon.mdoc.data.device.IssuerSignedItem
import com.sphereon.mdoc.data.device.IssuerSignedItemCborCodec
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.Status
import com.sphereon.mdoc.data.mso.IdentifierListInfo
import com.sphereon.mdoc.data.mso.StatusListInfo
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.signingAlgorithms
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import com.sphereon.statuslist.spi.CredentialStatusEnricher
import com.sphereon.statuslist.spi.ReservedStatus
import com.sphereon.statuslist.spi.StatusClaimMergeTarget

private const val DEFAULT_MSO_MDOC_VALIDITY_DAYS: Int = 365
private const val SECONDS_PER_DAY: Long = 24L * 60L * 60L

internal data class MdocCertificateValidityWindow(
    val signedEpochSeconds: Long,
    val validFromEpochSeconds: Long,
    val validUntilEpochSeconds: Long,
)

/**
 * Resolve the MSO validity interval against the authenticated issuer certificate.
 *
 * The issuer certificate is the authority for the MSO's signed/validity dates. In particular,
 * a coarse hour-rounded timestamp may be used only while it remains inside that certificate's
 * interval. A certificate that is not currently valid, cannot be parsed, or leaves no positive
 * MSO interval fails closed instead of causing the MSO to be back/forward-dated.
 */
internal fun calculateMdocCertificateValidityWindow(
    encodedCertificate: String,
    nowEpochSeconds: Long,
    issuanceClockSkewInSeconds: Long,
    expirationInDays: Int?,
): IdkResult<MdocCertificateValidityWindow, IdkError> {
    val certificate =
        try {
            certificateFromDer(encodedCertificate.decodeFrom(Encoding.BASE64))
        } catch (expected: Exception) {
            return Err(
                IdkError.fromString(
                    code = "signing_certificate_chain_invalid",
                    message = "Issuer signing certificate could not be parsed: ${expected.message}",
                ),
            )
        }
    return calculateMdocCertificateValidityWindow(
        certificate = certificate,
        nowEpochSeconds = nowEpochSeconds,
        issuanceClockSkewInSeconds = issuanceClockSkewInSeconds,
        expirationInDays = expirationInDays,
    )
}

internal fun calculateMdocCertificateValidityWindow(
    certificate: Certificate,
    nowEpochSeconds: Long,
    issuanceClockSkewInSeconds: Long,
    expirationInDays: Int?,
): IdkResult<MdocCertificateValidityWindow, IdkError> {
    fun invalidWindow(message: String): IdkResult<MdocCertificateValidityWindow, IdkError> =
        Err(IdkError.fromString(code = "signing_certificate_validity_invalid", message = message))

    val notBefore = certificate.notBefore.epochSeconds
    val notAfter = certificate.notAfter.epochSeconds
    if (notBefore >= notAfter) {
        return invalidWindow("Issuer signing certificate validity interval is empty or reversed")
    }
    if (issuanceClockSkewInSeconds < 0L) {
        return invalidWindow("Issuer issuance clock skew cannot be negative")
    }
    if (expirationInDays != null && expirationInDays <= 0) {
        return invalidWindow("mso_mdoc validity period must be positive")
    }
    if (nowEpochSeconds < notBefore) {
        return Err(
            IdkError.fromString(
                code = "signing_certificate_not_yet_valid",
                message = "Issuer signing certificate is not valid at the current issuance time",
            ),
        )
    }
    if (nowEpochSeconds > notAfter) {
        return Err(
            IdkError.fromString(
                code = "signing_certificate_expired",
                message = "Issuer signing certificate is expired at the current issuance time",
            ),
        )
    }

    val rounded = roundedCredentialIssuanceEpochSeconds(nowEpochSeconds, issuanceClockSkewInSeconds)
    val signed =
        when {
            rounded < notBefore -> notBefore
            rounded <= notAfter -> rounded
            else -> return invalidWindow("Hour-rounded MSO signed date is outside issuer certificate validity")
        }
    if (signed !in notBefore..notAfter) {
        return invalidWindow("MSO signed date is outside issuer certificate validity")
    }

    val validityDays = (expirationInDays ?: DEFAULT_MSO_MDOC_VALIDITY_DAYS).toLong()
    val requestedDuration = validityDays * SECONDS_PER_DAY
    val requestedUntil = signed + requestedDuration
    val validUntil = minOf(requestedUntil, notAfter)
    if (validUntil <= signed || validUntil > notAfter) {
        return invalidWindow("MSO validity interval is empty or exceeds issuer certificate validity")
    }

    return Ok(
        MdocCertificateValidityWindow(
            signedEpochSeconds = signed,
            validFromEpochSeconds = signed,
            validUntilEpochSeconds = validUntil,
        ),
    )
}

/**
 * mso_mdoc format handler (ISO 18013-5).
 *
 * Issues mdoc credentials using [MdocSignService] and the [IssuerSigned.MsoBuilder] API.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<CredentialFormatHandler>())
class MsoMdocFormatHandler(
    private val mdocSignService: MdocSignService,
    private val kms: KeyManagerService,
    private val issuerSignedCborCodec: IssuerSignedCborCodec,
    private val issuerSignedItemCborCodec: IssuerSignedItemCborCodec,
    private val statusEnricherProvider: Provider<CredentialStatusEnricher>? = null,
) : CredentialFormatHandler {
    override val supportedFormat: String = CredentialFormat.MSO_MDOC.value

    override suspend fun canHandle(
        request: CredentialRequest,
        configuration: CredentialConfigurationSupported,
    ): Boolean = configuration.format == CredentialFormat.MSO_MDOC.value

    @Suppress("UNCHECKED_CAST")
    override suspend fun issueCredential(
        request: CredentialRequest,
        context: IssuanceContext,
    ): IdkResult<CredentialEnvelope, IdkError> {
        val doctype =
            context.credentialConfiguration.doctype
                ?: return Err(IdkError.fromString(code = "invalid_credential_request", message = "mso_mdoc requires doctype in credential configuration"))

        // Reserve the status index before constructing the MSO. The reference is part of the
        // signed MSO, so a configured status list must never be silently omitted.
        val statusEnricher = statusEnricherProvider?.invoke()
        val reservedStatus =
            reserveCredentialStatus(statusEnricher, context).getOrElse { return Err(it) }
        var statusBound = reservedStatus == null
        try {
            val mdocStatus =
                reservedStatus
                    ?.let { reserved -> reservedStatusToMdocStatus(reserved) }
                    ?.getOrElse { return Err(it) }

        // Server-resolved signing key; a credential is never signed under a name derived from a
        // caller-visible identifier such as the credential configuration id or the doctype.
        val keyAlias = context.requireSigningKeyName().getOrElse { return Err(it) }
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = keyAlias))
        if (keyResult.isErr) {
            return Err(IdkError.fromString(code = "signing_key_unavailable", message = CREDENTIAL_SIGNING_KEY_UNAVAILABLE))
        }
        val issuerKeyInfo =
            keyResult.value.key
                ?: return Err(IdkError.fromString(code = "signing_key_unavailable", message = CREDENTIAL_SIGNING_KEY_UNAVAILABLE))
        val kmsX5c = issuerKeyInfo.x5c ?: (issuerKeyInfo.key as? Jwk)?.x5c
        val configuredX5c = context.signingX5c
        val x5cChain =
            when {
                !kmsX5c.isNullOrEmpty() -> normalizeX5c(kmsX5c).getOrElse { return Err(it) }
                configuredX5c != null -> normalizeX5c(configuredX5c).getOrElse { return Err(it) }
                else -> null
            }
                ?: return Err(
                    IdkError.fromString(
                        code = "signing_certificate_chain_unavailable",
                        message = "mso_mdoc signing requires an X.509 certificate chain",
                    ),
                )
        val signingKeyInfo =
            ManagedKeyInfo(
                alias = issuerKeyInfo.alias,
                providerId = issuerKeyInfo.providerId,
                resolvedKeyInfo =
                    ResolvedKeyInfo(
                        key = issuerKeyInfo.key,
                        kid = null,
                        opts = issuerKeyInfo.opts,
                        keyVisibility = issuerKeyInfo.keyVisibility,
                        signatureAlgorithm = issuerKeyInfo.signatureAlgorithm,
                        alias = issuerKeyInfo.alias,
                        x5c = x5cChain,
                        providerId = issuerKeyInfo.providerId,
                        keyType = issuerKeyInfo.keyType,
                        keyEncoding = issuerKeyInfo.keyEncoding,
                        noCache = issuerKeyInfo.noCache,
                    ),
            )

        // A claimless mdoc is never a valid fallback. All issuer-signed items must come from the
        // resolved design/pipeline attributes for this request.
        val effectiveAttributes = requireMdocAttributes(context.attributes).getOrElse { return Err(it) }
        val namespacedAttributes = groupAttributesByNamespace(effectiveAttributes, doctype)

        // ISO 18013-5 §9.1.2.4 MSO validity timestamps. Apply clock-skew tolerance and
        // round to the hour so a batch does not carry a precise shared issuance instant.
        // The whole-day validity duration keeps `validUntil` on the same coarse boundary.
        val validityWindow =
            calculateMdocCertificateValidityWindow(
                encodedCertificate = x5cChain.first(),
                nowEpochSeconds = kotlin.time.Clock.System.now().epochSeconds,
                issuanceClockSkewInSeconds = context.issuanceClockSkewInSeconds,
                expirationInDays = context.expirationInDays,
            ).getOrElse { return Err(it) }
        val signedEpochSeconds = validityWindow.signedEpochSeconds
        val validFromEpochSeconds = validityWindow.validFromEpochSeconds
        val validUntilEpochSeconds = validityWindow.validUntilEpochSeconds

        val signed = DateTimeUtils.DEFAULTS.dateTimeLocal(epochSeconds = signedEpochSeconds.toInt())
        val validFrom = DateTimeUtils.DEFAULTS.dateTimeLocal(epochSeconds = validFromEpochSeconds.toInt())
        val validUntil = DateTimeUtils.DEFAULTS.dateTimeLocal(epochSeconds = validUntilEpochSeconds.toInt())

        val builder =
            IssuerSigned
                .MsoBuilder(issuerSignedItemCborCodec = issuerSignedItemCborCodec)
                .withDocType(DocType(doctype))
                .withSigningKeyInfo(signingKeyInfo)
                .withSigned(signed)
                .withValidFrom(validFrom)
                .withValidUntil(validUntil)
                .withStatus(mdocStatus)

        // Device key from holder's proof of possession (required by ISO 18013-5)
        val holderJwk =
            context.holderBindingKey?.let { key ->
                try {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    json.decodeFromJsonElement(Jwk.serializer(), key)
                } catch (_: Exception) {
                    null
                }
            }
        if (holderJwk != null) {
            builder.withDeviceKeyInfo(ResolvedKeyInfo(key = holderJwk, kid = holderJwk.kid))
        }

        var digestCounter = 0u
        for ((namespace, elements) in namespacedAttributes) {
            val items =
                elements.map { (elementName, value) ->
                    val nativeValue =
                        jsonElementToMdocValue(elementName, value).getOrElse { return Err(it) }
                    IssuerSignedItem.create(
                        digestID = DigestID(digestCounter++),
                        elementIdentifier = DataElementIdentifier(elementName),
                        elementValue = nativeValue,
                    ) as IssuerSignedItem<Any>
                }
            builder.addNameSpace(NameSpace(namespace), *items.toTypedArray())
        }

        // ISO 18013-5 requires x5chain in the COSE unprotected header.
        // The certificate chain is on the ManagedKeyInfo but the signing service
        // reads it from the inner key type which may not carry it. Pass it explicitly.
        val unprotectedHeader =
            CoseHeaderCbor().also { header ->
                header.x5chain = x5cChain.encodeToCborByteArray(Encoding.BASE64)
            }

        // Resolve the signing algorithm: prefer key's algorithm, then credential config, then default ES256 for EC keys
        val sigAlg =
            issuerKeyInfo.signatureAlgorithm
                ?: context.credentialConfiguration.signingAlgorithms
                    .firstOrNull()
                    ?.let { SignatureAlgorithm.fromJose(it) }
                ?: SignatureAlgorithm.ECDSA_SHA256

        val document =
            try {
                builder.buildAndSignMdoc(
                    mdocSignService = mdocSignService,
                    signatureAlgorithm = sigAlg,
                    requireDeviceX5Chain = false,
                    unprotectedHeader = unprotectedHeader,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (expected: Exception) {
                return Err(IdkError.fromString(message = "Failed to build and sign mdoc: ${expected.message}"))
            }

        // OID4VCI mso_mdoc: credential is CBOR-encoded IssuerSigned (not full Document)
        val issuerSignedBytes =
            issuerSignedCborCodec.encode(document.issuerSigned).getOrElse {
                return Err(IdkError.fromString(message = "Failed to CBOR-encode IssuerSigned: ${it.message.defaultMessage}"))
            }

        reservedStatus?.let { reserved ->
            checkNotNull(statusEnricher)
                .bind(reserved.handle, credentialId = context.credentialId, credentialHash = null)
                .getOrElse { return Err(it) }
        }
        statusBound = true

        return Ok(
            CredentialEnvelope(
                credential = JsonPrimitive(issuerSignedBytes.encodeToBase64Url()),
                format = CredentialFormat.MSO_MDOC.value,
            ),
        )
        } finally {
            if (!statusBound && reservedStatus != null) {
                try {
                    withContext(NonCancellable) {
                        checkNotNull(statusEnricher).cancel(reservedStatus.handle)
                    }
                } catch (_: Exception) {
                    // Preserve the original issuance error if reservation cleanup also fails.
                }
            }
        }
    }

    companion object {
        /** Converts the generic pre-sign claim into the ISO 18013-5 MSO status structure. */
        internal fun reservedStatusToMdocStatus(
            reserved: ReservedStatus,
        ): IdkResult<Status, IdkError> {
            if (reserved.mergeTarget != StatusClaimMergeTarget.MDOC_STATUS) {
                return Err(
                    IdkError.fromString(
                        code = "status_configuration_unsupported",
                        message = "mso_mdoc requires an ISO 18013-5 status-list binding",
                    ),
                )
            }
            val statusList =
                reserved.claim["status_list"]?.let { element ->
                    if (element is JsonObject) element else null
                }
            val identifierList =
                reserved.claim["identifier_list"]?.let { element ->
                    if (element is JsonObject) element else null
                }
            if (identifierList != null && statusList != null) {
                return Err(
                    IdkError.fromString(
                        code = "status_reference_invalid",
                        message = "mso_mdoc status claim must contain only one status mechanism",
                    ),
                )
            }
            if (identifierList != null) {
                val encodedIdentifier =
                    (identifierList["id"] as? JsonPrimitive)
                        ?.takeIf { it.isString }
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: return Err(
                            IdkError.fromString(
                                code = "status_reference_invalid",
                                message = "mso_mdoc identifier_list.id must be a non-empty base64url value",
                            ),
                        )
                val identifier =
                    try {
                        encodedIdentifier.decodeFromBase64Url()
                    } catch (e: Exception) {
                        return Err(
                            IdkError.fromString(
                                code = "status_reference_invalid",
                                message = "mso_mdoc identifier_list.id is not valid base64url: ${e.message}",
                            ),
                        )
                    }
                val uri =
                    (identifierList["uri"] as? JsonPrimitive)
                        ?.takeIf { it.isString }
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: return Err(
                            IdkError.fromString(
                                code = "status_reference_invalid",
                                message = "mso_mdoc identifier_list.uri must be a non-empty URI",
                            ),
                        )
                return Ok(Status(identifierList = IdentifierListInfo(id = identifier, uri = uri)))
            }
            val statusListObject = statusList
                ?: return Err(
                    IdkError.fromString(
                        code = "status_reference_invalid",
                        message = "mso_mdoc status claim must contain a status_list or identifier_list object",
                    ),
                )
            val index =
                (statusListObject["idx"] as? JsonPrimitive)?.intOrNull
                    ?.takeIf { it >= 0 }
                    ?: return Err(
                        IdkError.fromString(
                            code = "status_reference_invalid",
                            message = "mso_mdoc status_list.idx must be a non-negative integer",
                        ),
                    )
            val uri =
                (statusListObject["uri"] as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?: return Err(
                        IdkError.fromString(
                            code = "status_reference_invalid",
                            message = "mso_mdoc status_list.uri must be a non-empty URI",
                        ),
                        )
            val aggregationUriElement = statusListObject["aggregation_uri"]
            val aggregationUri =
                when {
                    aggregationUriElement == null -> null
                    aggregationUriElement !is JsonPrimitive || !aggregationUriElement.isString || aggregationUriElement.contentOrNull.isNullOrBlank() ->
                        return Err(
                            IdkError.fromString(
                                code = "status_reference_invalid",
                                message = "mso_mdoc status_list.aggregation_uri must be a non-empty URI when present",
                            ),
                        )
                    else -> aggregationUriElement.content
                }
            return Ok(Status(statusList = StatusListInfo(idx = index.toUInt(), uri = uri, aggregationUri = aggregationUri)))
        }

        internal fun requireMdocAttributes(
            attributes: Map<String, JsonElement>,
        ): IdkResult<Map<String, JsonElement>, IdkError> =
            if (attributes.isEmpty()) {
                Err(
                    IdkError.fromString(
                        code = "invalid_credential_request",
                        message = "mso_mdoc issuance requires at least one resolved credential claim",
                    ),
                )
            } else {
                Ok(attributes)
            }

        fun groupAttributesByNamespace(
            attributes: Map<String, JsonElement>,
            doctype: String,
        ): Map<String, List<Pair<String, JsonElement>>> {
            val result = mutableMapOf<String, MutableList<Pair<String, JsonElement>>>()
            for ((rawKey, value) in attributes) {
                // Attribute paths produced by the claims mapper are JSON-pointer-like and may
                // carry one leading slash. That slash is path syntax, not part of the ISO mdoc
                // namespace. Persisting it yielded `/org.iso.18013.5.1`, which is a different
                // namespace and made every mandatory mDL element invisible to wallets.
                val key = rawKey.removePrefix("/")
                val lastDot = key.lastIndexOf('.')
                val (namespace, element) =
                    if (lastDot > 0) {
                        key.substring(0, lastDot) to key.substring(lastDot + 1)
                    } else {
                        doctype to key
                    }
                result.getOrPut(namespace) { mutableListOf() }.add(element to value)
            }
            return result
        }

        fun jsonElementToNativeValue(element: JsonElement): Any =
            when (element) {
                is JsonPrimitive -> {
                    if (element.isString) {
                        element.content.toStructuredMdocValueOrNull()
                            ?.let(::jsonElementToNativeValue)
                            ?: element.content
                    } else {
                        element.booleanOrNull
                            ?: element.longOrNull
                            ?: element.doubleOrNull
                            ?: element.content
                    }
                }
                is JsonArray -> element.map(::jsonElementToNativeValue)
                is JsonObject -> element.mapValues { (_, value) -> jsonElementToNativeValue(value) }
            }

        /**
         * Converts one issuer-signed mdoc element to its native CBOR input value.
         *
         * `driving_privileges` is not a generic JSON array: ISO 18013-5 defines the nested
         * `issue_date` and `expiry_date` members as CBOR full-date values (tag 1004). Encoding
         * those members as ordinary text causes strict wallets to reject or hide the complete
         * element. The developer form deliberately keeps the value as editable JSON text, so
         * this boundary validates that text and materializes the required tagged date values.
         */
        internal fun jsonElementToMdocValue(
            elementIdentifier: String,
            element: JsonElement,
        ): IdkResult<Any, IdkError> {
            if (elementIdentifier != DRIVING_PRIVILEGES_ELEMENT) {
                return Ok(jsonElementToNativeValue(element))
            }

            return runCatching { drivingPrivilegesToNativeValue(element) }
                .fold(
                    onSuccess = ::Ok,
                    onFailure = {
                        Err(
                            IdkError.fromString(
                                code = "invalid_credential_request",
                                message = "mso_mdoc driving_privileges must be a non-empty ISO 18013-5 array",
                            ),
                        )
                    },
                )
        }

        private fun drivingPrivilegesToNativeValue(element: JsonElement): List<Map<String, Any>> {
            val structured =
                if (element is JsonPrimitive && element.isString) {
                    element.content.toStructuredMdocValueOrNull()
                        ?: throw IllegalArgumentException("driving_privileges is not structured JSON")
                } else {
                    element
                }
            val privileges = structured as? JsonArray
                ?: throw IllegalArgumentException("driving_privileges is not an array")
            require(privileges.isNotEmpty()) { "driving_privileges is empty" }

            return privileges.mapIndexed { index, entry ->
                val privilege = entry as? JsonObject
                    ?: throw IllegalArgumentException("driving_privileges[$index] is not an object")
                linkedMapOf<String, Any>().apply {
                    put(
                        "vehicle_category_code",
                        privilege.requiredString("vehicle_category_code", index),
                    )
                    privilege.optionalString("issue_date", index)?.let { value ->
                        LocalDate.parse(value)
                        put("issue_date", CborFullDate(value))
                    }
                    privilege.optionalString("expiry_date", index)?.let { value ->
                        LocalDate.parse(value)
                        put("expiry_date", CborFullDate(value))
                    }
                    privilege["codes"]?.let { codes ->
                        put("codes", codes.toDrivingPrivilegeCodes(index))
                    }
                }
            }
        }

        private fun JsonObject.requiredString(
            name: String,
            privilegeIndex: Int,
        ): String =
            optionalString(name, privilegeIndex)
                ?: throw IllegalArgumentException("driving_privileges[$privilegeIndex].$name is required")

        private fun JsonObject.optionalString(
            name: String,
            privilegeIndex: Int,
        ): String? {
            val value = this[name] ?: return null
            val primitive = value as? JsonPrimitive
                ?: throw IllegalArgumentException("driving_privileges[$privilegeIndex].$name is not text")
            require(primitive.isString && primitive.content.isNotBlank()) {
                "driving_privileges[$privilegeIndex].$name is blank"
            }
            return primitive.content
        }

        private fun JsonElement.toDrivingPrivilegeCodes(privilegeIndex: Int): List<Map<String, String>> {
            val entries = this as? JsonArray
                ?: throw IllegalArgumentException("driving_privileges[$privilegeIndex].codes is not an array")
            return entries.mapIndexed { codeIndex, entry ->
                val code = entry as? JsonObject
                    ?: throw IllegalArgumentException("driving_privileges[$privilegeIndex].codes[$codeIndex] is not an object")
                linkedMapOf<String, String>().apply {
                    put("code", code.requiredString("code", privilegeIndex))
                    code.optionalString("sign", privilegeIndex)?.let { put("sign", it) }
                    code.optionalString("value", privilegeIndex)?.let { put("value", it) }
                }
            }
        }

        /**
         * The developer form represents nested mdoc values as editable JSON text. Parse only
         * object/array-shaped text here so ordinary string claims remain ordinary strings.
         */
        private fun String.toStructuredMdocValueOrNull(): JsonElement? {
            val trimmed = trim()
            if (!trimmed.startsWith('[') && !trimmed.startsWith('{')) return null
            return runCatching { Json.parseToJsonElement(trimmed) }
                .getOrNull()
                ?.takeIf { it is JsonArray || it is JsonObject }
        }

        private const val DRIVING_PRIVILEGES_ELEMENT = "driving_privileges"
    }
}
