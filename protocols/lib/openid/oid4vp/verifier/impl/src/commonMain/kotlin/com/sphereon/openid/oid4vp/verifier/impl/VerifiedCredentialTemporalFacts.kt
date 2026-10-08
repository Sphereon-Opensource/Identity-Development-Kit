package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.openid.oid4vp.verifier.VerifiedCredentialTemporalFacts
import kotlinx.serialization.json.*
import kotlin.time.Instant

/** A projection of already authenticated claims, not a replacement signature or time verifier. */
internal fun verifiedCredentialTemporalFacts(claims: JsonObject?, vcdm: Boolean = false): VerifiedCredentialTemporalFacts? {
    if (claims == null) return null
    return try {
        fun numeric(name: String): Long? {
            val value = claims[name] ?: return null
            val primitive = value as? JsonPrimitive ?: error("Malformed numeric date")
            require(!primitive.isString)
            val seconds = requireNotNull(primitive.doubleOrNull)
            require(seconds.isFinite() && seconds * 1000 > Long.MIN_VALUE.toDouble() && seconds * 1000 < Long.MAX_VALUE.toDouble())
            return (seconds * 1000).toLong()
        }
        val body = if (vcdm) (claims["vc"] as? JsonObject ?: claims) else null
        fun date(name: String): Long? {
            val value = body?.get(name) ?: return null
            val primitive = value as? JsonPrimitive ?: error("Malformed credential date")
            require(primitive.isString)
            return Instant.parse(primitive.content).toEpochMilliseconds()
        }
        val issuance = date("issuanceDate")
        val from = listOfNotNull(numeric("nbf"), issuance, date("validFrom")).maxOrNull()
        val until = listOfNotNull(numeric("exp"), date("expirationDate"), date("validUntil")).minOrNull()
        require(from == null || until == null || from < until)
        VerifiedCredentialTemporalFacts(numeric("iat") ?: issuance, from, until)
    } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null }
}
