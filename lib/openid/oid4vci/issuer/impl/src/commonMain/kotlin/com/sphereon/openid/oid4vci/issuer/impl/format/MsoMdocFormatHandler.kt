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
import com.sphereon.crypto.core.ManagedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.x509.x5cWithoutTerminalSelfSignedRoot
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
        val x5cChain =
            issuerKeyInfo.x5c?.takeIf { it.isNotEmpty() }
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
                        x5c = x5cWithoutTerminalSelfSignedRoot(x5cChain),
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
        val nowEpochSeconds =
            kotlin.time.Clock.System
                .now()
                .epochSeconds
        val signedEpochSeconds =
            roundedCredentialIssuanceEpochSeconds(
                nowEpochSeconds = nowEpochSeconds,
                issuanceClockSkewInSeconds = context.issuanceClockSkewInSeconds,
            )
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
                .withSigningKeyInfo(signingKeyInfo)
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
    }
}
