/*
 * © 2025 Sphereon International B.V.
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
 *
 */

package com.sphereon.credential.claims.mapper.impl.resolver

import com.sphereon.openid.oid4vp.common.CredentialFormat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SdJwtClaimResolverTest {

    private val resolver = SdJwtClaimResolver()

    @Test
    fun `supportedFormats should include SD_JWT_DC and SD_JWT_VC`() {
        assertTrue(resolver.supportedFormats.contains(CredentialFormat.SD_JWT_DC))
        assertTrue(resolver.supportedFormats.contains(CredentialFormat.SD_JWT_VC))
    }

    @Test
    fun `supports should return true for SD_JWT_DC`() {
        assertTrue(resolver.supports(CredentialFormat.SD_JWT_DC))
    }

    @Test
    fun `supports should return true for SD_JWT_VC`() {
        assertTrue(resolver.supports(CredentialFormat.SD_JWT_VC))
    }

    @Test
    fun `supports should return false for unsupported formats`() {
        assertFalse(resolver.supports(CredentialFormat.MSO_MDOC))
        assertFalse(resolver.supports(CredentialFormat.JWT_VC_JSON))
    }

    @Test
    fun `extractClaim should extract top-level claim from disclosedClaims`() = runTest {
        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
            put("family_name", JsonPrimitive("Doe"))
        }

        val result = resolver.extractClaim(
            credential = "",
            format = CredentialFormat.SD_JWT_DC,
            claimPath = listOf("given_name"),
            disclosedClaims = disclosedClaims
        )

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("John"), result.value)
    }

    @Test
    fun `extractClaim should extract nested claim from disclosedClaims`() = runTest {
        val disclosedClaims = buildJsonObject {
            put("address", buildJsonObject {
                put("street", JsonPrimitive("123 Main St"))
                put("city", JsonPrimitive("Amsterdam"))
            })
        }

        val result = resolver.extractClaim(
            credential = "",
            format = CredentialFormat.SD_JWT_DC,
            claimPath = listOf("address", "street"),
            disclosedClaims = disclosedClaims
        )

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("123 Main St"), result.value)
    }

    @Test
    fun `extractClaim should return null for non-existent claim`() = runTest {
        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }

        val result = resolver.extractClaim(
            credential = "",
            format = CredentialFormat.SD_JWT_DC,
            claimPath = listOf("email"),
            disclosedClaims = disclosedClaims
        )

        assertTrue(result.isOk)
        assertNull(result.value)
    }

    @Test
    fun `extractClaim should return null for non-existent nested claim`() = runTest {
        val disclosedClaims = buildJsonObject {
            put("address", buildJsonObject {
                put("street", JsonPrimitive("123 Main St"))
            })
        }

        val result = resolver.extractClaim(
            credential = "",
            format = CredentialFormat.SD_JWT_DC,
            claimPath = listOf("address", "country"),
            disclosedClaims = disclosedClaims
        )

        assertTrue(result.isOk)
        assertNull(result.value)
    }

    @Test
    fun `extractClaims should extract multiple claims`() = runTest {
        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
            put("family_name", JsonPrimitive("Doe"))
            put("email", JsonPrimitive("john@example.com"))
        }

        val result = resolver.extractClaims(
            credential = "",
            format = CredentialFormat.SD_JWT_DC,
            claimPaths = listOf(
                listOf("given_name"),
                listOf("family_name")
            ),
            disclosedClaims = disclosedClaims
        )

        assertTrue(result.isOk)
        assertEquals(2, result.value.size)
        assertEquals(JsonPrimitive("John"), result.value["given_name"])
        assertEquals(JsonPrimitive("Doe"), result.value["family_name"])
    }

    @Test
    fun `extractClaims should skip non-existent claims`() = runTest {
        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
        }

        val result = resolver.extractClaims(
            credential = "",
            format = CredentialFormat.SD_JWT_DC,
            claimPaths = listOf(
                listOf("given_name"),
                listOf("email") // Does not exist
            ),
            disclosedClaims = disclosedClaims
        )

        assertTrue(result.isOk)
        assertEquals(1, result.value.size)
        assertEquals(JsonPrimitive("John"), result.value["given_name"])
        assertFalse(result.value.containsKey("email"))
    }

    @Test
    fun `extractAllClaims should extract all top-level claims`() = runTest {
        val disclosedClaims = buildJsonObject {
            put("given_name", JsonPrimitive("John"))
            put("family_name", JsonPrimitive("Doe"))
            put("email", JsonPrimitive("john@example.com"))
        }

        val result = resolver.extractAllClaims(
            credential = "",
            format = CredentialFormat.SD_JWT_DC,
            disclosedClaims = disclosedClaims
        )

        assertTrue(result.isOk)
        assertTrue(result.value.size >= 3)
        assertEquals(JsonPrimitive("John"), result.value["given_name"])
        assertEquals(JsonPrimitive("Doe"), result.value["family_name"])
        assertEquals(JsonPrimitive("john@example.com"), result.value["email"])
    }

    @Test
    fun `parseClaimPath should parse simple path`() {
        val path = SdJwtClaimResolver.parseClaimPath("given_name")
        assertEquals(1, path.size)
        assertEquals("given_name", path[0])
    }

    @Test
    fun `parseClaimPath should parse nested path`() {
        val path = SdJwtClaimResolver.parseClaimPath("address.street")
        assertEquals(2, path.size)
        assertEquals("address", path[0])
        assertEquals("street", path[1])
    }

    @Test
    fun `parseClaimPath should handle deeply nested path`() {
        val path = SdJwtClaimResolver.parseClaimPath("credential.subject.claims.name")
        assertEquals(4, path.size)
        assertEquals("credential", path[0])
        assertEquals("subject", path[1])
        assertEquals("claims", path[2])
        assertEquals("name", path[3])
    }
}
