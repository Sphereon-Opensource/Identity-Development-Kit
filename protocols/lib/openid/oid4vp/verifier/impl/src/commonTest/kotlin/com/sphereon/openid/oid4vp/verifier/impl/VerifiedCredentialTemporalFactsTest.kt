package com.sphereon.openid.oid4vp.verifier.impl

import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Instant

class VerifiedCredentialTemporalFactsTest {
    @Test fun missingFactsAreDistinctFromMalformedOrUnsupportedFacts() {
        assertNull(verifiedCredentialTemporalFacts(null))
        assertNotNull(verifiedCredentialTemporalFacts(JsonObject(emptyMap())))
        for (value in listOf("null", "\"2030-01-01T00:00:00Z\"", "{}")) {
            assertNull(verifiedCredentialTemporalFacts(Json.parseToJsonElement("{\"exp\":$value}").jsonObject))
        }
        assertNull(verifiedCredentialTemporalFacts(Json.parseToJsonElement("{\"validUntil\":\"malformed\"}").jsonObject, vcdm = true))
    }
    @Test fun authenticatedEnvelopeBoundsAreIntersectedAndSubjectClaimsDoNotOverrideThem() {
        val facts = assertNotNull(verifiedCredentialTemporalFacts(Json.parseToJsonElement("""{
            "iat":1700000000,"nbf":1700000001,"exp":1900000000,
            "vc":{"validFrom":"2024-01-01T00:00:00Z","validUntil":"2028-01-01T00:00:00Z",
            "credentialSubject":{"exp":9999999999,"validUntil":"2099-01-01T00:00:00Z"}}
        }""").jsonObject, vcdm = true))
        assertEquals(1700000000000L, facts.issuedAtEpochMillis)
        assertEquals(Instant.parse("2024-01-01T00:00:00Z").toEpochMilliseconds(), facts.notBeforeEpochMillis)
        assertEquals(Instant.parse("2028-01-01T00:00:00Z").toEpochMilliseconds(), facts.expiresAtEpochMillis)
        assertNull(verifiedCredentialTemporalFacts(Json.parseToJsonElement("""{"nbf":20,"exp":10}""").jsonObject))
    }
}
