/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.trust.core.resolver

import com.sphereon.trust.core.TrustDiagnosticReasonCodes

/**
 * Platform-neutral classification for addresses returned for a trust-list
 * hostname. The JVM transport supplies the addresses from one DNS lookup and
 * uses the same approved values as its dial resolver.
 */
internal object TrustListAddressPolicy {
    fun requireGlobalAddresses(addresses: List<String>) {
        if (addresses.isEmpty()) {
            throw TrustListResolutionException(
                "Trust-list destination could not be resolved",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_ADDRESS_RESOLUTION_FAILED,
            )
        }
        if (addresses.any { !isGlobalAddress(it) }) {
            throw TrustListResolutionException(
                "Trust-list destination address was blocked",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_ADDRESS_BLOCKED,
            )
        }
    }

    fun requireApprovedDialAddress(
        approvedAddresses: List<String>,
        dialAddress: String,
    ) {
        if (!isGlobalAddress(dialAddress)) {
            throw TrustListResolutionException(
                "Trust-list destination address was blocked",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_ADDRESS_BLOCKED,
            )
        }
        if (approvedAddresses.none { normalize(it) == normalize(dialAddress) }) {
            throw TrustListResolutionException(
                "Trust-list connection did not use an approved destination",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_DNS_REBINDING,
            )
        }
    }

    fun isGlobalAddress(address: String): Boolean {
        val normalized = normalize(address)
        if (normalized.isEmpty() || '%' in normalized) {
            return false
        }

        parseIpv4(normalized)?.let { return isGlobalIpv4(it) }
        val groups = parseIpv6(normalized) ?: return false
        if (groups.all { it == 0 }) return false
        if (groups.dropLast(1).all { it == 0 } && groups.last() == 1) return false

        if (groups[0] == 0 && groups[1] == 0 && groups[2] == 0 && groups[3] == 0 && groups[4] == 0 && groups[5] == 0xffff) {
            return false // ::ffff:0:0/96 IPv4-mapped addresses
        }

        val first = groups[0]
        return when {
            first in 0xfc00..0xfdff -> false // fc00::/7 unique-local
            first in 0xfe80..0xfebf -> false // fe80::/10 link-local
            first in 0xff00..0xffff -> false // ff00::/8 multicast
            first == 0x0100 && groups[1] == 0 && groups[2] == 0 && groups[3] == 0 -> false // 100::/64 discard-only
            first == 0x0100 && groups[1] == 0 && groups[2] == 0 && groups[3] == 1 -> false // 100:0:0:1::/64 dummy
            first == 0x0064 && groups[1] == 0xff9b && groups[2] == 1 -> false // 64:ff9b:1::/48 translation
            first == 0x5f00 -> false // 5f00::/16 SRv6 SIDs
            first == 0x2001 && groups[1] in 0x0000..0x01ff ->
                if (groups[1] == 1) isAllowedProtocolAnycast(groups) else false // 2001::/23, except allocated anycast addresses
            first == 0x2001 && groups[1] == 2 && groups[2] == 0 -> false // 2001:2::/48 benchmarking
            first == 0x2001 && groups[1] == 4 && groups[2] != 0x0070 -> false // only 2001:4:112::/48 is allocated
            first == 0x2001 && groups[1] == 0x0db8 -> false // documentation
            first == 0x2001 && groups[1] in 0x0010..0x002f -> false // ORCHID and ORCHIDv2
            first == 0x2002 -> false // 2002::/16 6to4
            first == 0x3fff && groups[1] in 0x0000..0x0fff -> false // documentation
            first in 0xfec0..0xfeff -> false // deprecated site-local
            first == 0x0000 -> false
            else -> true
        }
    }

    private fun isAllowedProtocolAnycast(groups: List<Int>): Boolean =
        groups.drop(2).take(5).all { it == 0 } && groups.last() in 1..3

    private fun normalize(address: String): String =
        address.trim().removeSurrounding("[", "]").lowercase()

    private fun parseIpv4(value: String): List<Int>? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        return octets.takeIf { octets.all { octet -> octet in 0..255 } }
    }

    private fun parseIpv6(value: String): List<Int>? {
        if (':' !in value) return null
        val withoutIpv4 =
            if ('.' in value) {
                val separator = value.lastIndexOf(':')
                val ipv4 = parseIpv4(value.substring(separator + 1)) ?: return null
                val first = ((ipv4[0] shl 8) or ipv4[1]).toString(16)
                val second = ((ipv4[2] shl 8) or ipv4[3]).toString(16)
                value.substring(0, separator + 1) +
                    "$first:$second"
            } else {
                value
            }
        val doubleColon = withoutIpv4.indexOf("::")
        if (doubleColon >= 0 && withoutIpv4.indexOf("::", doubleColon + 1) >= 0) return null
        val parts = withoutIpv4.split(':')
        val groups =
            parts.filter { it.isNotEmpty() }.map { part ->
                part.toIntOrNull(16)?.takeIf { it in 0..0xffff } ?: return null
            }
        if (doubleColon < 0) return groups.takeIf { it.size == 8 }
        val missing = 8 - groups.size
        if (missing <= 0) return null
        val expanded = ArrayList<Int>(8)
        val left = withoutIpv4.substring(0, doubleColon).split(':').filter { it.isNotEmpty() }
        val right = withoutIpv4.substring(doubleColon + 2).split(':').filter { it.isNotEmpty() }
        left.mapTo(expanded) { it.toInt(16) }
        repeat(missing) { expanded += 0 }
        right.mapTo(expanded) { it.toInt(16) }
        return expanded.takeIf { it.size == 8 }
    }

    private fun isGlobalIpv4(octets: List<Int>): Boolean {
        val (a, b, c, d) = octets
        return when {
            a == 0 -> false // unspecified and "this network"
            a == 10 -> false
            a == 100 && b in 64..127 -> false
            a == 127 -> false
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 0 -> false
            a == 192 && b == 2 -> false
            a == 192 && b == 88 && c == 99 -> false
            a == 192 && b == 168 -> false
            a == 198 && b in 18..19 -> false
            a == 198 && b == 51 && c == 100 -> false
            a == 203 && b == 0 && c == 113 -> false
            a in 224..255 -> false
            else -> true
        }
    }
}
