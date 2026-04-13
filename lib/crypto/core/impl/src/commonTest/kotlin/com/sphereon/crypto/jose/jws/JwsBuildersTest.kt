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

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class JwsBuildersTest {
    @Test
    fun testJwsHeaderBuilder() {
        // Using builder methods
        val header =
            JwsHeaderBuilder()
                .alg("ES256")
                .kid("key-123")
                .typ("JWT")
                .claim("custom_claim", "custom_value")
                .build()

        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("key-123", header["kid"]?.jsonPrimitive?.content)
        assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)
        assertEquals("custom_value", header["custom_claim"]?.jsonPrimitive?.content)
    }

    @Test
    fun testJwsHeaderBuilderDSL() {
        // Using DSL syntax
        val header =
            jwsHeader {
                alg("RS256")
                kid("my-key")
                typ("JWT")
                cty("application/json")
                claim("x-custom", "value")
            }

        assertEquals("RS256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("my-key", header["kid"]?.jsonPrimitive?.content)
        assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)
        assertEquals("application/json", header["cty"]?.jsonPrimitive?.content)
        assertEquals("value", header["x-custom"]?.jsonPrimitive?.content)
    }

    @Test
    fun testJwsHeaderBuilderCritical() {
        val header =
            jwsHeader {
                alg("PS256")
                crit("exp", "aud")
            }

        assertNotNull(header["crit"])
        // Critical is an array, just verify it exists
        assertEquals("PS256", header["alg"]?.jsonPrimitive?.content)
    }

    @Test
    fun testJwsPayloadBuilder() {
        val now = Clock.System.now().epochSeconds
        val payload =
            JwsPayloadBuilder()
                .iss("https://issuer.example.com")
                .sub("user-123")
                .aud("https://audience.example.com")
                .exp(now + 3600)
                .iat(now)
                .claim("email", "user@example.com")
                .claim("age", 25)
                .claim("verified", true)
                .build()

        assertEquals("https://issuer.example.com", payload["iss"]?.jsonPrimitive?.content)
        assertEquals("user-123", payload["sub"]?.jsonPrimitive?.content)
        assertEquals("https://audience.example.com", payload["aud"]?.jsonPrimitive?.content)
        assertEquals(now + 3600, payload["exp"]?.jsonPrimitive?.content?.toLong())
        assertEquals(now, payload["iat"]?.jsonPrimitive?.content?.toLong())
        assertEquals("user@example.com", payload["email"]?.jsonPrimitive?.content)
        assertEquals(25, payload["age"]?.jsonPrimitive?.content?.toInt())
        assertEquals(true, payload["verified"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun testJwsPayloadBuilderDSL() {
        val now = Clock.System.now().epochSeconds
        val payload =
            jwsPayload {
                iss("https://example.com")
                sub("user-456")
                aud("aud1", "aud2", "aud3")
                exp(now + 7200)
                nbf(now - 60)
                iat(now)
                jti("jwt-id-789")
                claim("custom", "data")
            }

        assertEquals("https://example.com", payload["iss"]?.jsonPrimitive?.content)
        assertEquals("user-456", payload["sub"]?.jsonPrimitive?.content)
        assertNotNull(payload["aud"]) // Array of audiences
        assertEquals(now + 7200, payload["exp"]?.jsonPrimitive?.content?.toLong())
        assertEquals(now - 60, payload["nbf"]?.jsonPrimitive?.content?.toLong())
        assertEquals(now, payload["iat"]?.jsonPrimitive?.content?.toLong())
        assertEquals("jwt-id-789", payload["jti"]?.jsonPrimitive?.content)
        assertEquals("data", payload["custom"]?.jsonPrimitive?.content)
    }

    @Test
    fun testJwsOptsBuilder() {
        val opts =
            JwsOptsBuilder()
                .noIssPayloadUpdate()
                .noIdentifierInHeader()
                .protectedHeader {
                    alg("ES256")
                    typ("JWT")
                }.unprotectedHeader {
                    claim("x-version", "1.0")
                }.build()

        assertTrue(opts.noIssPayloadUpdate)
        assertTrue(opts.noIdentifierInHeader)
        assertNotNull(opts.protectedHeader)
        assertNotNull(opts.unprotectedHeader)
        assertEquals("ES256", opts.protectedHeader!!["alg"]?.jsonPrimitive?.content)
        assertEquals("1.0", opts.unprotectedHeader!!["x-version"]?.jsonPrimitive?.content)
    }

    @Test
    fun testJwsOptsBuilderDSL() {
        val opts =
            jwsOptions {
                protectedHeader {
                    alg("RS256")
                    kid("key-abc")
                }
                unprotectedHeader {
                    claim("x-trace-id", "12345")
                }
            }

        assertNotNull(opts.protectedHeader)
        assertNotNull(opts.unprotectedHeader)
        assertEquals("RS256", opts.protectedHeader!!["alg"]?.jsonPrimitive?.content)
        assertEquals("key-abc", opts.protectedHeader!!["kid"]?.jsonPrimitive?.content)
        assertEquals("12345", opts.unprotectedHeader!!["x-trace-id"]?.jsonPrimitive?.content)
    }

    @Test
    fun testCreateJwsArgsBuilderWithBuilders() {
        // Note: issuer is null in this test since we're just testing the builder pattern
        val args =
            CreateJwsArgsBuilder()
                .payload {
                    iss("https://issuer.example.com")
                    sub("user-123")
                    claim("email", "user@example.com")
                }.mode(JwsIdentifierMode.KID)
                .options {
                    protectedHeader {
                        typ("JWT")
                        cty("application/json")
                    }
                }.build()

        assertNotNull(args.payload)
        assertEquals(JwsIdentifierMode.KID, args.mode)
        assertNotNull(args.opts.protectedHeader)
        assertEquals(
            "JWT",
            args.opts.protectedHeader!!["typ"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun testCreateJwsArgsBuilderDSL() {
        val args =
            createJwsArgs {
                // issuer would be set here in real usage
                payload {
                    iss("https://example.com")
                    sub("user-789")
                    claim("scope", "read write")
                }
                mode(JwsIdentifierMode.AUTO)
                options {
                    noIssPayloadUpdate()
                    protectedHeader {
                        alg("PS256")
                    }
                }
            }

        assertNotNull(args.payload)
        assertEquals(JwsIdentifierMode.AUTO, args.mode)
        assertTrue(args.opts.noIssPayloadUpdate)
        assertNotNull(args.opts.protectedHeader)
        assertEquals(
            "PS256",
            args.opts.protectedHeader!!["alg"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun testCreateJwsJsonArgsBuilder() {
        val args =
            CreateJwsJsonArgsBuilder()
                .payloadString("Hello, World!")
                .mode(JwsIdentifierMode.JWK)
                .options {
                    protectedHeader {
                        alg("ES384")
                    }
                }.build()

        assertEquals("Hello, World!", args.payload)
        assertEquals(JwsIdentifierMode.JWK, args.mode)
        assertNotNull(args.opts.protectedHeader)
        assertEquals(
            "ES384",
            args.opts.protectedHeader!!["alg"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun testCreateJwsJsonArgsBuilderDSL() {
        val args =
            createJwsJsonArgs {
                payloadBytes("Binary data".encodeToByteArray())
                mode(JwsIdentifierMode.X5C)
                options {
                    noIdentifierInHeader()
                    unprotectedHeader {
                        claim("x-metadata", "test")
                    }
                }
            }

        assertTrue(args.payload is ByteArray)
        assertEquals(JwsIdentifierMode.X5C, args.mode)
        assertTrue(args.opts.noIdentifierInHeader)
        assertNotNull(args.opts.unprotectedHeader)
        assertEquals(
            "test",
            args.opts.unprotectedHeader!!["x-metadata"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun testHeaderMerge() {
        val baseHeader =
            jwsHeader {
                alg("ES256")
                typ("JWT")
            }

        val extendedHeader =
            JwsHeaderBuilder
                .from(baseHeader)
                .kid("new-key")
                .claim("x-extra", "value")
                .build()

        // Base header should be unchanged
        assertEquals("ES256", baseHeader["alg"]?.jsonPrimitive?.content)
        assertEquals("JWT", baseHeader["typ"]?.jsonPrimitive?.content)
        assertEquals(null, baseHeader["kid"])

        // Extended header should have all fields
        assertEquals("ES256", extendedHeader["alg"]?.jsonPrimitive?.content)
        assertEquals("JWT", extendedHeader["typ"]?.jsonPrimitive?.content)
        assertEquals("new-key", extendedHeader["kid"]?.jsonPrimitive?.content)
        assertEquals("value", extendedHeader["x-extra"]?.jsonPrimitive?.content)
    }

    @Test
    fun testPayloadMerge() {
        val basePayload =
            jwsPayload {
                iss("https://issuer.example.com")
                sub("user-123")
            }

        val extendedPayload =
            JwsPayloadBuilder
                .from(basePayload)
                .claim("email", "user@example.com")
                .claim("role", "admin")
                .build()

        // Base payload should be unchanged
        assertEquals("https://issuer.example.com", basePayload["iss"]?.jsonPrimitive?.content)
        assertEquals("user-123", basePayload["sub"]?.jsonPrimitive?.content)
        assertEquals(null, basePayload["email"])

        // Extended payload should have all fields
        assertEquals("https://issuer.example.com", extendedPayload["iss"]?.jsonPrimitive?.content)
        assertEquals("user-123", extendedPayload["sub"]?.jsonPrimitive?.content)
        assertEquals("user@example.com", extendedPayload["email"]?.jsonPrimitive?.content)
        assertEquals("admin", extendedPayload["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun testBuilderCopy() {
        val original =
            JwsHeaderBuilder()
                .alg("ES256")
                .kid("original-key")

        val copy =
            original
                .copy()
                .claim("x-extra", "new-value")
                .build()

        val originalBuilt = original.build()

        // Original should not have the extra field
        assertEquals(null, originalBuilt["x-extra"])

        // Copy should have all fields
        assertEquals("ES256", copy["alg"]?.jsonPrimitive?.content)
        assertEquals("original-key", copy["kid"]?.jsonPrimitive?.content)
        assertEquals("new-value", copy["x-extra"]?.jsonPrimitive?.content)
    }

    @Test
    fun testHeaderBuilderWithPairs() {
        val header =
            jwsHeader {
                alg("RS256")
                claim(
                    "x-custom-1" to "value1",
                    "x-custom-2" to 42,
                    "x-custom-3" to true,
                )
            }

        assertEquals("RS256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("value1", header["x-custom-1"]?.jsonPrimitive?.content)
        assertEquals(42, header["x-custom-2"]?.jsonPrimitive?.content?.toInt())
        assertEquals(true, header["x-custom-3"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun testPayloadBuilderWithPairs() {
        val payload =
            jwsPayload {
                iss("https://example.com")
                sub("user-123")
                claim(
                    "email" to "user@example.com",
                    "age" to 25,
                    "verified" to true,
                    "score" to 98.5,
                )
            }

        assertEquals("https://example.com", payload["iss"]?.jsonPrimitive?.content)
        assertEquals("user-123", payload["sub"]?.jsonPrimitive?.content)
        assertEquals("user@example.com", payload["email"]?.jsonPrimitive?.content)
        assertEquals(25, payload["age"]?.jsonPrimitive?.content?.toInt())
        assertEquals(true, payload["verified"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(98.5, payload["score"]?.jsonPrimitive?.content?.toDouble())
    }
}
