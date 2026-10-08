/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.presenter.contracts

/**
 * The candidate BCP 47 tag that best serves [languageTag]: the exact tag (ignoring case and the
 * `_` separator), then the first candidate with the same primary language. Null when none matches,
 * so the caller keeps its own default (the issuer's first entry).
 */
fun bestLanguageMatch(languageTag: String, candidates: Collection<String>): String? {
    fun normalize(tag: String) = tag.trim().replace('_', '-').lowercase()
    val wanted = normalize(languageTag)
    if (wanted.isEmpty()) return null
    candidates.firstOrNull { normalize(it) == wanted }?.let { return it }
    val language = wanted.substringBefore('-')
    return candidates.firstOrNull { normalize(it).substringBefore('-') == language }
}

/** The entry of [localized] for [languageTag], or [default] when none matches. */
fun localizedOrDefault(languageTag: String, localized: Map<String, String>, default: String): String =
    bestLanguageMatch(languageTag, localized.keys)?.let { localized[it] } ?: default
