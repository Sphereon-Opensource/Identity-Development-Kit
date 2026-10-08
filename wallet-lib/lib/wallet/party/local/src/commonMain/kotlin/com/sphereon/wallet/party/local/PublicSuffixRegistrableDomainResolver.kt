/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.party.local

import com.sphereon.core.idn.Idna

/** Offline, commonMain resolver abstraction used only for Organization association suggestions. */
fun interface WalletRegistrableDomainResolver {
    /** Returns the PSL registrable domain (public suffix plus one label), or null. */
    fun registrableDomain(host: String): String?
}

/** Full publicsuffix.org algorithm over the pinned ICANN and PRIVATE rule sections. */
object PublicSuffixRegistrableDomainResolver : WalletRegistrableDomainResolver {
    private val rules: RuleSet by lazy {
        val exact = linkedSetOf<String>()
        val wildcard = linkedSetOf<String>()
        val exception = linkedSetOf<String>()
        BundledPublicSuffixRules.chunks.asSequence().flatMap { it.lineSequence() }.forEach { rule ->
            when {
                rule.startsWith('!') -> exception += rule.substring(1).lowercase()
                rule.startsWith("*.") -> wildcard += rule.substring(2).lowercase()
                else -> exact += rule.lowercase()
            }
        }
        RuleSet(exact, wildcard, exception)
    }

    override fun registrableDomain(host: String): String? {
        val canonical = canonicalHost(host) ?: return null
        val labels = canonical.split('.')
        if (labels.size < 2) return null

        val exceptionLength =
            labels.indices
                .map { index -> labels.drop(index).joinToString(".") }
                .filter { it in rules.exception }
                .maxOfOrNull { it.count { character -> character == '.' } + 1 }
        val publicSuffixLength =
            if (exceptionLength != null) {
                exceptionLength - 1
            } else {
                var longest = 1 // PSL prevailing default rule: *
                labels.indices.forEach { index ->
                    val suffix = labels.drop(index).joinToString(".")
                    val suffixLength = labels.size - index
                    if (suffix in rules.exact) longest = maxOf(longest, suffixLength)
                    if (index > 0 && suffix in rules.wildcard) longest = maxOf(longest, suffixLength + 1)
                }
                longest
            }
        if (labels.size <= publicSuffixLength) return null
        return labels.takeLast(publicSuffixLength + 1).joinToString(".")
    }

    private fun canonicalHost(value: String): String? {
        val host = value.trim().trimEnd('.')
        if (host.isBlank() || ':' in host || host.startsWith('[') || host.endsWith(']')) return null
        if (isIpv4Literal(host)) return null
        val ascii = Idna.toAscii(host)
        if (!ascii.isOk) return null
        val unicode = Idna.toUnicode(ascii.value)
        if (!unicode.isOk) return null
        return unicode.value.trimEnd('.').lowercase()
    }

    private fun isIpv4Literal(host: String): Boolean {
        val labels = host.split('.')
        return labels.size == 4 && labels.all { label -> label.toIntOrNull()?.let { it in 0..255 } == true }
    }

    private data class RuleSet(
        val exact: Set<String>,
        val wildcard: Set<String>,
        val exception: Set<String>,
    )
}
