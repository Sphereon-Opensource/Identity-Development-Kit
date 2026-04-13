/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName
import kotlin.native.ObjCName

// ============================================================================
// Localization Primitives
// ============================================================================

/**
 * Language-tagged string value.
 * Parallel to ETSI LoTE's MultiLangString, but defined in trust/core
 * to avoid a dependency on etsi-entities-public.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("LocalizedString", exact = true)
@JsExportCompat
@Serializable
data class LocalizedString(
    val lang: String,
    val value: String,
)

/**
 * Language-tagged URI value.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("LocalizedUri", exact = true)
@JsExportCompat
@Serializable
data class LocalizedUri(
    val lang: String,
    val uri: String,
)

fun List<LocalizedString>.forLang(lang: String): String? = firstOrNull { it.lang.equals(lang, ignoreCase = true) }?.value

fun List<LocalizedString>.toLangMap(): Map<String, String> = associate { it.lang to it.value }

@JvmName("forLangUri")
fun List<LocalizedUri>.forLang(lang: String): String? = firstOrNull { it.lang.equals(lang, ignoreCase = true) }?.uri

fun List<LocalizedUri>.toUriMap(): Map<String, String> = associate { it.lang to it.uri }

fun Map<String, String>.toLocalizedStrings(): List<LocalizedString> = map { (lang, value) -> LocalizedString(lang, value) }

fun Map<String, String>.toLocalizedUris(): List<LocalizedUri> = map { (lang, uri) -> LocalizedUri(lang, uri) }

// ============================================================================
// Entity Role
// ============================================================================

/**
 * The role an entity plays in a trust ecosystem.
 *
 * Generic roles (ISSUER, VERIFIER, WALLET, GENERAL) are defined here.
 * Trust-type-specific roles (e.g., "oidfed_openid_credential_issuer",
 * "etsi_pid_issuer") are defined in their respective modules.
 */
@Serializable
@JvmInline
value class EntityRole(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        val ISSUER = EntityRole("issuer")
        val VERIFIER = EntityRole("verifier")
        val WALLET = EntityRole("wallet")
        val GENERAL = EntityRole("general")
    }
}

// ============================================================================
// Entity Contact
// ============================================================================

@OptIn(ExperimentalObjCName::class)
@ObjCName("ContactType", exact = true)
@JsExportCompat
@Serializable
enum class ContactType {
    EMAIL,
    PHONE,
    URL,
    OTHER,
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("EntityContact", exact = true)
@JsExportCompat
@Serializable
data class EntityContact(
    val type: ContactType,
    val value: String,
    val description: String? = null,
)

// ============================================================================
// Entity Address
// ============================================================================

@OptIn(ExperimentalObjCName::class)
@ObjCName("EntityAddress", exact = true)
@JsExportCompat
@Serializable
data class EntityAddress(
    val lang: String? = null,
    val streetAddress: String? = null,
    val locality: String? = null,
    val stateOrProvince: String? = null,
    val postalCode: String? = null,
    val countryName: String? = null,
)

// ============================================================================
// Entity Logo & Display
// ============================================================================

@OptIn(ExperimentalObjCName::class)
@ObjCName("EntityLogo", exact = true)
@JsExportCompat
@Serializable
data class EntityLogo(
    val uri: String,
    val altText: String? = null,
    val lang: String? = null,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("EntityDisplay", exact = true)
@JsExportCompat
@Serializable
data class EntityDisplay(
    val name: String? = null,
    val description: String? = null,
    val locale: String? = null,
    val logo: EntityLogo? = null,
    /** True when this entry was synthesized from another locale rather than sourced directly */
    val derived: Boolean = false,
)

// ============================================================================
// Trust Chain Position
// ============================================================================

@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustChainNodeRole", exact = true)
@JsExportCompat
@Serializable
enum class TrustChainNodeRole {
    LEAF,
    INTERMEDIATE,
    TRUST_ANCHOR,
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("TrustChainPosition", exact = true)
@JsExportCompat
@Serializable
data class TrustChainPosition(
    val depth: Int,
    val role: TrustChainNodeRole,
)

// ============================================================================
// DiscoveredEntityInfo (main model)
// ============================================================================

/**
 * Uniform entity information discovered during trust establishment.
 *
 * All trust mechanisms (X.509, OIDFED, ETSI, DID) map their source-specific
 * entity data to this common model. The [sourceMetadata] field preserves the
 * original source structure alongside the mapped common fields.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DiscoveredEntityInfo", exact = true)
@JsExportCompat
@Serializable
data class DiscoveredEntityInfo(
    /** Entity identifier (URI, subject DN, DID, etc.) */
    val entityIdentifier: String,
    /** Source trust type that produced this info */
    val sourceType: TrustAnchorType,
    /** Position in the trust chain */
    val chainPosition: TrustChainPosition,
    // --- Mapped common fields ---
    val names: List<LocalizedString> = emptyList(),
    val tradeNames: List<LocalizedString> = emptyList(),
    val display: List<EntityDisplay> = emptyList(),
    val contacts: List<EntityContact> = emptyList(),
    val addresses: List<EntityAddress> = emptyList(),
    val informationUris: List<LocalizedUri> = emptyList(),
    val logos: List<EntityLogo> = emptyList(),
    val organizationName: String? = null,
    val jurisdiction: String? = null,
    val roles: List<EntityRole> = emptyList(),
    /** True when this entity is the one that was actually matched as the trust anchor during validation */
    val trustAnchor: Boolean = false,
    // --- Raw source metadata ---
    val sourceMetadata: Map<String, JsonElement> = emptyMap(),
)

// ============================================================================
// Entity Discovery Options
// ============================================================================

/**
 * Options controlling entity info discovery during trust validation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EntityDiscoveryOptions", exact = true)
@JsExportCompat
@Serializable
data class EntityDiscoveryOptions(
    /** Whether to perform entity discovery */
    val enabled: Boolean = false,
    /** Maximum chain depth to extract info from. 1 = leaf only (default), 0 = unlimited */
    val maxDepth: Int = 1,
    /** Entity roles to filter for. Empty = no filter, return all discovered roles. */
    val roles: List<EntityRole> = emptyList(),
    /**
     * When true, entity discovery is deferred until after the trust path is established.
     * Use this when multiple trust validators are tried and only the winning path matters.
     * The result shape is identical — the caller amends discoveredEntities after validation.
     *
     * When false (default), discovery happens inline during validation (eager).
     * Use this when specific trust anchors are provided and the path is known upfront.
     */
    val deferred: Boolean = false,
)

// ============================================================================
// DiscoveredContact (IDK conversion target)
// ============================================================================

/**
 * Flattened contact representation derived from [DiscoveredEntityInfo].
 * Suitable for display in wallet UIs or as input for Party creation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DiscoveredContact", exact = true)
@JsExportCompat
@Serializable
data class DiscoveredContact(
    val displayName: String?,
    val organizationName: String?,
    val emails: List<String>,
    val phones: List<String>,
    val urls: List<String>,
    val jurisdiction: String?,
    val address: EntityAddress?,
    val logoUri: String?,
)

fun DiscoveredEntityInfo.toContact(): DiscoveredContact =
    DiscoveredContact(
        displayName = display.firstOrNull()?.name ?: names.firstOrNull()?.value,
        organizationName = organizationName,
        emails = contacts.filter { it.type == ContactType.EMAIL }.map { it.value },
        phones = contacts.filter { it.type == ContactType.PHONE }.map { it.value },
        urls =
            contacts
                .filter { it.type == ContactType.URL }
                .map { it.value }
                .ifEmpty { informationUris.map { it.uri } },
        jurisdiction = jurisdiction,
        address = addresses.firstOrNull(),
        logoUri = logos.firstOrNull()?.uri ?: display.firstOrNull()?.logo?.uri,
    )

// ============================================================================
// EntityRole Mapping Utility
// ============================================================================

/**
 * Maps between generic [EntityRole] values and trust-type-specific identifiers.
 */
object EntityRoleMapping {
    fun toOidfedMetadataKeys(role: EntityRole): List<String> =
        when (role) {
            EntityRole.ISSUER -> listOf("openid_credential_issuer", "vc_issuer")
            EntityRole.VERIFIER -> listOf("openid_relying_party", "openid_credential_verifier")
            EntityRole.WALLET -> listOf("openid_wallet")
            EntityRole.GENERAL -> listOf("federation_entity")
            else -> listOf(role.value)
        }

    fun fromOidfedMetadataKey(key: String): EntityRole =
        when (key) {
            "openid_credential_issuer", "vc_issuer" -> EntityRole.ISSUER
            "openid_relying_party", "openid_credential_verifier" -> EntityRole.VERIFIER
            "openid_wallet" -> EntityRole.WALLET
            "federation_entity" -> EntityRole.GENERAL
            "oauth_authorization_server" -> EntityRole.ISSUER
            else -> EntityRole(key)
        }

    fun toEtsiServiceTypes(role: EntityRole): List<String> =
        when (role) {
            EntityRole.ISSUER -> {
                listOf(
                    "http://uri.etsi.org/TrstSvc/Svctype/IdV/nothrust/PIDIssuer",
                    "http://uri.etsi.org/TrstSvc/Svctype/IdV/nothrust/QEAAIssuer",
                )
            }

            EntityRole.VERIFIER -> {
                listOf(
                    "http://uri.etsi.org/TrstSvc/Svctype/IdV/nothrust/RelyingParty",
                )
            }

            EntityRole.WALLET -> {
                listOf(
                    "http://uri.etsi.org/TrstSvc/Svctype/IdV/nothrust/WalletProvider",
                )
            }

            EntityRole.GENERAL -> {
                emptyList()
            }

            else -> {
                listOf(role.value)
            }
        }

    fun fromEtsiServiceType(serviceType: String): EntityRole =
        when {
            serviceType.contains("PIDIssuer", ignoreCase = true) -> EntityRole.ISSUER
            serviceType.contains("QEAAIssuer", ignoreCase = true) -> EntityRole.ISSUER
            serviceType.contains("RelyingParty", ignoreCase = true) -> EntityRole.VERIFIER
            serviceType.contains("WalletProvider", ignoreCase = true) -> EntityRole.WALLET
            serviceType.contains("Registrar", ignoreCase = true) -> EntityRole.GENERAL
            else -> EntityRole(serviceType)
        }
}

/**
 * Infers a [ContactType] from the contact value string.
 */
fun inferContactType(value: String): ContactType =
    when {
        value.contains("@") -> ContactType.EMAIL
        value.startsWith("http://") || value.startsWith("https://") -> ContactType.URL
        value.startsWith("+") || value.all { it.isDigit() || it == '-' || it == ' ' || it == '(' || it == ')' } -> ContactType.PHONE
        else -> ContactType.OTHER
    }
