/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.oauth2.common.model

import com.sphereon.core.idn.Idna
import kotlinx.serialization.Serializable

/**
 * Canonical issuer URI shared by authorization-server resources and JWT validation.
 *
 * The value is deliberately narrower than a general URL: issuer identity has no
 * query or fragment, userinfo, encoded authority/path, dot segments, or
 * ambiguous repeated slashes. Canonicalization is deterministic so resource
 * uniqueness and JWT `iss` matching use the same representation without making
 * JWT validation depend on the authorization-server implementation module.
 */
@Serializable
@JvmInline
value class CanonicalAuthorizationServerIssuer private constructor(val value: String) {
    companion object {
        /** Parse and canonicalize an issuer URI. */
        fun parse(
            raw: String,
            allowHostedLocalDevelopmentHttp: Boolean = false,
            external: Boolean = false,
        ): CanonicalAuthorizationServerIssuer =
            CanonicalAuthorizationServerIssuer(
                canonicalize(raw, allowHostedLocalDevelopmentHttp, external),
            )

        /** Return the canonical issuer string or throw for an invalid issuer. */
        fun canonicalize(
            raw: String,
            allowHostedLocalDevelopmentHttp: Boolean = false,
            external: Boolean = false,
        ): String {
            require(raw.isNotEmpty() && raw == raw.trim()) { "issuer must not be blank or padded" }
            require('%' !in raw && '@' !in raw && '?' !in raw && '#' !in raw) {
                "issuer must not contain encoded material, userinfo, query, or fragment"
            }
            val separator = raw.indexOf("://")
            require(separator > 0 && raw.indexOf("://", separator + 3) < 0) { "issuer must use one URI authority" }
            val scheme = raw.substring(0, separator).lowercase()
            require(scheme == "https" || scheme == "http") { "issuer scheme must be http or https" }

            val remainder = raw.substring(separator + 3)
            val slash = remainder.indexOf('/')
            val authority = if (slash < 0) remainder else remainder.substring(0, slash)
            val path = if (slash < 0) "" else remainder.substring(slash)
            require(authority.isNotEmpty()) { "issuer host must not be empty" }
            require('\\' !in authority && '\\' !in path) { "issuer must not contain backslashes" }
            require("//" !in path) { "issuer path must not contain repeated slashes" }
            require(path.split('/').none { it == "." || it == ".." }) { "issuer path must not contain dot segments" }

            val parsedAuthority = parseAuthority(authority)
            val host = parsedAuthority.host
            require(host.isNotEmpty()) { "issuer host must not be empty" }
            val loopback = isLoopback(host)
            require(scheme == "https" || (!external && allowHostedLocalDevelopmentHttp && loopback)) {
                "http issuer is allowed only for explicitly enabled hosted loopback development"
            }

            // The root path is canonicalized to the authority without a trailing slash as well;
            // this keeps `https://issuer.example` and `https://issuer.example/` equivalent.
            val canonicalPath = if (path.endsWith('/')) path.dropLast(1) else path
            val defaultPort = (scheme == "https" && parsedAuthority.port == 443) ||
                (scheme == "http" && parsedAuthority.port == 80)
            val port = parsedAuthority.port?.takeUnless { defaultPort }?.let { ":$it" }.orEmpty()
            return "$scheme://${parsedAuthority.renderedHost}$port$canonicalPath"
        }

        /** Require an already canonical issuer string. */
        fun validate(
            value: String,
            allowHostedLocalDevelopmentHttp: Boolean = false,
            external: Boolean = false,
        ) {
            require(value == canonicalize(value, allowHostedLocalDevelopmentHttp, external)) {
                "issuer is not canonical"
            }
        }

        private fun parseAuthority(authority: String): Authority {
            if (authority.startsWith('[')) {
                val end = authority.indexOf(']')
                require(end > 1) { "invalid IPv6 issuer host" }
                val host = authority.substring(1, end).lowercase()
                val suffix = authority.substring(end + 1)
                require(host.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }) {
                    "invalid IPv6 issuer host"
                }
                val port = suffix.takeIf { it.isNotEmpty() }?.let(::parsePort)
                return Authority("[$host]", host, port)
            }
            require(authority.count { it == ':' } <= 1) { "issuer host must not contain an unbracketed IPv6 address" }
            val colon = authority.lastIndexOf(':')
            val rawHost = if (colon < 0) authority else authority.substring(0, colon)
            val port = if (colon < 0) null else parsePort(authority.substring(colon))
            require(rawHost.isNotEmpty()) { "issuer host must not be empty" }
            require(!rawHost.endsWith('.')) { "issuer host must not use a trailing dot" }
            val ascii = Idna.toAscii(rawHost)
            require(ascii.isOk || isIpv4(rawHost)) { "issuer host is not a valid IDNA name" }
            val renderedHost = if (isIpv4(rawHost)) rawHost else ascii.value.trimEnd('.')
            require(renderedHost.isNotEmpty()) { "issuer host must not be empty" }
            return Authority(renderedHost, renderedHost, port)
        }

        private fun parsePort(suffix: String): Int {
            require(suffix.startsWith(':')) { "invalid issuer authority suffix" }
            val raw = suffix.drop(1)
            require(raw.isNotEmpty() && raw.all(Char::isDigit)) { "issuer port must be numeric" }
            return raw.toIntOrNull()?.also { require(it in 1..65535) } ?: error("issuer port is invalid")
        }

        private fun isIpv4(value: String): Boolean {
            val labels = value.split('.')
            return labels.size == 4 && labels.all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }
        }

        private fun isLoopback(host: String): Boolean =
            host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]"

        private data class Authority(val renderedHost: String, val host: String, val port: Int?)
    }
}
