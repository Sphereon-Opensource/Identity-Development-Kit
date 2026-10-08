/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.mdoc.transport.oid4vp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class Iso18013PresentationDefinitionValidationTest {
    @Test
    fun acceptsTheRestrictedAnnexBShapeAndKeepsTheOriginalJson() {
        val definition = Json.parseToJsonElement(validDefinition)

        assertSame(definition, validateIso18013PresentationDefinition(definition))
    }

    @Test
    fun rejectsPresentationExchangeMembersOutsideTheAnnexBProfile() {
        val definition =
            Json.parseToJsonElement(validDefinition).jsonObject.toMutableMap().apply {
                put("name", Json.parseToJsonElement("\"unsupported\""))
            }

        assertFailsWith<IllegalArgumentException> {
            validateIso18013PresentationDefinition(Json.parseToJsonElement(definition.toString()))
        }
    }

    @Test
    fun rejectsAlternativeFormatsAndNonRequiredLimitDisclosure() {
        val definition =
            Json.parseToJsonElement(validDefinition).toString()
                .replace("\"mso_mdoc\"", "\"dc+sd-jwt\"")

        assertFailsWith<IllegalArgumentException> {
            validateIso18013PresentationDefinition(Json.parseToJsonElement(definition))
        }

        val wrongDisclosure =
            Json.parseToJsonElement(validDefinition).toString()
                .replace("\"limit_disclosure\":\"required\"", "\"limit_disclosure\":\"preferred\"")
        assertFailsWith<IllegalArgumentException> {
            validateIso18013PresentationDefinition(Json.parseToJsonElement(wrongDisclosure))
        }
    }

    private companion object {
        val validDefinition =
            """
            {
              "id":"mDL-sample-req",
              "input_descriptors":[{
                "id":"org.iso.18013.5.1.mDL",
                "format":{"mso_mdoc":{"alg":["ES256","EdDSA"]}},
                "constraints":{
                  "limit_disclosure":"required",
                  "fields":[{
                    "path":["$['org.iso.18013.5.1']['family_name']"],
                    "intent_to_retain":false
                  }]
                }
              }]
            }
            """.trimIndent()
    }
}
