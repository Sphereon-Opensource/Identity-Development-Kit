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
 *
 */

package com.sphereon.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HexEncodingTest {
    @Test
    fun encodeToHexWorks() {
        val bytes = byteArrayOf(0x12, 0x34, 0xAB.toByte(), 0xCD.toByte())
        assertEquals("1234abcd", bytes.encodeToHex())
    }

    @Test
    fun encodeToHexEmptyArrayReturnsEmpty() {
        assertEquals("", byteArrayOf().encodeToHex())
    }

    @Test
    fun decodeFromHexWorks() {
        val result = "1234abcd".decodeFromHex()
        assertEquals(4, result.size)
        assertEquals(0x12.toByte(), result[0])
        assertEquals(0x34.toByte(), result[1])
        assertEquals(0xAB.toByte(), result[2])
        assertEquals(0xCD.toByte(), result[3])
    }

    @Test
    fun decodeFromHexEmptyStringReturnsEmpty() {
        assertEquals(0, "".decodeFromHex().size)
    }

    @Test
    fun hexRoundTrips() {
        val original = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val encoded = original.encodeToHex()
        val decoded = encoded.decodeFromHex()
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun hexUppercaseDecodes() {
        val result = "ABCD".decodeFromHex()
        assertEquals(0xAB.toByte(), result[0])
        assertEquals(0xCD.toByte(), result[1])
    }
}

class Base64EncodingTest {
    @Test
    fun encodeToBase64Works() {
        val bytes = "Hello".encodeToByteArray()
        assertEquals("SGVsbG8=", bytes.encodeToBase64())
    }

    @Test
    fun encodeToBase64EmptyReturnsEmpty() {
        assertEquals("", byteArrayOf().encodeToBase64())
    }

    @Test
    fun decodeFromBase64Works() {
        val result = "SGVsbG8=".decodeFromBase64()
        assertEquals("Hello", result.decodeToString())
    }

    @Test
    fun decodeFromBase64EmptyReturnsEmpty() {
        assertEquals(0, "".decodeFromBase64().size)
    }

    @Test
    fun base64RoundTrips() {
        val original = "Hello, World! This is a test of Base64 encoding.".encodeToByteArray()
        val encoded = original.encodeToBase64()
        val decoded = encoded.decodeFromBase64()
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun decodeFromBase64WithoutPaddingWorks() {
        val result = "SGVsbG8".decodeFromBase64()
        assertEquals("Hello", result.decodeToString())
    }

    @Test
    fun encodeToBase64UrlSafeWorks() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte())
        val encoded = bytes.encodeToBase64(urlSafe = true)
        assertTrue(!encoded.contains('+'))
        assertTrue(!encoded.contains('/'))
    }
}

class Base64UrlEncodingTest {
    @Test
    fun encodeToBase64UrlWorks() {
        val bytes = "Hello".encodeToByteArray()
        val encoded = bytes.encodeToBase64Url()
        assertEquals("SGVsbG8", encoded)
        assertTrue(!encoded.endsWith("="))
    }

    @Test
    fun decodeFromBase64UrlWorks() {
        val result = "SGVsbG8".decodeFromBase64Url()
        assertEquals("Hello", result.decodeToString())
    }

    @Test
    fun base64UrlRoundTrips() {
        val original = "Test data with special chars: +/=".encodeToByteArray()
        val encoded = original.encodeToBase64Url()
        val decoded = encoded.decodeFromBase64Url()
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun base64UrlHasNoSpecialChars() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFC.toByte())
        val encoded = bytes.encodeToBase64Url()
        assertTrue(!encoded.contains('+'))
        assertTrue(!encoded.contains('/'))
        assertTrue(!encoded.contains('='))
    }
}

class Base58BtcEncodingTest {
    @Test
    fun encodeToBase58BtcWorks() {
        val bytes = "Hello".encodeToByteArray()
        val encoded = bytes.encodeToBase58Btc()
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun encodeToBase58BtcEmptyReturnsEmpty() {
        assertEquals("", byteArrayOf().encodeToBase58Btc())
    }

    @Test
    fun decodeFromBase58BtcWorks() {
        val encoded = "Hello".encodeToByteArray().encodeToBase58Btc()
        val decoded = encoded.decodeFromBase58Btc()
        assertEquals("Hello", decoded.decodeToString())
    }

    @Test
    fun decodeFromBase58BtcEmptyReturnsEmpty() {
        assertEquals(0, "".decodeFromBase58Btc().size)
    }

    @Test
    fun base58BtcRoundTrips() {
        val original = "The quick brown fox jumps over the lazy dog".encodeToByteArray()
        val encoded = original.encodeToBase58Btc()
        val decoded = encoded.decodeFromBase58Btc()
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun base58BtcLeadingZerosPreserved() {
        val original = byteArrayOf(0, 0, 0, 1, 2, 3)
        val encoded = original.encodeToBase58Btc()
        val decoded = encoded.decodeFromBase58Btc()
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun decodeFromBase58BtcThrowsForInvalidChar() {
        assertFailsWith<IllegalArgumentException> {
            "InvalidChar0".decodeFromBase58Btc()
        }
    }

    @Test
    fun base58BtcExcludesAmbiguousChars() {
        val testBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val encoded = testBytes.encodeToBase58Btc()
        assertTrue(!encoded.contains('0'))
        assertTrue(!encoded.contains('O'))
        assertTrue(!encoded.contains('I'))
        assertTrue(!encoded.contains('l'))
    }
}

class DecodeFromEncodingTest {
    @Test
    fun decodeFromBase64Works() {
        val result = "SGVsbG8=".decodeFrom(Encoding.BASE64)
        assertEquals("Hello", result.decodeToString())
    }

    @Test
    fun decodeFromBase64UrlWorks() {
        val result = "SGVsbG8".decodeFrom(Encoding.BASE64URL)
        assertEquals("Hello", result.decodeToString())
    }

    @Test
    fun decodeFromBase58BtcWorks() {
        val bytes = "Test".encodeToByteArray()
        val encoded = bytes.encodeToBase58Btc()
        val decoded = encoded.decodeFrom(Encoding.BASE58BTC)
        assertEquals("Test", decoded.decodeToString())
    }

    @Test
    fun decodeFromHexWorks() {
        val result = "48656c6c6f".decodeFrom(Encoding.HEX)
        assertEquals("Hello", result.decodeToString())
    }

    @Test
    fun decodeFromUtf8Works() {
        val result = "Hello".decodeFrom(Encoding.UTF8)
        assertEquals("Hello", result.decodeToString())
    }
}

class EncodeToEncodingTest {
    @Test
    fun encodeToBase64Works() {
        val bytes = "Hello".encodeToByteArray()
        assertEquals("SGVsbG8=", bytes.encodeTo(Encoding.BASE64))
    }

    @Test
    fun encodeToBase64UrlWorks() {
        val bytes = "Hello".encodeToByteArray()
        assertEquals("SGVsbG8", bytes.encodeTo(Encoding.BASE64URL))
    }

    @Test
    fun encodeToBase58BtcWorks() {
        val bytes = "Test".encodeToByteArray()
        val encoded = bytes.encodeTo(Encoding.BASE58BTC)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun encodeToHexWorks() {
        val bytes = "Hello".encodeToByteArray()
        assertEquals("48656c6c6f", bytes.encodeTo(Encoding.HEX))
    }

    @Test
    fun encodeToUtf8Works() {
        val bytes = "Hello".encodeToByteArray()
        assertEquals("Hello", bytes.encodeTo(Encoding.UTF8))
    }
}

class UrlEncodingTest {
    @Test
    fun encodeUrlComponentLeavesUnreservedCharacters() {
        assertEquals("AZaz09-_.~", "AZaz09-_.~".encodeUrlComponent())
    }

    @Test
    fun encodeUrlComponentUsesUtf8PercentEncoding() {
        assertEquals("%C3%A9%20%F0%9F%9A%80", "\u00E9 \uD83D\uDE80".encodeUrlComponent())
    }

    @Test
    fun encodeUrlComponentEncodesPathAndQueryDelimiters() {
        assertEquals("a%2Fb%3Fc%3Dd%26e", "a/b?c=d&e".encodeUrlComponent())
    }

    @Test
    fun encodeUrlGraphEncodesSpace() {
        assertEquals("Hello%20World", "Hello World".encodeUrlGraph())
    }

    @Test
    fun encodeUrlGraphEncodesSpecialChars() {
        assertTrue("Hello!".encodeUrlGraph().contains("%21"))
        assertTrue("a@b".encodeUrlGraph().contains("%40"))
        assertTrue("a#b".encodeUrlGraph().contains("%23"))
    }

    @Test
    fun encodeUrlGraphEncodesPercentFirst() {
        val result = "100%".encodeUrlGraph()
        assertTrue(result.contains("%25"))
        assertTrue(!result.contains("%%"))
    }

    @Test
    fun decodeUrlGraphDecodesSpace() {
        assertEquals("Hello World", "Hello%20World".decodeUrlGraph())
    }

    @Test
    fun decodeUrlGraphDecodesPlusAsSpace() {
        assertEquals("Hello World", "Hello+World".decodeUrlGraph())
    }

    @Test
    fun decodeUrlGraphDecodesSpecialChars() {
        assertEquals("Hello!", "Hello%21".decodeUrlGraph())
        assertEquals("a@b", "a%40b".decodeUrlGraph())
        assertEquals("a#b", "a%23b".decodeUrlGraph())
    }

    @Test
    fun urlEncodingRoundTrips() {
        val original = "Hello World! Test @#\$&*()"
        val encoded = original.encodeUrlGraph()
        val decoded = encoded.decodeUrlGraph()
        assertEquals(original, decoded)
    }
}

class EncodingEnumTest {
    @Test
    fun encodingEnumHasAllValues() {
        assertEquals(5, Encoding.entries.size)
        assertTrue(Encoding.entries.contains(Encoding.BASE64))
        assertTrue(Encoding.entries.contains(Encoding.BASE64URL))
        assertTrue(Encoding.entries.contains(Encoding.BASE58BTC))
        assertTrue(Encoding.entries.contains(Encoding.HEX))
        assertTrue(Encoding.entries.contains(Encoding.UTF8))
    }
}

// ========== Serializer Tests ==========

class Base64SerializerTest {
    @Test
    fun descriptorHasCorrectName() {
        assertEquals("Base64", Base64Serializer.descriptor.serialName)
    }

    @Test
    fun serializeRoundTrips() {
        val original = "Hello, World!".encodeToByteArray()
        val json =
            kotlinx.serialization.json.Json
                .encodeToString(Base64Serializer, original)
        val decoded =
            kotlinx.serialization.json.Json
                .decodeFromString(Base64Serializer, json)
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun serializeProducesBase64() {
        val bytes = "Test".encodeToByteArray()
        val json =
            kotlinx.serialization.json.Json
                .encodeToString(Base64Serializer, bytes)
        assertTrue(json.contains("VGVzdA=="))
    }
}

class Base64UrlSerializerTest {
    @Test
    fun descriptorHasCorrectName() {
        assertEquals("Base64Url", Base64UrlSerializer.descriptor.serialName)
    }

    @Test
    fun serializeRoundTrips() {
        val original = "Hello, World!".encodeToByteArray()
        val json =
            kotlinx.serialization.json.Json
                .encodeToString(Base64UrlSerializer, original)
        val decoded =
            kotlinx.serialization.json.Json
                .decodeFromString(Base64UrlSerializer, json)
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun serializeUsesUrlSafeAlphabet() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte())
        val json =
            kotlinx.serialization.json.Json
                .encodeToString(Base64UrlSerializer, bytes)
        assertTrue(!json.contains('+'))
        assertTrue(!json.contains('/'))
    }
}

class Base58BtcSerializerTest {
    @Test
    fun descriptorHasCorrectName() {
        assertEquals("Base58Btc", Base58BtcSerializer.descriptor.serialName)
    }

    @Test
    fun serializeRoundTrips() {
        val original = "Hello, World!".encodeToByteArray()
        val json =
            kotlinx.serialization.json.Json
                .encodeToString(Base58BtcSerializer, original)
        val decoded =
            kotlinx.serialization.json.Json
                .decodeFromString(Base58BtcSerializer, json)
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun serializeProducesBase58Btc() {
        val bytes = "Test".encodeToByteArray()
        val json =
            kotlinx.serialization.json.Json
                .encodeToString(Base58BtcSerializer, bytes)
        // Base58Btc encoding of "Test"
        assertTrue(json.isNotEmpty())
        assertTrue(!json.contains('0'))
        assertTrue(!json.contains('O'))
        assertTrue(!json.contains('I'))
        assertTrue(!json.contains('l'))
    }
}

class InstantIso8601SerializerTest {
    @Test
    fun descriptorHasCorrectName() {
        assertEquals("kotlinx.datetime.Instant", InstantIso8601Serializer.descriptor.serialName)
    }

    @Test
    fun serializeRoundTrips() {
        val original = kotlin.time.Instant.parse("2024-01-15T10:30:00Z")
        val json =
            kotlinx.serialization.json.Json
                .encodeToString(InstantIso8601Serializer, original)
        val decoded =
            kotlinx.serialization.json.Json
                .decodeFromString(InstantIso8601Serializer, json)
        assertEquals(original, decoded)
    }

    @Test
    fun serializeProducesIso8601() {
        val instant = kotlin.time.Instant.parse("2024-01-15T10:30:00Z")
        val json =
            kotlinx.serialization.json.Json
                .encodeToString(InstantIso8601Serializer, instant)
        assertTrue(json.contains("2024-01-15"))
        assertTrue(json.contains("10:30:00"))
    }
}

class Base64EdgeCasesTest {
    @Test
    fun base64SingleByteRoundTrips() {
        val original = byteArrayOf(0x42)
        val encoded = original.encodeToBase64()
        val decoded = encoded.decodeFromBase64()
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun base64TwoBytesRoundTrips() {
        val original = byteArrayOf(0x42, 0x43)
        val encoded = original.encodeToBase64()
        val decoded = encoded.decodeFromBase64()
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun base64ThreeBytesRoundTrips() {
        val original = byteArrayOf(0x42, 0x43, 0x44)
        val encoded = original.encodeToBase64()
        val decoded = encoded.decodeFromBase64()
        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun base64AllByteValuesRoundTrip() {
        val original = ByteArray(256) { it.toByte() }
        val encoded = original.encodeToBase64()
        val decoded = encoded.decodeFromBase64()
        assertEquals(original.toList(), decoded.toList())
    }
}
