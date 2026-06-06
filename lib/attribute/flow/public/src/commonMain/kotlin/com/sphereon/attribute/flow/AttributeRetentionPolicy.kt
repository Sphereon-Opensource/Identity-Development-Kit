/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.flow

import com.sphereon.core.api.compliance.LegalBasis
import com.sphereon.core.api.compliance.TerritoryRestriction
import com.sphereon.core.api.compliance.TrustFrameworkType
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How long an [AttributeRecord] may be retained, and under what legal basis.
 *
 * Sealed; variants are top-level (mirroring [AttributeOrigin]) so kotlinx-serialization and JS
 * export stay well-behaved. Most attributes are [SessionRetention]; [RetainedRetention] carries
 * the explicit legal basis, regulatory framework, and data-residency context required to keep a
 * datum beyond the session.
 */
@JsExportCompat
@Serializable
sealed interface AttributeRetentionPolicy

/** In-memory only. Never serialized to persistent storage. */
@Serializable
@SerialName("ephemeral")
data object EphemeralRetention : AttributeRetentionPolicy

/** Stored encrypted in the session store; cleared on session completion / expiry. */
@Serializable
@SerialName("session")
data class SessionRetention(
    val encryptionRequired: Boolean = true,
) : AttributeRetentionPolicy

/**
 * Persisted long-term with an explicit retention period, legal basis, and jurisdiction context.
 */
@Serializable
@SerialName("retained")
data class RetainedRetention(
    val retentionDays: Int,
    /** Legal basis for retention (GDPR Article 6(1), AML record-keeping, ...). */
    val legalBasis: LegalBasis,
    /** Regulatory framework governing this data (eIDAS, NIST, UK DIATF, ...). */
    val regulatoryFramework: TrustFrameworkType? = null,
    /** Territory / jurisdiction for data residency (ISO 3166 country code). */
    val territoryId: String? = null,
    /** Territory-specific residency restriction. */
    val territoryRestriction: TerritoryRestriction? = null,
    val encryptionRequired: Boolean = true,
    /** Purpose limitation (GDPR Art. 5(1)(b)). */
    val purpose: String? = null,
    /** Whether the data subject has given explicit consent. */
    val consentObtained: Boolean = false,
) : AttributeRetentionPolicy
