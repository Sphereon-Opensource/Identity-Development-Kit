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

package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.sourceAs
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StrictCompactJwsTest {
    private companion object {
        const val HEADER = "eyJhbGciOiJFZERTQSIsInR5cCI6InZjK2p3dCJ9"
        const val PAYLOAD = "eyJAY29udGV4dCI6Imh0dHBzOi8vd3d3LnczLm9yZy9ucy9jcmVkZW50aWFscy92MiIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiXX0"
        const val SIGNATURE = "AQID"
    }

    @Test
    fun parsesHeaderPayloadSignatureAndPreservesOriginalSegments() {
        val compact = "$HEADER.$PAYLOAD.$SIGNATURE"
        val parsed = StrictCompactJws.parse(compact).getOrThrow()

        assertEquals("EdDSA", parsed.protectedHeader["alg"]?.jsonPrimitive?.content)
        assertEquals("vc+jwt", parsed.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("https://www.w3.org/ns/credentials/v2", parsed.payload["@context"]?.jsonPrimitive?.content)
        assertContentEquals(byteArrayOf(1, 2, 3), parsed.signature)
        assertEquals(compact, parsed.original.value)
        assertEquals(HEADER, parsed.protectedSegment)
        assertEquals(PAYLOAD, parsed.payloadSegment)
        assertEquals(SIGNATURE, parsed.signatureSegment)
    }

    @Test
    fun rejectsWrongSegmentCount() {
        listOf("$HEADER.$PAYLOAD", "$HEADER.$PAYLOAD.$SIGNATURE.extra").forEach { compact ->
            val result = StrictCompactJws.parse(compact)
            assertTrue(result.isErr)
            assertEquals("JWS_COMPACT_INVALID_SEGMENT_COUNT", result.error.code)
            assertIs<StrictCompactJwsError.InvalidSegmentCount>(result.error.sourceAs())
        }
    }

    @Test
    fun rejectsEmptySegmentsIncludingUnsignedCompactJws() {
        listOf(".$PAYLOAD.$SIGNATURE", "$HEADER..$SIGNATURE", "$HEADER.$PAYLOAD.").forEach { compact ->
            val result = StrictCompactJws.parse(compact)
            assertTrue(result.isErr)
            assertEquals("JWS_COMPACT_EMPTY_SEGMENT", result.error.code)
            assertIs<StrictCompactJwsError.EmptySegment>(result.error.sourceAs())
        }
    }

    @Test
    fun rejectsPaddingWhitespaceAndNonUrlAlphabet() {
        listOf(
            "$HEADER=.$PAYLOAD.$SIGNATURE",
            "$HEADER .$PAYLOAD.$SIGNATURE",
            "$HEADER.${PAYLOAD}+.$SIGNATURE",
            "$HEADER.$PAYLOAD.${SIGNATURE}/",
        ).forEach { compact ->
            val result = StrictCompactJws.parse(compact)
            assertTrue(result.isErr)
            assertEquals("JWS_COMPACT_INVALID_BASE64URL", result.error.code)
            assertIs<StrictCompactJwsError.InvalidBase64Url>(result.error.sourceAs())
        }
    }

    @Test
    fun rejectsNonCanonicalBase64UrlLengthAndPadBits() {
        listOf(
            "$HEADER.$PAYLOAD.A",
            "$HEADER.$PAYLOAD.AR",
            "eyJ4IjoxfR.$PAYLOAD.$SIGNATURE",
            "$HEADER.${PAYLOAD.dropLast(1)}1.$SIGNATURE",
        ).forEach { compact ->
            val result = StrictCompactJws.parse(compact)
            assertTrue(result.isErr)
            assertEquals("JWS_COMPACT_INVALID_BASE64URL", result.error.code)
            assertIs<StrictCompactJwsError.InvalidBase64Url>(result.error.sourceAs())
        }
    }

    @Test
    fun rejectsNonObjectHeaderAndPayload() {
        listOf(
            "W10.$PAYLOAD.$SIGNATURE",
            "$HEADER.InRleHQi.$SIGNATURE",
            "$HEADER.ew.$SIGNATURE",
        ).forEach { compact ->
            val result = StrictCompactJws.parse(compact)
            assertTrue(result.isErr)
            assertEquals("JWS_COMPACT_INVALID_JSON_OBJECT", result.error.code)
            assertIs<StrictCompactJwsError.InvalidJsonObject>(result.error.sourceAs())
        }
    }

    @Test
    fun exposesTypedErrorWithoutTokenContents() {
        val error = StrictCompactJws.parse("bad").error
        assertIs<StrictCompactJwsError.InvalidSegmentCount>(error.sourceAs())
        assertFalse(error.meta.values.any { it.toString().contains("bad") })
    }

    @Test
    fun typedErrorsAreValidationErrorsWithErrorSeverity() {
        listOf(
            StrictCompactJws.parse("bad").error,
            StrictCompactJws.parse(".$PAYLOAD.$SIGNATURE").error,
            StrictCompactJws.parse("$HEADER.$PAYLOAD.A").error,
            StrictCompactJws.parse("$HEADER.InRleHQi.$SIGNATURE").error,
        ).forEach { error ->
            assertEquals(ErrorCategory.VALIDATION, error.category)
            assertEquals(com.sphereon.core.api.error.IdkError.Severity.ERROR, error.severity)
        }
    }

    @Test
    fun malformedEncodingIsContainedAsAnInvalidBase64UrlError() {
        val result = runCatching { StrictCompactJws.parse("$HEADER.$PAYLOAD.A") }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isErr)
        assertEquals("JWS_COMPACT_INVALID_BASE64URL", result.getOrThrow().error.code)
    }
}
