/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.identity.idv.model

import com.sphereon.data.store.party.model.PartyType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IdvMethodType(val value: String) {
    companion object {
        val OIDC = IdvMethodType("oidc")
        val OTP = IdvMethodType("otp")
        val BIOMETRIC = IdvMethodType("biometric")
        val DOCUMENT = IdvMethodType("document")
        val WALLET = IdvMethodType("wallet")
        val REST_API = IdvMethodType("rest_api")
        val CLAIM_MATCH = IdvMethodType("claim_match")
    }
}

@Serializable
data class IdvMethodId(val value: String)

@Serializable
data class IdvUseCaseId(val value: String)

@Serializable
data class IdvExecutionId(val value: String)

@Serializable
data class IdvNodeId(val value: String)

@Serializable
data class IdvGroupId(val value: String)

@Serializable
data class OtpChannel(val value: String) {
    companion object {
        val EMAIL = OtpChannel("email")
        val SMS = OtpChannel("sms")
        val AUTHENTICATOR_APP = OtpChannel("authenticator_app")
    }
}

@Serializable
data class BiometricProviderType(val value: String) {
    companion object {
        val IPROOV = BiometricProviderType("iproov")
        val ONFIDO = BiometricProviderType("onfido")
        val JUMIO = BiometricProviderType("jumio")
        val CUSTOM = BiometricProviderType("custom")
    }
}

@Serializable
data class DocumentProviderType(val value: String) {
    companion object {
        val ONFIDO = DocumentProviderType("onfido")
        val JUMIO = DocumentProviderType("jumio")
        val READID = DocumentProviderType("readid")
        val CUSTOM = DocumentProviderType("custom")
    }
}

@Serializable
data class DocumentType(val value: String) {
    companion object {
        val PASSPORT = DocumentType("passport")
        val ID_CARD = DocumentType("id_card")
        val DRIVING_LICENSE = DocumentType("driving_license")
        val RESIDENCE_PERMIT = DocumentType("residence_permit")
    }
}

@Serializable
enum class EidasAssuranceLevel(val serializedValue: String) {
    @SerialName("low")
    LOW("low"),

    @SerialName("substantial")
    SUBSTANTIAL("substantial"),

    @SerialName("high")
    HIGH("high"),
}

@Serializable
enum class AuthAssuranceLevel(val serializedValue: String) {
    @SerialName("aal1")
    AAL1("aal1"),

    @SerialName("aal2")
    AAL2("aal2"),

    @SerialName("aal3")
    AAL3("aal3"),
}

@Serializable
data class AuthMethodReference(val value: String) {
    companion object {
        val MFA = AuthMethodReference("mfa")
        val OTP = AuthMethodReference("otp")
        val SMS = AuthMethodReference("sms")
        val FACE = AuthMethodReference("face")
        val FPT = AuthMethodReference("fpt")
        val HWK = AuthMethodReference("hwk")
        val SWK = AuthMethodReference("swk")
        val SC = AuthMethodReference("sc")
        val PIN = AuthMethodReference("pin")
        val PWD = AuthMethodReference("pwd")
    }
}

@Serializable
data class EvidenceStrength(val value: String) {
    companion object {
        val FAIR = EvidenceStrength("fair")
        val STRONG = EvidenceStrength("strong")
        val SUPERIOR = EvidenceStrength("superior")
    }
}

@Serializable
data class ProofingScenario(val value: String) {
    companion object {
        val FACE_TO_FACE = ProofingScenario("face_to_face")
        val REMOTE_ASSISTED = ProofingScenario("remote_assisted")
        val UNATTENDED_REMOTE = ProofingScenario("unattended_remote")
    }
}

@Serializable
data class TrustFrameworkType(val value: String) {
    companion object {
        val EIDAS = TrustFrameworkType("eidas")
        val NIST_800_63A = TrustFrameworkType("nist_800_63A")
        val UK_DIATF = TrustFrameworkType("uk_diatf")
        val DE_AML = TrustFrameworkType("de_aml")
    }
}

@Serializable
data class RestApiAuthType(val value: String) {
    companion object {
        val API_KEY = RestApiAuthType("api_key")
        val OAUTH2_CLIENT_CREDENTIALS = RestApiAuthType("oauth2_client_credentials")
        val MTLS = RestApiAuthType("mtls")
        val BASIC = RestApiAuthType("basic")
        val NONE = RestApiAuthType("none")
    }
}

@Serializable
data class IdvEntryPointType(val value: String) {
    companion object {
        val WALLET_OID4VP = IdvEntryPointType("wallet_oid4vp")
        val FEDERATED_OIDC = IdvEntryPointType("federated_oidc")
        val MANUAL_FORM = IdvEntryPointType("manual_form")
        val API_CALL = IdvEntryPointType("api_call")
    }
}

@Serializable
data class IdvTriggerType(val value: String) {
    companion object {
        val ONBOARDING = IdvTriggerType("onboarding")
        val STEP_UP = IdvTriggerType("step_up")
        val REVALIDATION = IdvTriggerType("revalidation")
        val REGISTRATION = IdvTriggerType("registration")
    }
}

@Serializable
data class IdvEvidenceType(val value: String) {
    companion object {
        val DOCUMENT = IdvEvidenceType("document")
        val ELECTRONIC_RECORD = IdvEvidenceType("electronic_record")
        val VOUCH = IdvEvidenceType("vouch")
        val BIOMETRIC = IdvEvidenceType("biometric")
    }
}

@Serializable
data class IdvProviderType(val value: String)

@Serializable
data class AttributePath(val value: String)

@Serializable
data class InputFieldId(val value: String)

@Serializable
data class ConfigReference(
    val key: String,
    val required: Boolean = true,
)

@Serializable
data class SecretReference(
    val providerId: String? = null,
    val path: String,
    val key: String? = null,
)

@Serializable
data class LegalBasis(val value: String) {
    companion object {
        val GDPR_ART6_1A_CONSENT = LegalBasis("gdpr_art6_1a_consent")
        val GDPR_ART6_1B_CONTRACT = LegalBasis("gdpr_art6_1b_contract")
        val GDPR_ART6_1C_LEGAL_OBLIGATION = LegalBasis("gdpr_art6_1c_legal_obligation")
        val GDPR_ART6_1F_LEGITIMATE_INTEREST = LegalBasis("gdpr_art6_1f_legitimate_interest")
        val AMLR_ART56_RECORD_KEEPING = LegalBasis("amlr_art56_record_keeping")
    }
}

@Serializable
data class EtsiLoip(val value: String) {
    companion object {
        val FACE_TO_FACE = EtsiLoip("face_to_face")
        val REMOTE_ASSISTED = EtsiLoip("remote_assisted")
        val UNATTENDED_REMOTE = EtsiLoip("unattended_remote")
    }
}

@Serializable
enum class IdvExecutionStatus {
    CREATED,
    RUNNING,
    AWAITING_ACTION,
    COMPLETED,
    FAILED,
    EXPIRED,
    CANCELLED,
}

@Serializable
enum class IdvNodeStatus {
    PENDING,
    READY,
    DISPATCHED,
    AWAITING_INPUT,
    AWAITING_CALLBACK,
    AWAITING_POLL,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED,
}

@Serializable
enum class IdvMethodScope {
    APP,
    TENANT,
}

@Serializable
enum class IdentityAssociationType {
    NATURAL_PERSON,
    ORGANIZATION,
    CONTACT_ONLY,
    NONE,
    ;

    fun toPartyType(): PartyType? = when (this) {
        NATURAL_PERSON -> PartyType.NATURAL_PERSON
        ORGANIZATION -> PartyType.ORGANIZATION
        CONTACT_ONLY, NONE -> null
    }
}

@Serializable
enum class ConsentType {
    BIOMETRIC_PROCESSING,
    DATA_SHARING,
    CROSS_BORDER_TRANSFER,
}

@Serializable
enum class CddLevel {
    SDD,
    CDD,
    EDD,
}

@Serializable
enum class MatchOperator {
    EQUALS,
    CONTAINS,
    EXISTS,
    NOT_EMPTY,
    REGEX,
}
