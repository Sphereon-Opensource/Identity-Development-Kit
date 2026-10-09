/*
 * (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */
package com.sphereon.core.api.http.util

import com.sphereon.core.api.error.ErrorCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpsUrlSyntaxTest {
    @Test
    fun exactCasePercentSpellingAndPcharSubdelimitersArePreserved() {
        for (value in listOf(
            "HTTPS://ExAmPlE.test/Tenant/Case%2FSensitive/",
            "https://ex%61mple.test/Tenant%5CName",
            "https://example.test/!$&'()*+,;=:@~_-./Case",
            "https://registered!$&'()*+,;=.test/Case",
        )) assertAcceptedExactly(value)
    }

    @Test
    fun endpointQueriesAndEmptyQueryArePreservedButIdentifierModeRejectsThem() {
        for (value in listOf(
            "https://example.test/Path?wallet=A%2FB&next=/one?two",
            "https://example.test/Path?", "https://example.test?mode=one",
        )) {
            assertAcceptedExactly(value)
            assertRejected(value, allowQuery = false)
        }
        assertAcceptedExactly("https://example.test/Path", allowQuery = false)
    }

    @Test
    fun zeroEmptyDefaultAndZeroPaddedPortsRemainLiteralSyntaxControls() {
        for (value in listOf(
            "https://example.test:0/Tenant", "https://example.test:/Tenant",
            "https://example.test:443/Tenant", "https://example.test:000443/Tenant",
            "https://example.test:8443/Tenant", "https://example.test:65535/Tenant",
        )) assertAcceptedExactly(value, allowQuery = false)
    }

    @Test
    fun fullCompressedEmbeddedIpv4AndUppercaseIpvFutureLiteralsAreAcceptedExactly() {
        for (value in listOf(
            "https://[2001:0db8:0000:0000:0000:0000:0000:0001]/Tenant",
            "https://[2001:db8::1]:8443/Tenant", "https://[::]/Tenant",
            "https://[::ffff:192.0.2.128]/Tenant", "https://[2001:db8:0:0:0:0:192.0.2.128]/Tenant",
            "https://[1:2:3:4:5:6:7::]/Tenant", "https://[::1:2:3:4:5:6:7]/Tenant",
            "https://[1:2:3:4:5::192.0.2.1]/Tenant",
            "https://[v1.future:address]/Tenant", "https://[Vf.Future!$&'()*+,;=:Address]:0/Tenant",
        )) assertAcceptedExactly(value)
    }

    @Test
    fun localAndUnusualRegisteredNamesAreSyntaxNotNetworkPermission() {
        for (value in listOf(
            "https://localhost/Tenant", "https://127.0.0.1/Tenant",
            "https://192.000.2.1/Tenant", "https://999.999.999.999/Tenant",
        )) assertAcceptedExactly(value)
    }

    @Test
    fun schemeMissingAuthorityPortOnlyUserinfoWhitespaceBackslashAndFragmentsReject() {
        for (value in listOf(
            "", "http://example.test/Tenant", "/Tenant", "//example.test/Tenant",
            "https://", "https:///Tenant", "https://:8443/entity",
            "https://user:password@example.test/Tenant", " https://example.test/Tenant",
            "https://example.test/Tenant\t", "https://example.test/Tenant\u0085",
            "https://example.test/Tenant\\Other", "https://example.test/Tenant#", "https://example.test/Tenant#fragment",
        )) assertRejected(value)
    }

    @Test
    fun signedIntegerPortsAreNotDecimalPortGrammar() {
        for (value in listOf("https://example.test:+443/Tenant", "https://example.test:-0/Tenant")) assertRejected(value)
    }

    @Test
    fun portDigitsMustBeAsciiAndOverflowIsControlled() {
        for (value in listOf(
            "https://example.test:٤٤٣/Tenant", "https://example.test:４４３/Tenant",
            "https://example.test:1e3/Tenant", "https://example.test:65536/Tenant",
            "https://example.test:999999999999999999999999999999/Tenant",
        )) assertRejected(value)
    }

    @Test
    fun illegalRawRegisteredNameCharactersReject() {
        for (value in listOf(
            "https://bad|host.test/Tenant", "https://bad\"host.test/Tenant",
            "https://bad{host}.test/Tenant", "https://bad^host.test/Tenant",
            "https://bad`host.test/Tenant", "https://bad[host].test/Tenant",
        )) assertRejected(value)
    }

    @Test
    fun rawHostPathAndQueryAreAsciiOnlyWithoutImplicitIdnaConversion() {
        for (value in listOf(
            "https://éxample.test/Tenant", "https://example.test/Ténant", "https://example.test/Tenant?name=é",
        )) assertRejected(value)
    }

    @Test
    fun malformedHostPercentTripletsReject() {
        for (value in listOf("https://ex%ample.test/Tenant", "https://ex%G0ample.test/Tenant", "https://ex%ＦFample.test/Tenant")) assertRejected(value)
    }

    @Test
    fun malformedPathPercentTripletsReject() {
        for (value in listOf("https://example.test/%", "https://example.test/%G0", "https://example.test/%ＦF")) assertRejected(value)
    }

    @Test
    fun malformedQueryPercentTripletsReject() {
        for (value in listOf("https://example.test/Tenant?x=%", "https://example.test/Tenant?x=%G0", "https://example.test/Tenant?x=%ＦF")) assertRejected(value)
    }

    @Test
    fun rawPathAndQueryCharactersFollowDifferentGrammar() {
        for (value in listOf(
            "https://example.test/Tenant|Other", "https://example.test/Tenant[Other]",
            "https://example.test/Tenant?name={value}", "https://example.test/Tenant?name=one|two",
        )) assertRejected(value)
        assertAcceptedExactly("https://example.test/Tenant:Case@A?next=/one?two&encoded=%7Bvalue%7D")
    }

    @Test
    fun malformedBracketsUnbracketedIpv6AndIpv6GroupCountsReject() {
        for (value in listOf(
            "https://[2001:db8::1/Tenant", "https://[2001:db8::1]extra/Tenant", "https://2001:db8::1/Tenant",
            "https://[1::2::3]/Tenant", "https://[1:2:3:4:5:6:7]/Tenant", "https://[1:2:3:4:5:6:7:8:9]/Tenant",
            "https://[12345::1]/Tenant", "https://[1:2:3:4:5:6:7:8::]/Tenant", "https://[fe80::1%25eth0]/Tenant",
        )) assertRejected(value)
    }

    @Test
    fun embeddedIpv4RequiresDecimalOctetsWithoutLeadingZeros() {
        for (value in listOf(
            "https://[::ffff:192.00.2.128]/Tenant", "https://[::ffff:256.0.2.128]/Tenant",
            "https://[::ffff:192.0.2]/Tenant", "https://[::ffff:١٩٢.0.2.128]/Tenant",
            "https://[192.0.2.1::]/Tenant", "https://[1:2:3:4:5:192.0.2.1::]/Tenant",
            "https://[::192.0.2.1:1]/Tenant",
        )) assertRejected(value)
    }

    @Test
    fun ipvFutureVersionIsAsciiHexAndAddressIsNonemptyAllowedGrammar() {
        for (value in listOf(
            "https://[vG.address]/Tenant", "https://[v1.]/Tenant", "https://[v1.address|bad]/Tenant",
            "https://[VＦ.address]/Tenant", "https://[v1.address%41]/Tenant",
        )) assertRejected(value)
    }

    private fun assertAcceptedExactly(value: String, allowQuery: Boolean = true) {
        val result = validateHttpsUrlSyntax(value, allowQuery)
        assertTrue(result.isOk, value)
        assertEquals(value, result.value)
    }

    private fun assertRejected(value: String, allowQuery: Boolean = true) {
        val result = validateHttpsUrlSyntax(value, allowQuery)
        assertTrue(result.isErr, value)
        assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
        assertEquals(ErrorCategory.VALIDATION, result.error.category)
        assertEquals("com.sphereon.core.error.illegal-argument-error", result.error.message.i18nKey)
        assertTrue("url" in result.error.message.defaultMessage)
    }
}
