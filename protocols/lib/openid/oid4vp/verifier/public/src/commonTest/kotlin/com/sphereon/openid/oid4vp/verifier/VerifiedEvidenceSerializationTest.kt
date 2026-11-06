/*
 * (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */
package com.sphereon.openid.oid4vp.verifier

import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.catalog.model.AttestationTypeKeyKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class VerifiedEvidenceSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun shouldOmitAbsentTrustAndDecodeHistoricalExplicitNull() {
        val evidence = VerifiedCredentialEvidence(
            presentationSha256 = "digest", verifiedAtEpochMillis = 1,
            status = VerifiedCredentialStatus(VerifiedCredentialStatusOutcome.ACCEPT, required = false, rejectOnUnresolvable = true),
        )
        val encoded = json.encodeToJsonElement(VerifiedCredentialEvidence.serializer(), evidence).jsonObject
        assertFalse("trust" in encoded)
        val historical = JsonObject(encoded + ("trust" to JsonNull))
        assertEquals(evidence, json.decodeFromJsonElement(VerifiedCredentialEvidence.serializer(), historical))
        val populated = evidence.copy(trust = CredentialTrustValidation(enabled = true, trusted = true))
        val populatedJson = json.encodeToJsonElement(VerifiedCredentialEvidence.serializer(), populated).jsonObject
        assertNotNull(populatedJson["trust"])
        assertEquals(populated, json.decodeFromJsonElement(VerifiedCredentialEvidence.serializer(), populatedJson))
    }

    @Test
    fun shouldOmitAbsentCredentialTypeAndDecodeHistoricalExplicitNull() {
        val mark = ScopedTrustMarkValidation(
            outcome = ScopedTrustMarkOutcome.UNAVAILABLE, policyDigest = "policy", issuer = "issuer",
            trustMarkType = "mark", scopePolicyId = "scope", scopePolicyDigest = "digest",
            authorityDomainId = "domain", authorityAnchorId = "anchor", checkedAtEpochMillis = 1,
        )
        val encoded = json.encodeToJsonElement(ScopedTrustMarkValidation.serializer(), mark).jsonObject
        assertFalse("credentialType" in encoded)
        assertEquals(JsonPrimitive("issuer"), encoded["issuer"])
        assertFalse("attemptedIssuer" in encoded)
        assertFalse("verifiedIssuer" in encoded)
        val historical = JsonObject(encoded + ("credentialType" to JsonNull))
        assertEquals(mark, json.decodeFromJsonElement(ScopedTrustMarkValidation.serializer(), historical))
        val populated = mark.copy(credentialType = AttestationTypeKey(AttestationTypeKeyKind.VCT, "urn:example:adl"))
        val populatedJson = json.encodeToJsonElement(ScopedTrustMarkValidation.serializer(), populated).jsonObject
        assertNotNull(populatedJson["credentialType"])
        assertEquals(populated, json.decodeFromJsonElement(ScopedTrustMarkValidation.serializer(), populatedJson))
    }
}
