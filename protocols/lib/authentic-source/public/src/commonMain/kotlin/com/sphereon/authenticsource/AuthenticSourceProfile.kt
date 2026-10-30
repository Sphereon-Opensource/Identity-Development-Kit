/* Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 * See https://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.authenticsource

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/** Structural profile only; success does not authenticate the provider, subject or result. */
@JsExportCompat
enum class SourceProfile { ETSI_TS_119_478_V1_1_1, EU_2025_1569_ANNEX_IV_AS_AMENDED_2026_1735 }
@JsExportCompat
enum class SourceBinding { HTTP }
@JsExportCompat
enum class Outcome { MATCH, MATCH_WITH_VARIATION, NO_MATCH, UNKNOWN }

/** Projections are not ETSI wire serializers. JSON text must decode to an object. */
@JsExportCompat
data class AttributeCandidate(val attributeIdentifier: String, val objectJson: String)
@JsExportCompat
data class AttributeResultProjection(val attributeIdentifier: String, val resultUri: String, val objectJson: String? = null)
@JsExportCompat
data class ProviderIdentifier(val type: String, val identifier: String)
@JsExportCompat
data class LawProjection(val legislationIdentifier: String, val legalBasis: String? = null)
/** Not a wire DTO: a future lossless parser must map and validate the annex fields. */
@JsExportCompat
data class ProviderProjection(val legalName: String, val identifiers: List<ProviderIdentifier> = emptyList(), val establishedByLaw: LawProjection? = null, val currentAddress: String? = null)
@JsExportCompat
data class VerifyRequest(val attributes: List<AttributeCandidate>, val hasFragments: Boolean = false)
@JsExportCompat
data class VerifyResponse(val provider: ProviderProjection, val authenticSource: ProviderProjection? = null, val attributeVerificationResults: List<AttributeResultProjection> = emptyList(), val hasFragmentResults: Boolean = false)

/** Expectations are correlation inputs, never authorization or cryptographic proof. */
@JsExportCompat
data class VerificationContext(val profile: SourceProfile, val expectedProvider: ProviderProjection, val expectedAuthenticSource: ProviderProjection?)
@JsExportCompat
data class CorrelatedAttribute(val candidate: AttributeCandidate, val outcome: Outcome, val returnedObjectJson: String?)
@JsExportCompat
data class CorrelatedResponse(val attributes: List<CorrelatedAttribute>)

interface AuthenticSourceProfileValidator {
    fun validate(request: VerifyRequest, response: VerifyResponse, context: VerificationContext): IdkResult<CorrelatedResponse, IdkError>
}
