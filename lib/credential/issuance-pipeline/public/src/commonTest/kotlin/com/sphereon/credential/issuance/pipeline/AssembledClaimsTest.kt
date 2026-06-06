/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline

import com.sphereon.attribute.flow.AttributeEvidence
import com.sphereon.attribute.flow.AttributeKey
import com.sphereon.attribute.flow.KeyUsage
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class AssembledClaimsTest {
    @Test
    fun serializationRoundTrip() {
        val assembled =
            AssembledClaims(
                bindingId = "PID",
                attributes = mapOf("given_name" to JsonPrimitive("Jane")),
                issuerKeyRef = AttributeKey(KeyInfo<KeyType>(kid = "issuer-key-1"), KeyUsage.ISSUER_SIGNING),
                evidence = listOf(AttributeEvidence(evidenceId = "ev-1", evidenceType = "idv_result")),
            )
        val json = Json
        val decoded =
            json.decodeFromString(
                AssembledClaims.serializer(),
                json.encodeToString(AssembledClaims.serializer(), assembled),
            )
        assertEquals(assembled, decoded)
    }
}
