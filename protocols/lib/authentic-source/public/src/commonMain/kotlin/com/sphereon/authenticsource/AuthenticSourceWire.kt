/* Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 * See https://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.authenticsource

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/** ETSI TS 119 478 v1.1.1 HTTP verification wire fields. Parsing is not authentication. */
@JsExportCompat
data class WireAttribute(val attributeIdentifier: String, val attributeValueJson: String)
@JsExportCompat
data class WireFragment(val attributeIdentifier: String, val location: String, val valueJson: String)
@JsExportCompat
data class WireVerifyRequest(val attributes: List<WireAttribute>? = null, val attributeFragments: List<WireFragment>? = null, val mandateJson: String? = null)
@JsExportCompat
data class WireProviderIdentifier(val type: String, val identifier: String)
@JsExportCompat
data class WireLaw(val legislationIdentifier: String, val legalBasis: String? = null)
@JsExportCompat
data class WireProvider(val legalName: String, val identifiers: List<WireProviderIdentifier>? = null, val establishedByLaw: WireLaw? = null, val currentAddress: String? = null)
@JsExportCompat
data class WireAttributeResult(val attributeIdentifier: String, val attributeVerificationResult: String, val attributeValueJson: String? = null)
@JsExportCompat
data class WireFragmentResult(val attributeIdentifier: String, val fragmentVerificationResult: String, val fragmentValueJson: String? = null)
/** Original document and extracted object strings preserve their exact input lexemes. */
@JsExportCompat
data class WireVerifyResponse(val originalJson: String, val provider: WireProvider, val authenticSource: WireProvider? = null, val attributeVerificationResults: List<WireAttributeResult>? = null, val fragmentVerificationResults: List<WireFragmentResult>? = null, val mandateResultJson: String? = null)

interface AuthenticSourceWireCodec {
    fun encodeRequest(request: WireVerifyRequest): IdkResult<String, IdkError>
    fun decodeResponse(json: String): IdkResult<WireVerifyResponse, IdkError>
}

/** An admitted binding is supplied by the host's policy owner; catalogue results are proposals only. */
data class SourceTransactionRef(val transactionId: String, val subjectRef: String, val bindingRevision: String)
data class SourceEndpointBinding(val endpoint: String, val providerRef: String, val sourceRef: String, val revision: String, val retrievalAdvertised: Boolean)
data class UnverifiedSourceResponse<T>(val requestContext: SourceTransactionRef, val providerRef: String, val sourceRef: String, val payload: T)
data class WireRetrieveRequest(val attributeIdentifiers: List<String>, val mandateJson: String? = null)
data class WireRetrievedAttribute(val attributeIdentifier: String, val attributeValueJson: String)
data class WireRetrieveResponse(val originalJson: String, val provider: WireProvider, val authenticSource: WireProvider?, val attributes: List<WireRetrievedAttribute>?, val mandateResultJson: String?)

/** Host implementation owns token handling, callback replay/expiry and exact transaction/binding checks.
 * The capability executes the request internally; no token or callback state enters results or logs. */
interface SourceAuthorizationOwner {
    suspend fun authorize(binding: SourceEndpointBinding, transaction: SourceTransactionRef): IdkResult<SourceAuthorizedCall, IdkError>
}
interface SourceAuthorizedCall {
    suspend fun postJson(url: String, body: String): IdkResult<SourceHttpResponse, IdkError>
}
class SourceHttpResponse(val status: Int, val body: String, val contentType: String?) {
    override fun toString(): String = "SourceHttpResponse(status=$status, body=<redacted>)"
}
