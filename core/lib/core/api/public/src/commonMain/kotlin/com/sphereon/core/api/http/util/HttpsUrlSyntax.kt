/*
 * (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0
 */
package com.sphereon.core.api.http.util

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import io.ktor.http.URLDecodeException
import io.ktor.http.URLParserException
import io.ktor.http.Url

/**
 * Pure HTTPS syntax validation, retaining the original spelling on success.
 * Raw RFC 3986 component checks supplement Ktor parsing; they do not grant network permission.
 */
fun validateHttpsUrlSyntax(value: String, allowQuery: Boolean = true): IdkResult<String, IdkError> {
    if (!value.startsWith("https://", ignoreCase = true) || '#' in value || (!allowQuery && '?' in value) ||
        value.any { it.code !in 0x21..0x7e || it == '\\' }
    ) return invalidHttpsUrl()
    val afterScheme = value.substring(8)
    val authority = afterScheme.takeWhile { it != '/' && it != '?' }
    if (!validRawAuthority(authority)) return invalidHttpsUrl()
    val pathAndQuery = afterScheme.substring(authority.length)
    val queryStart = pathAndQuery.indexOf('?')
    val path = if (queryStart < 0) pathAndQuery else pathAndQuery.substring(0, queryStart)
    if (!validRawComponent(path) { isPchar(it) || it == '/' }) return invalidHttpsUrl()
    if (queryStart >= 0 && !validRawComponent(pathAndQuery.substring(queryStart + 1)) { isPchar(it) || it == '/' || it == '?' }) {
        return invalidHttpsUrl()
    }
    return try {
        val url = Url(value)
        if (url.protocol.name.equals("https", ignoreCase = true) && url.host.isNotBlank() && url.user == null && url.password == null) {
            Ok(value)
        } else invalidHttpsUrl()
    } catch (_: URLParserException) {
        invalidHttpsUrl()
    } catch (_: URLDecodeException) {
        invalidHttpsUrl()
    } catch (_: IllegalArgumentException) {
        invalidHttpsUrl()
    }
}

private fun validRawAuthority(authority: String): Boolean {
    if (authority.isEmpty() || '@' in authority) return false
    if (authority.startsWith('[')) {
        val close = authority.indexOf(']')
        if (close <= 1) return false
        val literal = authority.substring(1, close)
        if (!validIpv6Literal(literal) && !validIpvFutureLiteral(literal)) return false
        val suffix = authority.substring(close + 1)
        return suffix.isEmpty() || (suffix.startsWith(':') && validRawPort(suffix.substring(1)))
    }
    val colon = authority.indexOf(':')
    if (colon != authority.lastIndexOf(':')) return false
    val host = if (colon < 0) authority else authority.substring(0, colon)
    if (host.isEmpty() || !validRawComponent(host) { isUnreserved(it) || isSubDelimiter(it) }) return false
    return colon < 0 || validRawPort(authority.substring(colon + 1))
}

private fun validRawPort(port: String): Boolean =
    port.isEmpty() || (port.all { it in '0'..'9' } && port.toIntOrNull()?.let { it in 0..65535 } == true)

/** Validate percent-triplet spelling, without decoding or returning a normalized component. */
private fun validRawComponent(value: String, allowed: (Char) -> Boolean): Boolean {
    var index = 0
    while (index < value.length) {
        if (value[index] == '%') {
            if (index + 2 >= value.length || !isAsciiHex(value[index + 1]) || !isAsciiHex(value[index + 2])) return false
            index += 3
        } else {
            if (!allowed(value[index])) return false
            index++
        }
    }
    return true
}

private fun isUnreserved(char: Char): Boolean =
    char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in "-._~"

private fun isSubDelimiter(char: Char): Boolean = char in "!$&'()*+,;="

private fun isPchar(char: Char): Boolean = isUnreserved(char) || isSubDelimiter(char) || char == ':' || char == '@'

private fun isAsciiHex(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

private fun validIpvFutureLiteral(value: String): Boolean {
    if (value.firstOrNull() != 'v' && value.firstOrNull() != 'V') return false
    val dot = value.indexOf('.')
    if (dot <= 1 || dot == value.lastIndex || !value.substring(1, dot).all(::isAsciiHex)) return false
    return value.substring(dot + 1).all { isUnreserved(it) || isSubDelimiter(it) || it == ':' }
}

/** Recognize bracket-literal grammar only, without address conversion or egress classification. */
private fun validIpv6Literal(value: String): Boolean {
    val compression = value.indexOf("::")
    val components = if (compression >= 0) {
        if (value.indexOf("::", compression + 2) >= 0) return false
        val left = value.substring(0, compression)
        val right = value.substring(compression + 2)
        (if (left.isEmpty()) emptyList() else left.split(':')) +
            (if (right.isEmpty()) emptyList() else right.split(':'))
    } else {
        value.split(':')
    }
    var groups = 0
    for ((index, component) in components.withIndex()) {
        if ('.' in component) {
            // IPv4 may supply only the final ls32, never precede trailing compression.
            if (index != components.lastIndex || !value.endsWith(component) || !validEmbeddedIpv4(component)) return false
            groups += 2
        } else {
            if (component.length !in 1..4 || !component.all(::isAsciiHex)) return false
            groups++
        }
    }
    // Compression must stand for at least one group; uncompressed literals contain exactly eight.
    return if (compression >= 0) groups < 8 else groups == 8
}

private fun validEmbeddedIpv4(value: String): Boolean {
    val octets = value.split('.')
    if (octets.size != 4) return false
    return octets.all { octet ->
        octet.length in 1..3 && octet.all { it in '0'..'9' } &&
            (octet.length == 1 || !octet.startsWith('0')) &&
            octet.toIntOrNull()?.let { it in 0..255 } == true
    }
}

private fun invalidHttpsUrl(): IdkResult<String, IdkError> = Err(
    IdkError.ILLEGAL_ARGUMENT_ERROR(arg = "url", message = "url must be an absolute HTTPS URL with valid syntax and no fragment"),
)
