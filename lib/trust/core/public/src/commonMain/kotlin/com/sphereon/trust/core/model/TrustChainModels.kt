/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.native.ObjCName

/**
 * Cryptographic status of the links in a [TrustChain].
 *
 * VERIFIED means each hop attested the next, toward the anchor. BROKEN means a link failed
 * verification. This is not domain admission and not attestation authorisation.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustChainLinks", exact = true)
enum class TrustChainLinks {
    VERIFIED,
    BROKEN,
}

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustChainHopPosition", exact = true)
enum class TrustChainHopPosition {
    LEAF,
    INTERMEDIATE,
    ANCHOR,
}

/**
 * One hop in an ordered trust chain from leaf to anchor.
 *
 * identifier, displayName, position and assertedBy are the fields that OpenID Federation, X.509
 * and ETSI can all populate. metadata_policy and x5c failed that test and are not here.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustChainHop", exact = true)
data class TrustChainHop(
    val identifier: String,
    val displayName: String? = null,
    val position: TrustChainHopPosition,
    val assertedBy: String? = null,
) {
    init {
        require(identifier.isNotBlank()) { "trust_chain_hop_identifier_blank" }
        require(displayName == null || displayName.isNotBlank()) { "trust_chain_hop_display_name_blank" }
        require(assertedBy == null || assertedBy.isNotBlank()) { "trust_chain_hop_asserted_by_blank" }
        if (position == TrustChainHopPosition.ANCHOR) {
            require(assertedBy == null) { "trust_chain_anchor_must_not_name_an_attester" }
        }
    }
}

/**
 * Ordered hops from leaf to anchor. Absent on [TrustValidationResult] means the mechanism
 * produced no chain. A present chain with zero hops is not a thing and cannot be constructed.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustChain", exact = true)
data class TrustChain(
    val hops: List<TrustChainHop>,
    val links: TrustChainLinks,
) {
    init {
        require(hops.isNotEmpty()) { "trust_chain_hops_empty" }
        require(hops.last().position == TrustChainHopPosition.ANCHOR) { "trust_chain_last_hop_must_be_anchor" }
        if (hops.size == 1) {
            require(hops.single().position == TrustChainHopPosition.ANCHOR) { "trust_chain_single_hop_must_be_anchor" }
        } else {
            require(hops.first().position == TrustChainHopPosition.LEAF) { "trust_chain_first_hop_must_be_leaf" }
            require(hops.drop(1).dropLast(1).all { it.position == TrustChainHopPosition.INTERMEDIATE }) {
                "trust_chain_middle_hops_must_be_intermediate"
            }
        }
    }

    companion object {
        /**
         * Build a chain from ordered identifiers, leaf to anchor. Returns null when [identifiers]
         * is empty so a caller cannot present an empty-but-present chain.
         */
        fun fromOrderedIdentifiers(
            identifiers: List<String>,
            links: TrustChainLinks,
            displayNames: List<String?> = List(identifiers.size) { null },
        ): TrustChain? {
            if (identifiers.isEmpty()) return null
            require(displayNames.size == identifiers.size) { "trust_chain_display_names_length" }
            val hops =
                identifiers.mapIndexed { index, identifier ->
                    val position =
                        when {
                            identifiers.size == 1 || index == identifiers.lastIndex -> TrustChainHopPosition.ANCHOR
                            index == 0 -> TrustChainHopPosition.LEAF
                            else -> TrustChainHopPosition.INTERMEDIATE
                        }
                    TrustChainHop(
                        identifier = identifier,
                        displayName = displayNames[index],
                        position = position,
                        assertedBy = identifiers.getOrNull(index + 1),
                    )
                }
            return TrustChain(hops = hops, links = links)
        }

        /**
         * Federation entity-statement entries are JWTs (use `sub`) or entity identifiers (URI/DID).
         * Opaque strings are not hops: returning null keeps the chain absent rather than inventing.
         */
        fun fromEntityStatementEntries(
            entries: List<String>,
            links: TrustChainLinks,
        ): TrustChain? {
            if (entries.isEmpty()) return null
            val identifiers = entries.map { identifierFromEntityStatementEntry(it) ?: return null }
            return fromOrderedIdentifiers(identifiers, links)
        }
    }
}

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustDomainPosture", exact = true)
enum class TrustDomainPosture {
    FAIL_CLOSED,
    TRUST_ALL,
    CUSTOM,
}

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustDomainAdmissionOutcome", exact = true)
enum class TrustDomainAdmissionOutcome {
    ADMITTED_NAMED_DOMAIN,
    ADMITTED_TRUST_ALL,
    NOT_ADMITTED,
    FAIL_CLOSED,
}

/**
 * Whether the chain's anchor is admitted by the assigned trust domains.
 *
 * Separate from [TrustChain.links]. A cryptographically perfect chain can terminate at an
 * anchor this deployment does not accept. trust-all acceptance cannot name an admitting domain.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustDomainAdmission", exact = true)
data class TrustDomainAdmission(
    val posture: TrustDomainPosture,
    val outcome: TrustDomainAdmissionOutcome,
    val admittingDomain: String? = null,
) {
    init {
        require(admittingDomain == null || admittingDomain.isNotBlank()) { "trust_domain_admitting_domain_blank" }
        when (outcome) {
            TrustDomainAdmissionOutcome.ADMITTED_NAMED_DOMAIN -> {
                require(posture == TrustDomainPosture.CUSTOM) { "trust_named_admission_requires_custom_posture" }
                require(!admittingDomain.isNullOrBlank()) { "trust_named_admission_requires_admitting_domain" }
            }
            TrustDomainAdmissionOutcome.ADMITTED_TRUST_ALL -> {
                require(posture == TrustDomainPosture.TRUST_ALL) { "trust_all_admission_requires_trust_all_posture" }
                require(admittingDomain == null) { "trust_all_admission_must_not_name_a_domain" }
            }
            TrustDomainAdmissionOutcome.FAIL_CLOSED -> {
                require(posture == TrustDomainPosture.FAIL_CLOSED) { "trust_fail_closed_outcome_requires_fail_closed_posture" }
                require(admittingDomain == null) { "trust_fail_closed_must_not_name_a_domain" }
            }
            TrustDomainAdmissionOutcome.NOT_ADMITTED -> {
                require(posture == TrustDomainPosture.CUSTOM) { "trust_not_admitted_requires_custom_posture" }
                require(admittingDomain == null) { "trust_not_admitted_must_not_name_a_domain" }
            }
        }
    }

    companion object {
        /**
         * [posture] is supplied by the caller from the real issuerTrustMode (and the fail-closed
         * fallback). An empty [assignedAnchorIds] must not be used to infer TRUST_ALL.
         */
        fun admit(
            chain: TrustChain?,
            posture: TrustDomainPosture,
            assignedAnchorIds: List<String>,
        ): TrustDomainAdmission =
            when (posture) {
                TrustDomainPosture.TRUST_ALL ->
                    TrustDomainAdmission(
                        posture = TrustDomainPosture.TRUST_ALL,
                        outcome = TrustDomainAdmissionOutcome.ADMITTED_TRUST_ALL,
                    )
                TrustDomainPosture.FAIL_CLOSED ->
                    TrustDomainAdmission(
                        posture = TrustDomainPosture.FAIL_CLOSED,
                        outcome = TrustDomainAdmissionOutcome.FAIL_CLOSED,
                    )
                TrustDomainPosture.CUSTOM -> {
                    val anchorId = chain?.hops?.lastOrNull { it.position == TrustChainHopPosition.ANCHOR }?.identifier
                    val admittedId = assignedAnchorIds.firstOrNull { it == anchorId }
                    if (admittedId != null) {
                        TrustDomainAdmission(
                            posture = TrustDomainPosture.CUSTOM,
                            outcome = TrustDomainAdmissionOutcome.ADMITTED_NAMED_DOMAIN,
                            admittingDomain = admittedId,
                        )
                    } else {
                        TrustDomainAdmission(
                            posture = TrustDomainPosture.CUSTOM,
                            outcome = TrustDomainAdmissionOutcome.NOT_ADMITTED,
                        )
                    }
                }
            }
    }
}

@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("AttestationAuthorisationOutcome", exact = true)
enum class AttestationAuthorisationOutcome {
    AUTHORISED,
    NOT_AUTHORISED,
    UNKNOWN,
}

/**
 * Whether a trusted party may issue this attestation type.
 *
 * A party being trusted says nothing about which attestation types it may issue. Absence on
 * [TrustValidationResult] means nothing on the path could answer, which is the honest state today.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("AttestationAuthorisation", exact = true)
data class AttestationAuthorisation(
    val outcome: AttestationAuthorisationOutcome,
    val scheme: String? = null,
) {
    init {
        require(scheme == null || scheme.isNotBlank()) { "attestation_authorisation_scheme_blank" }
    }
}

internal fun identifierFromEntityStatementEntry(entry: String): String? {
    val trimmed = entry.trim().takeIf { it.isNotEmpty() } ?: return null
    val parts = trimmed.split('.')
    if (parts.size == 3) {
        return jwtSubject(trimmed)
    }
    if ("://" in trimmed || trimmed.startsWith("did:", ignoreCase = true)) {
        return trimmed
    }
    return null
}

@OptIn(ExperimentalEncodingApi::class)
private fun jwtSubject(jwt: String): String? =
    runCatching {
        val payloadPart = jwt.split('.')[1]
        val json =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(payloadPart)
                .decodeToString()
        Json.parseToJsonElement(json).jsonObject["sub"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()
