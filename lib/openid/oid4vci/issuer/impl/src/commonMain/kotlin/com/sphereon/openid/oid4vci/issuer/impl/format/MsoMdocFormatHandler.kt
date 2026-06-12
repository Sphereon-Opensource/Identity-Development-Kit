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

import com.sphereon.cbor.encodeToCborByteArray
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
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
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.signingAlgorithms
import com.sphereon.openid.oid4vci.issuer.format.CredentialEnvelope
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

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
        // Fail closed: this handler cannot embed a status entry into the MSO, so a credential
        // configuration bound to a status list must not issue through it.
        unsupportedStatusListBinding(context)?.let { return Err(it) }

        val doctype =
            context.credentialConfiguration.doctype
                ?: return Err(IdkError.fromString(code = "invalid_credential_request", message = "mso_mdoc requires doctype in credential configuration"))

        val keyAlias = context.signingKeyAlias ?: context.credentialConfigurationId
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = keyAlias))
        if (keyResult.isErr) {
            return Err(IdkError.fromString(message = "Failed to resolve issuer signing key '$keyAlias': ${keyResult.error.message.defaultMessage}"))
        }
        val issuerKeyInfo =
            keyResult.value.key
                ?: return Err(IdkError.fromString(message = "Signing key '$keyAlias' not found in KMS"))

        // For mdoc, ensure at least the mandatory claims have values.
        // If the caller didn't provide age_over_18 for the EU AV doctype, default it to true.
        val effectiveAttributes =
            context.attributes.ifEmpty {
                mapOf("age_over_18" to JsonPrimitive(true))
            }
        val namespacedAttributes = groupAttributesByNamespace(effectiveAttributes, doctype)

        // ISO 18013-5 §9.1.2.4 MSO validity timestamps. `signed` and `validFrom` are
        // shifted backward by the configured issuance clock-skew so wallets whose clocks
        // are slightly ahead of ours still see the MSO as already-valid on receipt —
        // same rationale and config knob (`issuanceClockSkewInSeconds`) as the SD-JWT
        // VC `iat` treatment. Without this, wallets that verify the MSO immediately
        // upon issuance reject with "MSO must be valid at time of verification".
        val nowEpochSeconds =
            kotlin.time.Clock.System
                .now()
                .epochSeconds
        val signedEpochSeconds = nowEpochSeconds - context.issuanceClockSkewInSeconds
        val validFromEpochSeconds = signedEpochSeconds
        val validUntilEpochSeconds =
            signedEpochSeconds + ((context.expirationInDays ?: DEFAULT_VALIDITY_DAYS).toLong() * SECONDS_PER_DAY)

        val signed = DateTimeUtils.DEFAULTS.dateTimeLocal(epochSeconds = signedEpochSeconds.toInt())
        val validFrom = DateTimeUtils.DEFAULTS.dateTimeLocal(epochSeconds = validFromEpochSeconds.toInt())
        val validUntil = DateTimeUtils.DEFAULTS.dateTimeLocal(epochSeconds = validUntilEpochSeconds.toInt())

        val builder =
            IssuerSigned
                .MsoBuilder(issuerSignedItemCborCodec = issuerSignedItemCborCodec)
                .withDocType(DocType(doctype))
                .withSigningKeyInfo(issuerKeyInfo)
                .withSigned(signed)
                .withValidFrom(validFrom)
                .withValidUntil(validUntil)

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
                    IssuerSignedItem.create(
                        digestID = DigestID(digestCounter++),
                        elementIdentifier = DataElementIdentifier(elementName),
                        elementValue = jsonElementToNativeValue(value),
                    ) as IssuerSignedItem<Any>
                }
            builder.addNameSpace(NameSpace(namespace), *items.toTypedArray())
        }

        // ISO 18013-5 requires x5chain in the COSE unprotected header.
        // The certificate chain is on the ManagedKeyInfo but the signing service
        // reads it from the inner key type which may not carry it. Pass it explicitly.
        val x5cChain = issuerKeyInfo.x5c
        val unprotectedHeader =
            if (!x5cChain.isNullOrEmpty()) {
                val header = CoseHeaderCbor()
                header.x5chain = x5cChain.encodeToCborByteArray(Encoding.BASE64)
                header
            } else {
                null
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
            } catch (expected: Exception) {
                return Err(IdkError.fromString(message = "Failed to build and sign mdoc: ${expected.message}"))
            }

        // OID4VCI mso_mdoc: credential is CBOR-encoded IssuerSigned (not full Document)
        val issuerSignedBytes =
            issuerSignedCborCodec.encode(document.issuerSigned).getOrElse {
                return Err(IdkError.fromString(message = "Failed to CBOR-encode IssuerSigned: ${it.message.defaultMessage}"))
            }

        return Ok(
            CredentialEnvelope(
                credential = JsonPrimitive(issuerSignedBytes.encodeToBase64Url()),
                format = CredentialFormat.MSO_MDOC.value,
            ),
        )
    }

    companion object {
        private const val SECONDS_PER_DAY: Long = 24L * 60L * 60L

        /** Fallback MSO validity when no `expirationInDays` is configured on the credential. */
        private const val DEFAULT_VALIDITY_DAYS: Int = 365

        fun groupAttributesByNamespace(
            attributes: Map<String, JsonElement>,
            doctype: String,
        ): Map<String, List<Pair<String, JsonElement>>> {
            val result = mutableMapOf<String, MutableList<Pair<String, JsonElement>>>()
            for ((key, value) in attributes) {
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

        fun jsonElementToNativeValue(element: JsonElement): Any {
            if (element is JsonPrimitive) {
                if (element.isString) {
                    return element.content
                }
                element.booleanOrNull?.let { return it }
                element.longOrNull?.let { return it }
                element.doubleOrNull?.let { return it }
                return element.content
            }
            return element.toString()
        }
    }
}
