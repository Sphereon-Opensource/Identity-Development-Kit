/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.ktor.http.client.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScope
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.AF_UNSPEC
import platform.posix.NI_MAXHOST
import platform.posix.NI_NUMERICHOST
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getnameinfo

/** True when a policy blocks any address range, which is when the iOS client must look at the addresses a host resolves to. */
fun UrlValidationPolicy.blocksAddressRanges(): Boolean = blockPrivateNetworks || blockRfc1918 || blockSharedNetworks

/**
 * Every address [host] resolves to, in numeric form, as the system resolver (getaddrinfo) returns them. Empty when the host
 * does not resolve. Throws [UrlValidationException] when an answer cannot be read as a numeric address.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun resolveHostAddresses(host: String): List<String> =
    memScope {
        val hints = alloc<addrinfo>()
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = SOCK_STREAM
        val head = alloc<CPointerVar<addrinfo>>()
        if (getaddrinfo(host, null, hints.ptr, head.ptr) != 0) return@memScope emptyList()
        val numeric = allocArray<ByteVar>(NI_MAXHOST)
        val addresses = mutableListOf<String>()
        try {
            var current = head.value
            while (current != null) {
                val entry = current.pointed
                // An answer that cannot be turned into a numeric address cannot be checked, so the host is refused.
                if (getnameinfo(entry.ai_addr, entry.ai_addrlen, numeric, NI_MAXHOST.convert(), null, 0u, NI_NUMERICHOST) != 0) {
                    throw UrlValidationException("The host resolves to an address that cannot be checked")
                }
                addresses += numeric.toKString()
                current = entry.ai_next
            }
        } finally {
            freeaddrinfo(head.value)
        }
        addresses
    }

/**
 * The strongest check the Darwin engine allows. Before every request, including each redirect hop, the host is resolved
 * with getaddrinfo and the request is refused if any address it resolves to is blocked by [policy].
 *
 * Residual gap: NSURLSession resolves the host again when it connects and offers no hook to hand it the validated
 * address, so a resolver that answers differently for the second lookup (DNS rebinding) is not caught. Connecting to the
 * validated address directly is not an option because TLS server name and certificate checks must keep using the host
 * name. The window is the time between this lookup and the connection, and the system resolver cache usually returns the
 * same answer for both.
 */
internal fun HttpClient.installResolvedAddressGuard(
    policy: UrlValidationPolicy,
    resolver: (String) -> List<String> = ::resolveHostAddresses,
) {
    plugin(HttpSend).intercept { request ->
        val host = request.url.host.removeSurrounding("[", "]")
        val addresses = withContext(Dispatchers.Default) { resolver(host) }
        if (addresses.isEmpty()) throw UrlValidationException("The host could not be resolved")
        addresses.forEach { address -> policy.validateResolvedAddress(address) }
        execute(request)
    }
}

/**
 * NSURLSession sends a request through the proxy the system configures and lets the proxy resolve the target, which would
 * skip [installResolvedAddressGuard]. A guarded client therefore talks to the network directly.
 */
fun DarwinClientEngineConfig.connectDirectly() {
    configureSession { connectionProxyDictionary = emptyMap<Any?, Any?>() }
}

/** Installs the iOS checks for a policy: literal and scheme validation, then the resolved-address check when ranges are blocked. */
fun HttpClient.installIosEgressPolicy(policy: UrlValidationPolicy) {
    installUrlValidation(policy)
    if (policy.blocksAddressRanges()) installResolvedAddressGuard(policy)
}
