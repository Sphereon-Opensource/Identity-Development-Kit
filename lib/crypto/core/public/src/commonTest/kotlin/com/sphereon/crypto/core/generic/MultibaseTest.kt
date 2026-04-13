package com.sphereon.crypto.core.generic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MultibaseTest {

    @Test
    fun base16RoundTrip() {
        val data = byteArrayOf(0x01, 0x02, 0xAB.toByte(), 0xFF.toByte())
        val encoded = Multibase.encode(data, MultibaseEncoding.BASE16)
        assertEquals('f', encoded[0], "Base16 prefix should be 'f'")
        val decoded = Multibase.decode(encoded)
        assertEquals(data.toList(), decoded.toList())
    }

    @Test
    fun base58BtcRoundTrip() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val encoded = Multibase.encode(data, MultibaseEncoding.BASE58BTC)
        assertEquals('z', encoded[0], "Base58btc prefix should be 'z'")
        val decoded = Multibase.decode(encoded)
        assertEquals(data.toList(), decoded.toList())
    }

    @Test
    fun base64UrlRoundTrip() {
        val data = "Hello, World!".encodeToByteArray()
        val encoded = Multibase.encode(data, MultibaseEncoding.BASE64URL)
        assertEquals('u', encoded[0], "Base64url prefix should be 'u'")
        val decoded = Multibase.decode(encoded)
        assertEquals(data.toList(), decoded.toList())
    }

    @Test
    fun detectEncoding() {
        assertEquals(MultibaseEncoding.BASE16, Multibase.getEncoding("f0102"))
        assertEquals(MultibaseEncoding.BASE58BTC, Multibase.getEncoding("z1234"))
        assertEquals(MultibaseEncoding.BASE64URL, Multibase.getEncoding("uSGVsbG8"))
    }

    @Test
    fun emptyInputFails() {
        assertFailsWith<IllegalArgumentException> { Multibase.decode("") }
    }

    @Test
    fun unknownPrefixFails() {
        assertFailsWith<IllegalArgumentException> { Multibase.decode("xSomething") }
    }

    @Test
    fun base58LeadingZeros() {
        val data = byteArrayOf(0, 0, 0, 1)
        val encoded = Multibase.encode(data, MultibaseEncoding.BASE58BTC)
        val decoded = Multibase.decode(encoded)
        assertEquals(data.toList(), decoded.toList())
    }
}
