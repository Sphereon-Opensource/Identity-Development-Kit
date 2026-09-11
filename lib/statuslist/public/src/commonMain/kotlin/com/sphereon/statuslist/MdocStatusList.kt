package com.sphereon.statuslist

/**
 * ISO/IEC 18013-5 second-edition status payloads. These models deliberately do not replace the
 * generic JWT/CWT Token Status List models: mdoc inner maps use text keys and a binary `lst`.
 */
sealed class MdocStatusListPayload {
    /** One-bit status list: `bits`, binary `lst`, and optional aggregation URI. */
    class Token(
        val bits: Int,
        val list: ByteArray,
        val aggregationUri: String? = null,
    ) : MdocStatusListPayload() {
        init {
            require(bits == 1) { "mdoc status lists require bits=1" }
            require(list.isNotEmpty()) { "mdoc status list must not be empty" }
        }

        override fun equals(other: Any?): Boolean =
            other is Token && bits == other.bits && list.contentEquals(other.list) && aggregationUri == other.aggregationUri

        override fun hashCode(): Int = 31 * (31 * bits + list.contentHashCode()) + (aggregationUri?.hashCode() ?: 0)
    }

    /**
     * Identifier-list form. The wire representation is an `identifiers` map whose keys are
     * binary identifiers and whose values are IdentifierInfo maps. IdentifierInfo is currently
     * reserved by ISO/IEC 18013-5, so this model exposes the identifier values and emits empty
     * info maps while retaining the optional aggregation URI.
     */
    class IdentifierList(
        val identifiers: List<ByteArray>,
        val aggregationUri: String? = null,
    ) : MdocStatusListPayload() {
        init {
            require(identifiers.all { it.isNotEmpty() }) { "mdoc identifiers must not be empty" }
            require(identifiers.distinctBy { it.toList() }.size == identifiers.size) {
                "mdoc identifier list must not contain duplicate identifiers"
            }
        }

        fun identifierAt(index: Int): ByteArray = identifiers.getOrElse(index) {
            throw IndexOutOfBoundsException("mdoc identifier index $index is outside 0..${identifiers.lastIndex}")
        }.copyOf()

        override fun equals(other: Any?): Boolean =
            other is IdentifierList &&
                aggregationUri == other.aggregationUri &&
                identifiers.size == other.identifiers.size &&
                identifiers.indices.all { identifiers[it].contentEquals(other.identifiers[it]) }

        override fun hashCode(): Int =
            identifiers.fold(aggregationUri?.hashCode() ?: 0) { result, identifier ->
                31 * result + identifier.contentHashCode()
            }
    }
}

/** Additive codec SPI for mdoc status payloads; the generic status-list codecs remain unchanged. */
interface MdocStatusListCodec {
    fun encode(payload: MdocStatusListPayload): ByteArray

    fun decode(encoded: ByteArray): MdocStatusListPayload
}

/**
 * The claim set carried inside an ISO/IEC 18013-5 revocation CWT.
 *
 * This is intentionally separate from [StatusListToken]: the latter is the generic hosted
 * status-list envelope, while an mdoc CWT uses a CBOR claim map signed by COSE_Sign1. The
 * expiration is mandatory for the mdoc profile, even though it remains optional in the generic
 * status-list API for backward compatibility.
 */
data class MdocRevocationCwtClaims(
    val issuer: String? = null,
    val subject: String? = null,
    val issuedAtEpochSeconds: Long? = null,
    val expiresAtEpochSeconds: Long,
    val ttlSeconds: Long? = null,
    val payload: MdocStatusListPayload,
) {
    init {
        require(expiresAtEpochSeconds >= 0) { "mdoc revocation CWT exp must be a non-negative epoch second" }
        issuedAtEpochSeconds?.let { require(it >= 0) { "mdoc revocation CWT iat must be a non-negative epoch second" } }
        ttlSeconds?.let { require(it >= 0) { "mdoc revocation CWT ttl must be non-negative" } }
    }
}

/** Encodes and decodes the mdoc-specific CWT claim set (not the surrounding COSE_Sign1). */
interface MdocRevocationCwtClaimsCodec {
    fun encode(claims: MdocRevocationCwtClaims): ByteArray

    fun decode(encoded: ByteArray): MdocRevocationCwtClaims
}

/** Input to the additive mdoc CWT signer. An mdoc revocation CWT always requires an expiry. */
data class MdocCwtStatusListSigningArgs(
    val issuer: String,
    val statusListUri: String,
    val signingKeyName: String,
    val expiresAtEpochSeconds: Long,
    val payload: MdocStatusListPayload,
    val issuedAtEpochSeconds: Long? = null,
    val ttlSeconds: Long? = null,
)
