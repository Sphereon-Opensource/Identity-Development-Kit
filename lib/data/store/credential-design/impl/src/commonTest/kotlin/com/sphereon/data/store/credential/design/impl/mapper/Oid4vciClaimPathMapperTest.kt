/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.credential.design.impl.mapper

import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.DesignClaimPath
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class Oid4vciClaimPathMapperTest {
    // ---- toOid4vciPath ----

    @Test
    fun toOid4vciPathSimpleProperty() {
        val designPath: DesignClaimPath = listOf(ClaimPathSegment.Property("given_name"))
        val result = Oid4vciClaimPathMapper.toOid4vciPath(designPath)
        assertEquals(listOf(JsonPrimitive("given_name")), result)
    }

    @Test
    fun toOid4vciPathNestedProperties() {
        val designPath: DesignClaimPath =
            listOf(
                ClaimPathSegment.Property("address"),
                ClaimPathSegment.Property("street"),
            )
        val result = Oid4vciClaimPathMapper.toOid4vciPath(designPath)
        assertEquals(listOf(JsonPrimitive("address"), JsonPrimitive("street")), result)
    }

    @Test
    fun toOid4vciPathArrayIndex() {
        val designPath: DesignClaimPath =
            listOf(
                ClaimPathSegment.Property("items"),
                ClaimPathSegment.Index(2),
            )
        val result = Oid4vciClaimPathMapper.toOid4vciPath(designPath)
        assertEquals(listOf(JsonPrimitive("items"), JsonPrimitive(2)), result)
    }

    @Test
    fun toOid4vciPathAnyArrayElement() {
        val designPath: DesignClaimPath =
            listOf(
                ClaimPathSegment.Property("degrees"),
                ClaimPathSegment.AnyArrayElement,
                ClaimPathSegment.Property("type"),
            )
        val result = Oid4vciClaimPathMapper.toOid4vciPath(designPath)
        assertEquals(listOf(JsonPrimitive("degrees"), JsonNull, JsonPrimitive("type")), result)
    }

    @Test
    fun toOid4vciPathMdocNamespace() {
        val designPath: DesignClaimPath =
            listOf(
                ClaimPathSegment.Property("org.iso.18013.5.1"),
                ClaimPathSegment.Property("given_name"),
            )
        val result = Oid4vciClaimPathMapper.toOid4vciPath(designPath)
        assertEquals(listOf(JsonPrimitive("org.iso.18013.5.1"), JsonPrimitive("given_name")), result)
    }

    // ---- toDesignPath ----

    @Test
    fun toDesignPathSimpleProperty() {
        val oid4vciPath = listOf(JsonPrimitive("given_name"))
        val result = Oid4vciClaimPathMapper.toDesignPath(oid4vciPath)
        assertEquals(listOf(ClaimPathSegment.Property("given_name")), result)
    }

    @Test
    fun toDesignPathNestedProperties() {
        val oid4vciPath = listOf(JsonPrimitive("address"), JsonPrimitive("street"))
        val result = Oid4vciClaimPathMapper.toDesignPath(oid4vciPath)
        assertEquals(
            listOf(ClaimPathSegment.Property("address"), ClaimPathSegment.Property("street")),
            result,
        )
    }

    @Test
    fun toDesignPathArrayIndex() {
        val oid4vciPath = listOf(JsonPrimitive("items"), JsonPrimitive(2))
        val result = Oid4vciClaimPathMapper.toDesignPath(oid4vciPath)
        assertEquals(
            listOf(ClaimPathSegment.Property("items"), ClaimPathSegment.Index(2)),
            result,
        )
    }

    @Test
    fun toDesignPathAnyArrayElement() {
        val oid4vciPath = listOf(JsonPrimitive("degrees"), JsonNull, JsonPrimitive("type"))
        val result = Oid4vciClaimPathMapper.toDesignPath(oid4vciPath)
        assertEquals(
            listOf(
                ClaimPathSegment.Property("degrees"),
                ClaimPathSegment.AnyArrayElement,
                ClaimPathSegment.Property("type"),
            ),
            result,
        )
    }

    @Test
    fun toDesignPathMdocNamespace() {
        val oid4vciPath = listOf(JsonPrimitive("org.iso.18013.5.1"), JsonPrimitive("given_name"))
        val result = Oid4vciClaimPathMapper.toDesignPath(oid4vciPath)
        assertEquals(
            listOf(
                ClaimPathSegment.Property("org.iso.18013.5.1"),
                ClaimPathSegment.Property("given_name"),
            ),
            result,
        )
    }

    // ---- round-trip ----

    @Test
    fun roundTripDesignToOid4vciAndBack() {
        val original: DesignClaimPath =
            listOf(
                ClaimPathSegment.Property("degrees"),
                ClaimPathSegment.AnyArrayElement,
                ClaimPathSegment.Property("type"),
                ClaimPathSegment.Index(0),
            )
        val oid4vciPath = Oid4vciClaimPathMapper.toOid4vciPath(original)
        val result = Oid4vciClaimPathMapper.toDesignPath(oid4vciPath)
        assertEquals(original, result)
    }
}
