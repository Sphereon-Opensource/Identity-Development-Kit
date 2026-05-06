/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.api.json.jcs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class JcsConformanceTest {
    @Test
    fun sortsMembersByUtf16Order() {
        val input =
            buildJsonObject {
                put("b", 1)
                put("a", 2)
                put("A", 3)
            }
        assertEquals("""{"A":3,"a":2,"b":1}""", Jcs.canonicalString(input))
    }

    @Test
    fun omitsInsignificantWhitespace() {
        val input = Json.parseToJsonElement("""{  "x"  :  [ 1 , 2 , 3 ]  }""")
        assertEquals("""{"x":[1,2,3]}""", Jcs.canonicalString(input))
    }

    @Test
    fun emitsLiteralTokensForNullTrueFalse() {
        val input =
            buildJsonObject {
                put("a", JsonPrimitive(true))
                put("b", JsonPrimitive(false))
                put("c", JsonPrimitive(null as String?))
            }
        assertEquals("""{"a":true,"b":false,"c":null}""", Jcs.canonicalString(input))
    }

    @Test
    fun escapesRequiredControlCharacters() {
        val input = buildJsonObject { put("s", "ab\nc\td\"\\e") }
        assertEquals("""{"s":"ab\nc\td\"\\e"}""", Jcs.canonicalString(input))
    }

    @Test
    fun escapesControlCharactersWithUnicodeSequence() {
        val input = buildJsonObject { put("s", "\u0001\u001f") }
        assertEquals("""{"s":"\u0001\u001f"}""", Jcs.canonicalString(input))
    }

    @Test
    fun roundTripsPolicyBindingShape() {
        val input =
            buildJsonObject {
                put("required_plan_id", "plan:default:1.0")
                put("classification", "CONFIDENTIAL")
                put("legal_basis", "gdpr.art6.1a.consent")
                put("processing_purpose", "issuance")
                put("consent_id", "c-42")
                put(
                    "regulatory_refs",
                    buildJsonArray {
                        add(JsonPrimitive("gdpr.art32"))
                        add(JsonPrimitive("iso27001.control.8.24"))
                    },
                )
                put("requires_dpia", false)
                put("retention_class", "RECORD_KEEPING")
                put("retention_days", 2555)
                put("retention_expires_at", JsonPrimitive(null as String?))
                put("retention_action", "CRYPTO_SHRED")
                put("jurisdiction", "EU-NL")
                put(
                    "data_residency_regions",
                    buildJsonArray { add(JsonPrimitive("EU-NL")) },
                )
                put("lifecycle", "PERMANENT")
                put("preserve_on_owner_deletion", false)
                put("wrap_algorithm_floor", "HPKE_X25519_ML_KEM_768_DRAFT_05")
                put("zero_access_unwrap_surface", "BROWSER_ALLOWED")
                put("encryption_level", "MANAGED")
                put("required_plan_ttl_override", JsonPrimitive(null as String?))
            }
        val canonical = Jcs.canonicalString(input)
        val reparsed = Json.parseToJsonElement(canonical) as JsonObject
        assertEquals(input.size, reparsed.size)
        assertEquals(canonical, Jcs.canonicalString(reparsed))
    }

    @Test
    fun canonicalizeIsByteStable() {
        val a = Jcs.canonicalize("""{"b":1,"a":2}""")
        val b = Jcs.canonicalize("""{ "a" : 2 , "b" : 1 }""")
        assertEquals(a.decodeToString(), b.decodeToString())
    }

    @Test
    fun nestedObjectsSortAtEveryLevel() {
        val input = Json.parseToJsonElement("""{"z":{"b":1,"a":2},"a":[{"y":1,"x":2}]}""")
        assertEquals(
            """{"a":[{"x":2,"y":1}],"z":{"a":2,"b":1}}""",
            Jcs.canonicalString(input),
        )
    }

    @Test
    fun emptyCollectionsEmitEmptyLiterals() {
        val input =
            buildJsonObject {
                put("a", JsonArray(emptyList()))
                put("b", JsonObject(emptyMap()))
            }
        assertEquals("""{"a":[],"b":{}}""", Jcs.canonicalString(input))
    }

    @Test
    fun integersEmitWithoutFractionalPart() {
        val input =
            buildJsonObject {
                put("n", 0)
                put("m", -42)
                put("l", 1234567890123L)
            }
        assertEquals("""{"l":1234567890123,"m":-42,"n":0}""", Jcs.canonicalString(input))
    }
}
