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

package com.sphereon.trust.etsi.lote.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmName

/**
 * Multilingual string value per ETSI TS 119 602 JSON binding.
 *
 * JSON representation: `{"lang": "en", "value": "Name"}`
 */
@Serializable
data class MultiLangString(
    val lang: String,
    val value: String,
)

/**
 * Multilingual URI value per ETSI TS 119 602 JSON binding.
 *
 * JSON representation: `{"lang": "en", "uriValue": "https://..."}`
 */
@Serializable
data class MultiLangURI(
    val lang: String,
    val uriValue: String,
)

/**
 * Finds the value for the given language code, or null if not found.
 */
fun List<MultiLangString>.forLang(lang: String): String? = firstOrNull { it.lang.equals(lang, ignoreCase = true) }?.value

/**
 * Converts to a map of language code to value.
 */
fun List<MultiLangString>.toLangMap(): Map<String, String> = associate { it.lang to it.value }

/**
 * Finds the URI value for the given language code, or null if not found.
 */
@JvmName("forLangURI")
fun List<MultiLangURI>.forLang(lang: String): String? = firstOrNull { it.lang.equals(lang, ignoreCase = true) }?.uriValue

/**
 * Converts to a map of language code to URI value.
 */
fun List<MultiLangURI>.toURIMap(): Map<String, String> = associate { it.lang to it.uriValue }

/**
 * Creates a MultiLangString list from a map of language code to value.
 */
fun Map<String, String>.toMultiLangStrings(): List<MultiLangString> = map { (lang, value) -> MultiLangString(lang, value) }

/**
 * Creates a MultiLangURI list from a map of language code to URI value.
 */
fun Map<String, String>.toMultiLangURIs(): List<MultiLangURI> = map { (lang, uriValue) -> MultiLangURI(lang, uriValue) }
