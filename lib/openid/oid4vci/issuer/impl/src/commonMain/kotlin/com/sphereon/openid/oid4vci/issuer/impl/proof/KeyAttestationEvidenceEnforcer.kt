/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.proof

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.KeyAttestationsRequired
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.issuer.proof.VerifiedKeyAttestation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/**
 * Enforces the Wallet Unit/TS03 evidence profile carried inside OID4VCI key attestations.
 *
 * The generic OID4VCI verifier stays responsible for JOSE trust, expiry, nonce, and proof-key
 * binding. This class only checks the persisted Wallet Unit evidence claims that make a KA usable
 * for device-bound PID issuance.
 */
internal class KeyAttestationEvidenceEnforcer(
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
) {
    fun enforce(
        header: JsonObject,
        claims: JsonObject,
        policy: KeyAttestationsRequired?,
        attestedKeyCount: Int,
    ): IdkResult<VerifiedKeyAttestation?, IdkError> {
        val productionRequired = policy != null
        val evidence = collectEvidence(claims)

        if (!productionRequired) {
            if (hasRevokedEvidence(claims, evidence)) {
                return invalid("key attestation carries revoked key_storage_status evidence")
            }
            return Ok(optionalEvidence(claims, evidence, attestedKeyCount))
        }

        if (!hasX5c(header)) {
            return invalid("production key attestation requires an x5c-bound Wallet Provider signature")
        }
        if (hasLocalOrTestEvidence(claims, evidence)) {
            return invalid(
                "production key attestation cannot use local, backendless, LOCAL_EVALUATION, WEBAUTHN_PRF_WSCD, " +
                    "or unsupported key_storage/user_authentication evidence",
            )
        }
        if (!hasStructuredCertification(claims["certification"])) {
            return invalid("production key attestation requires certification evidence")
        }

        val keyStorage = parseKeyStorage(claims["key_storage"], productionRequired).getOrElse { return Err(it) }
        val userAuthentication = parseUserAuthentication(claims["user_authentication"], productionRequired).getOrElse { return Err(it) }
        val status = parseStatusEvidence(claims["key_storage_status"], productionRequired).getOrElse { return Err(it) }

        if ("iso_18045_high" !in keyStorage.canonical) {
            return invalid("production key attestation key_storage must describe ISO_18045_HIGH protection")
        }
        if (keyStorage.secureComponentCanonical !in SUPPORTED_PRODUCTION_SECURE_COMPONENTS) {
            return invalid(
                "production key attestation secure_component must be a WSCA/WSCD component " +
                    "(got '${keyStorage.secureComponent ?: "missing"}')",
            )
        }
        if (keyStorage.nonExportable != true) {
            return invalid("production key attestation key_storage must prove non-exportable key protection")
        }
        if (keyStorage.canonical.any { it in UNSUPPORTED_KEY_STORAGE_VALUES }) {
            return invalid("production key attestation contains unsupported key_storage evidence '${keyStorage.values.joinToString()}'")
        }
        if (userAuthentication.assuranceLevel == null) {
            return invalid("production key attestation user_authentication.assurance_level is missing")
        }
        if (userAuthentication.canonical.any { it in UNSUPPORTED_USER_AUTH_VALUES }) {
            return invalid(
                "production key attestation contains unsupported user_authentication evidence " +
                    "'${userAuthentication.values.joinToString()}'",
            )
        }
        if (status.revoked || hasRevokedEvidence(claims, evidence)) {
            return invalid("production key attestation key_storage_status is revoked")
        }
        if (status.statusListUri == null || status.statusIndex == null) {
            return invalid("production key attestation key_storage_status must contain a status list URI and index")
        }
        val statusExpiry = status.expiresAtEpochSeconds
        if (statusExpiry == null || statusExpiry < nowEpochSeconds()) {
            return invalid("production key attestation key_storage_status maintenance expiry has passed")
        }

        val actualStorage = keyStorage.canonical
        policy.keyStorage.orEmpty().forEach { required ->
            if (canonical(required) !in actualStorage) {
                return invalid(
                    "production key attestation key_storage does not include required level '$required' " +
                        "(got ${keyStorage.values})",
                )
            }
        }
        val actualUserAuthentication = userAuthentication.canonical
        policy.userAuthentication.orEmpty().forEach { required ->
            if (canonical(required) !in actualUserAuthentication) {
                return invalid(
                    "production key attestation user_authentication does not include required level '$required' " +
                        "(got ${userAuthentication.values})",
                )
            }
        }

        return Ok(
            VerifiedKeyAttestation(
                evidenceId = evidence.firstMatchingValue(EVIDENCE_ID_KEYS),
                walletUnitId = evidence.firstMatchingValue(WALLET_UNIT_ID_KEYS) ?: claims.primitiveString("sub"),
                walletAccountId = evidence.firstMatchingValue(WALLET_ACCOUNT_ID_KEYS),
                issuer = claims.primitiveString("iss"),
                subject = claims.primitiveString("sub"),
                jwtId = claims.primitiveString("jti"),
                expiresAtEpochSeconds = claims.primitiveLong("exp"),
                keyStorage = keyStorage.values,
                userAuthentication = userAuthentication.values,
                keyStorageStatusListUri = status.statusListUri,
                keyStorageStatusIndex = status.statusIndex,
                keyStorageStatusSubjectRef = status.statusSubjectRef,
                keyStorageStatusMaintenanceExpiresAtEpochSeconds = statusExpiry,
                attestedKeyCount = attestedKeyCount,
                evidence =
                    evidence +
                        mapOf(
                            "keyStorageSecurityLevel" to (keyStorage.securityLevel ?: ""),
                            "keyStorageSecureComponent" to (keyStorage.secureComponent ?: ""),
                            "userAuthenticationAssuranceLevel" to (userAuthentication.assuranceLevel ?: ""),
                        ).filterValues { it.isNotBlank() },
            ),
        )
    }

    private fun optionalEvidence(
        claims: JsonObject,
        evidence: Map<String, String>,
        attestedKeyCount: Int,
    ): VerifiedKeyAttestation? {
        val keyStorage = parseKeyStorage(claims["key_storage"], productionRequired = false).getOrNull()
        val userAuthentication = parseUserAuthentication(claims["user_authentication"], productionRequired = false).getOrNull()
        val status = parseStatusEvidence(claims["key_storage_status"], productionRequired = false).getOrNull()

        if (keyStorage == null && userAuthentication == null && status == null && evidence.isEmpty()) {
            return null
        }
        return VerifiedKeyAttestation(
            evidenceId = evidence.firstMatchingValue(EVIDENCE_ID_KEYS),
            walletUnitId = evidence.firstMatchingValue(WALLET_UNIT_ID_KEYS) ?: claims.primitiveString("sub"),
            walletAccountId = evidence.firstMatchingValue(WALLET_ACCOUNT_ID_KEYS),
            issuer = claims.primitiveString("iss"),
            subject = claims.primitiveString("sub"),
            jwtId = claims.primitiveString("jti"),
            expiresAtEpochSeconds = claims.primitiveLong("exp"),
            keyStorage = keyStorage?.values.orEmpty(),
            userAuthentication = userAuthentication?.values.orEmpty(),
            keyStorageStatusListUri = status?.statusListUri,
            keyStorageStatusIndex = status?.statusIndex,
            keyStorageStatusSubjectRef = status?.statusSubjectRef,
            keyStorageStatusMaintenanceExpiresAtEpochSeconds = status?.expiresAtEpochSeconds,
            attestedKeyCount = attestedKeyCount,
            evidence = evidence,
        )
    }

    private fun parseKeyStorage(
        element: JsonElement?,
        productionRequired: Boolean,
    ): IdkResult<KeyStorageEvidence, IdkError> {
        if (element == null) {
            return if (productionRequired) invalid("production key attestation requires key_storage evidence") else Ok(KeyStorageEvidence())
        }
        val obj = element as? JsonObject
        if (obj == null) {
            if (productionRequired) return invalid("production key attestation key_storage must be a TS03 object")
            return Ok(KeyStorageEvidence(values = stringValues(element), canonical = stringValues(element).map(::canonical).toSet()))
        }

        val securityLevel = obj.primitiveString("security_level") ?: obj.primitiveString("securityLevel")
        val secureComponent = obj.primitiveString("secure_component") ?: obj.primitiveString("secureComponent")
        val nonExportable = obj.primitiveBoolean("non_exportable") ?: obj.primitiveBoolean("nonExportable")
        if (productionRequired && securityLevel.isNullOrBlank()) {
            return invalid("production key attestation key_storage.security_level is missing")
        }
        if (productionRequired && secureComponent.isNullOrBlank()) {
            return invalid("production key attestation key_storage.secure_component is missing")
        }

        val values =
            listOfNotNull(
                securityLevel,
                secureComponent,
                nonExportable?.let { if (it) "non_exportable" else "exportable" },
            )
        return Ok(
            KeyStorageEvidence(
                values = values,
                canonical = values.map(::canonical).toSet(),
                securityLevel = securityLevel,
                secureComponent = secureComponent,
                secureComponentCanonical = secureComponent?.let(::canonical),
                nonExportable = nonExportable,
            ),
        )
    }

    private fun parseUserAuthentication(
        element: JsonElement?,
        productionRequired: Boolean,
    ): IdkResult<UserAuthenticationEvidence, IdkError> {
        if (element == null) {
            return if (productionRequired) invalid("production key attestation requires user_authentication evidence") else Ok(UserAuthenticationEvidence())
        }
        val obj = element as? JsonObject
        if (obj == null) {
            if (productionRequired) return invalid("production key attestation user_authentication must be a TS03 object")
            return Ok(
                UserAuthenticationEvidence(
                    values = stringValues(element),
                    canonical = stringValues(element).map(::canonical).toSet(),
                ),
            )
        }

        val assuranceLevel = obj.primitiveString("assurance_level") ?: obj.primitiveString("assuranceLevel")
        val methods = obj["methods"]?.let(::stringValues).orEmpty()
        val values = listOfNotNull(assuranceLevel) + methods
        return Ok(
            UserAuthenticationEvidence(
                values = values,
                canonical = values.map(::canonical).toSet(),
                assuranceLevel = assuranceLevel,
            ),
        )
    }

    private fun parseStatusEvidence(
        element: JsonElement?,
        productionRequired: Boolean,
    ): IdkResult<KeyStorageStatusEvidence, IdkError> {
        if (element == null) {
            return if (productionRequired) invalid("production key attestation requires key_storage_status evidence") else Ok(KeyStorageStatusEvidence())
        }
        val obj = element as? JsonObject
        if (obj == null) {
            if (productionRequired) return invalid("production key attestation key_storage_status must be a TS03 object")
            val ref = primitiveString(element)
            val parsedRef = ref?.let(::parseStatusReference)
            return Ok(
                KeyStorageStatusEvidence(
                    statusListUri = parsedRef?.first,
                    statusIndex = parsedRef?.second,
                    statusSubjectRef = parsedRef?.let { "${it.first}#${it.second}" },
                ),
            )
        }

        val statusValue = obj.primitiveString("status")
        val parsedStatusRef = statusValue?.let(::parseStatusReference)
        val explicitUri =
            obj.primitiveString("status_list_uri")
                ?: obj.primitiveString("statusListUri")
                ?: obj.primitiveString("uri")
        val explicitIndex =
            obj.primitiveString("status_index")
                ?: obj.primitiveString("statusIndex")
                ?: obj.primitiveString("index")
                ?: obj.primitiveString("idx")
        val expiresAt =
            obj.primitiveLong("exp")
                ?: obj.primitiveLong("maintenance_expires_at")
                ?: obj.primitiveLong("maintenanceExpiresAt")
        val statusState = obj.primitiveString("state")
        val revoked =
            obj.primitiveBoolean("revoked") == true ||
                canonical(statusState).let { it in REVOKED_STATUS_VALUES } ||
                (parsedStatusRef == null && canonical(statusValue).let { it in REVOKED_STATUS_VALUES })

        return Ok(
            KeyStorageStatusEvidence(
                statusListUri = parsedStatusRef?.first ?: explicitUri,
                statusIndex = parsedStatusRef?.second ?: explicitIndex,
                statusSubjectRef =
                    parsedStatusRef?.let { "${it.first}#${it.second}" }
                        ?: explicitUri?.takeIf { it.isNotBlank() }?.let { uri ->
                            explicitIndex?.takeIf { it.isNotBlank() }?.let { index -> "$uri#$index" }
                        },
                expiresAtEpochSeconds = expiresAt,
                revoked = revoked,
            ),
        )
    }

    private fun collectEvidence(claims: JsonObject): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val evidence = claims["evidence"] as? JsonObject
        if (evidence != null) {
            flattenEvidence("evidence", evidence, out)
        }
        TOP_LEVEL_EVIDENCE_KEYS.forEach { key ->
            claims.primitiveString(key)?.let { out[key] = it }
        }
        return out
    }

    private fun flattenEvidence(
        prefix: String,
        element: JsonElement,
        out: MutableMap<String, String>,
    ) {
        when (element) {
            is JsonObject -> {
                element.forEach { (key, value) -> flattenEvidence("$prefix.$key", value, out) }
            }

            is JsonArray -> {
                element.forEachIndexed { index, value ->
                    if (value is JsonPrimitive) flattenEvidence("$prefix[$index]", value, out)
                }
            }

            is JsonPrimitive -> {
                primitiveString(element)?.let { out[prefix] = it }
            }
        }
    }

    private fun hasX5c(header: JsonObject): Boolean {
        val x5c = header["x5c"] ?: return false
        return when (x5c) {
            is JsonArray -> x5c.any { primitiveString(it)?.isNotBlank() == true }
            is JsonPrimitive -> primitiveString(x5c)?.isNotBlank() == true
            else -> false
        }
    }

    private fun hasStructuredCertification(element: JsonElement?): Boolean =
        when (element) {
            is JsonObject -> element.isNotEmpty()
            is JsonArray -> element.isNotEmpty()
            is JsonPrimitive -> primitiveString(element)?.isNotBlank() == true
            else -> false
        }

    private fun hasLocalOrTestEvidence(
        claims: JsonObject,
        evidence: Map<String, String>,
    ): Boolean {
        if (evidence.any { (key, value) -> isUntrustedBoolean(key, value) || isTestMarker(value) }) {
            return true
        }
        val keyStorage = claims["key_storage"] as? JsonObject
        val secureComponent = keyStorage?.primitiveString("secure_component") ?: keyStorage?.primitiveString("secureComponent")
        if (secureComponent != null && canonical(secureComponent) in UNSUPPORTED_KEY_STORAGE_VALUES) {
            return true
        }
        val userAuthentication = claims["user_authentication"] as? JsonObject
        val assuranceLevel =
            userAuthentication?.primitiveString("assurance_level")
                ?: userAuthentication?.primitiveString("assuranceLevel")
        return assuranceLevel != null && canonical(assuranceLevel) in UNSUPPORTED_USER_AUTH_VALUES
    }

    private fun hasRevokedEvidence(
        claims: JsonObject,
        evidence: Map<String, String>,
    ): Boolean {
        val statusObj = claims["key_storage_status"] as? JsonObject
        val statusValue = statusObj?.primitiveString("status")
        val stateValue = statusObj?.primitiveString("state")
        return statusObj?.primitiveBoolean("revoked") == true ||
            canonical(stateValue).let { it in REVOKED_STATUS_VALUES } ||
            (statusValue?.let(::parseStatusReference) == null && canonical(statusValue).let { it in REVOKED_STATUS_VALUES }) ||
            evidence.any { (key, value) ->
                (canonical(key).contains("revoked") && canonical(value) == "true") ||
                    (canonical(key).contains("status") && canonical(value) in REVOKED_STATUS_VALUES)
            }
    }

    private fun isUntrustedBoolean(
        key: String,
        value: String,
    ): Boolean {
        val canonicalKey = canonical(key)
        val canonicalValue = canonical(value)
        return canonicalValue == "false" &&
            (canonicalKey.endsWith("trusted") || canonicalKey.endsWith("production") || canonicalKey.endsWith("ts03_conformant"))
    }

    private fun isTestMarker(value: String): Boolean {
        val canonical = canonical(value)
        return canonical in TEST_EVIDENCE_MARKERS ||
            TEST_EVIDENCE_MARKERS.any { marker -> canonical.contains(marker) }
    }

    private fun stringValues(element: JsonElement): List<String> =
        when (element) {
            is JsonArray -> element.mapNotNull(::primitiveString).filter { it.isNotBlank() }
            is JsonPrimitive -> primitiveString(element)?.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
            is JsonObject -> emptyList()
            else -> emptyList()
        }

    private fun parseStatusReference(status: String): Pair<String, String>? {
        val uri = status.substringBeforeLast('#', missingDelimiterValue = "")
        val index = status.substringAfterLast('#', missingDelimiterValue = "")
        if (uri.isBlank() || index.isBlank() || uri == status) return null
        return uri to index
    }

    private fun Map<String, String>.firstMatchingValue(keys: Set<String>): String? =
        entries
            .firstOrNull { (key, value) ->
                val normalized = canonical(key)
                value.isNotBlank() && keys.any { expected -> normalized == expected || normalized.endsWith("_$expected") }
            }?.value

    private fun JsonObject.primitiveString(key: String): String? = primitiveString(this[key])

    private fun JsonObject.primitiveBoolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.primitiveLong(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun primitiveString(element: JsonElement?): String? = (element as? JsonPrimitive)?.contentOrNull

    private fun primitiveLong(element: JsonElement?): Long? = (element as? JsonPrimitive)?.longOrNull

    private fun canonical(value: String?): String =
        value
            ?.trim()
            ?.lowercase()
            ?.replace('-', '_')
            ?.replace('.', '_')
            ?.replace(' ', '_')
            .orEmpty()

    private fun invalid(message: String): IdkResult<Nothing, IdkError> = Err(IdkError.fromString(code = Oid4vciErrors.INVALID_PROOF, message = message))

    private data class KeyStorageEvidence(
        val values: List<String> = emptyList(),
        val canonical: Set<String> = emptySet(),
        val securityLevel: String? = null,
        val secureComponent: String? = null,
        val secureComponentCanonical: String? = null,
        val nonExportable: Boolean? = null,
    )

    private data class UserAuthenticationEvidence(
        val values: List<String> = emptyList(),
        val canonical: Set<String> = emptySet(),
        val assuranceLevel: String? = null,
    )

    private data class KeyStorageStatusEvidence(
        val statusListUri: String? = null,
        val statusIndex: String? = null,
        val statusSubjectRef: String? = null,
        val expiresAtEpochSeconds: Long? = null,
        val revoked: Boolean = false,
    )

    private companion object {
        val SUPPORTED_PRODUCTION_SECURE_COMPONENTS = setOf("remote_wsca", "remote_wscd")
        val UNSUPPORTED_KEY_STORAGE_VALUES =
            setOf(
                "none",
                "local_test",
                "local_test_reference",
                "local_secure_area",
                "local_kms",
                "webauthn_prf",
                "webauthn_prf_wscd",
                "stored_reference",
                "backendless",
                "test",
            )
        val UNSUPPORTED_USER_AUTH_VALUES =
            setOf(
                "none",
                "local",
                "local_test",
                "app_pin_test_profile",
                "pin_test",
                "test",
            )
        val TEST_EVIDENCE_MARKERS =
            setOf(
                "local_test",
                "local_test_reference",
                "webauthn_prf",
                "webauthn_prf_wscd",
                "backendless",
                "stored_reference",
            )
        val REVOKED_STATUS_VALUES = setOf("revoked", "invalid", "suspended")
        val TOP_LEVEL_EVIDENCE_KEYS =
            setOf(
                "attestation_ref",
                "attestationRef",
                "key_attestation_ref",
                "keyAttestationRef",
                "evidence_id",
                "evidenceId",
                "wallet_unit_id",
                "walletUnitId",
                "wallet_account_id",
                "walletAccountId",
                "profile",
                "attestation_profile",
                "attestationProfile",
                "wallet_unit_profile",
                "walletUnitProfile",
                "signer_profile",
                "signerProfile",
                "trusted",
                "production",
                "ts03_conformant",
                "ts03Conformant",
            )
        val EVIDENCE_ID_KEYS = setOf("attestation_ref", "attestationref", "key_attestation_ref", "keyattestationref", "evidence_id", "evidenceid")
        val WALLET_UNIT_ID_KEYS = setOf("wallet_unit_id", "walletunitid")
        val WALLET_ACCOUNT_ID_KEYS = setOf("wallet_account_id", "walletaccountid")
    }
}
