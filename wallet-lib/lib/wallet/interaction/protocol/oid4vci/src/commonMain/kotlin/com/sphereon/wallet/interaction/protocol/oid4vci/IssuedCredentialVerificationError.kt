/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.error.IdkError

internal enum class IssuedCredentialVerificationReason(val diagnosticCode: String) {
    ISSUER_KEYS_MISSING("issuer_keys_missing"),
    ISSUER_AUTHENTICATION_REFUSED("issuer_authentication_refused"),
    ISSUER_AUTHENTICATION_MISMATCH("issuer_authentication_mismatch"),
    JWS_COMMAND_MISSING("jws_command_missing"),
    CREDENTIAL_PROFILE_MISMATCH("credential_profile_mismatch"),
    JWS_COMMAND_FAILED("jws_command_failed"),
    JWS_INVALID("jws_invalid"),
    JWS_UNTRUSTED("jws_untrusted"),
    JWS_CRYPTO_UNVERIFIED("jws_crypto_unverified"),
    CREDENTIAL_ISSUER_MISMATCH("credential_issuer_mismatch"),
    SD_JWT_VERIFICATION_FAILED("sd_jwt_verification_failed"),
    DATA_INTEGRITY_VERIFIER_MISSING("data_integrity_verifier_missing"),
    LDP_DOCUMENT_MALFORMED("ldp_document_malformed"),
    LDP_PROFILE_UNSUPPORTED("ldp_profile_unsupported"),
    LDP_NOT_CREDENTIAL("ldp_not_credential"),
    LDP_ISSUER_MISSING("ldp_issuer_missing"),
    LDP_ISSUER_MISMATCH("ldp_issuer_mismatch"),
    DATA_INTEGRITY_VERIFICATION_FAILED("data_integrity_verification_failed"),
}

/** Retains a machine-readable refusal and provider cause without logging credential contents. */
internal class IssuedCredentialVerificationError(
    val reason: IssuedCredentialVerificationReason,
    credentialConfigurationId: String,
    description: String,
    providerError: IdkError? = null,
) : IdkError(
    code = "ISSUED_CREDENTIAL_VERIFICATION_FAILED",
    message = IdkError.fromString("Refusing to store issued credential '$credentialConfigurationId': $description").message,
    causes = listOfNotNull(providerError),
    meta = mapOf("verificationReason" to reason.diagnosticCode),
)
