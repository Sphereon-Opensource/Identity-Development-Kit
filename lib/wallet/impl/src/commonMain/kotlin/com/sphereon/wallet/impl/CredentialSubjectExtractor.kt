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
 */

package com.sphereon.wallet.impl

import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.IdentifierRef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts the credential subject identifier(s) from an issued credential's raw bytes.
 *
 * Returns a list because W3C VCs allow multiple credential subjects. No cryptographic
 * verification is performed — the payload is already issuer-signed and trusted at this
 * point in the issuance flow.
 */
interface CredentialSubjectExtractor {
    /**
     * Extract subject [IdentifierRef]s from [raw] according to [format].
     *
     * Returns an empty list when the format carries no standard subject identifier
     * (e.g. mso_mdoc) or when no subject is present in the payload. Never throws.
     */
    fun extractSubjects(
        format: CredentialFormat,
        raw: String
    ): List<IdentifierRef>
}

/**
 * Default implementation. Uses IDK's [SdJwtCodec] for SD-JWT variants and
 * [JwsUtils.decodeBase64UrlToJson] for plain-JWT variants. mdoc returns empty by design
 * (ISO 18013-5 carries no globally scoped subject URI).
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<CredentialSubjectExtractor>())
class CredentialSubjectExtractorImpl : CredentialSubjectExtractor {
    override fun extractSubjects(
        format: CredentialFormat,
        raw: String
    ): List<IdentifierRef> =
        when {
            format.isSdJwt -> extractFromSdJwt(raw)

            format.isJwt || format == CredentialFormat.VC_LD_JSON_JWT -> extractFromJwt(raw)

            format.isMdoc -> emptyList()

            // ISO 18013-5 has no globally scoped subject URI
            else -> emptyList()
        }

    /**
     * SD-JWT: parse via [SdJwtCodec] (resolves all disclosures), then read the `sub` string
     * claim from the full payload. Returns one [IdentifierRef] when present, empty otherwise.
     */
    private fun extractFromSdJwt(raw: String): List<IdentifierRef> {
        val parseResult = SdJwtCodec.parse(raw)
        if (parseResult.isErr) return emptyList()
        val subValue =
            parseResult.value.payload.fullPayload["sub"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: return emptyList()
        return listOf(identifierRefFor(subValue))
    }

    /**
     * JWT (jwt_vc_json / vc+ld+json+jwt): split on '.' and base64url-decode the payload part via
     * [JwsUtils.decodeBase64UrlToJson]. Then:
     *
     * - For jwt_vc_json: read `vc.credentialSubject` — may be an object with an `id` field, or
     *   a JSON array of objects each with an `id` field (W3C multi-subject).
     * - For vc+ld+json+jwt: the VCDM 2.0 body is the entire payload; read `credentialSubject`
     *   directly (same object-or-array shape).
     * - Fallback: read the top-level `sub` string claim (covers the jwt_vc_json envelope style
     *   where the issuer mirrors the subject DID at JWT level).
     */
    private fun extractFromJwt(raw: String): List<IdentifierRef> {
        val parts = raw.split(".")
        if (parts.size < 2) return emptyList()
        return try {
            val payload = JwsUtils.decodeBase64UrlToJson(parts[1])
            credentialSubjectsFromJwtPayload(payload)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun credentialSubjectsFromJwtPayload(payload: JsonObject): List<IdentifierRef> {
        // jwt_vc_json: subjects are inside the `vc` claim
        val vcClaim = payload["vc"]
        if (vcClaim is JsonObject) {
            val fromVc = subjectsFromCredentialSubjectClaim(vcClaim)
            if (fromVc.isNotEmpty()) return fromVc
        }

        // vc+ld+json+jwt: VCDM 2.0 payload is the root — credentialSubject at root
        val fromRoot = subjectsFromCredentialSubjectClaim(payload)
        if (fromRoot.isNotEmpty()) return fromRoot

        // Fallback: top-level `sub` JWT claim (skip blank values — the W3C VC spec
        // requires a non-empty URI; a pre-auth flow without a real user yields "")
        val sub =
            payload["sub"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return emptyList()
        return listOf(identifierRefFor(sub))
    }

    /**
     * Reads `credentialSubject` from [parent], accepting both a single [JsonObject] and a
     * [JsonArray] of objects (W3C VC Data Model allows multiple subjects).
     *
     * Blank `id` values (empty string) are skipped — the W3C VC spec requires a non-empty
     * URI for the subject identifier.
     */
    private fun subjectsFromCredentialSubjectClaim(parent: JsonObject): List<IdentifierRef> {
        val cs = parent["credentialSubject"] ?: return emptyList()
        return when (cs) {
            is JsonObject -> {
                val id =
                    cs["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: return emptyList()
                listOf(identifierRefFor(id))
            }

            is JsonArray -> {
                cs.mapNotNull { element ->
                    (element as? JsonObject)
                        ?.get("id")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { identifierRefFor(it) }
                }
            }

            else -> {
                emptyList()
            }
        }
    }

    private fun identifierRefFor(value: String): IdentifierRef {
        val type = if (value.startsWith("did:")) IdentifierType.DID else IdentifierType("uri")
        return IdentifierRef(type = type, value = value)
    }
}
