/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.provider.local

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.provider.RevocationReason
import com.sphereon.wallet.provider.UnitProvisioningRequest
import com.sphereon.wallet.provider.WalletProvider
import com.sphereon.wallet.provider.WalletUnitDescriptor
import com.sphereon.wallet.provider.WalletUnitStatus
import com.sphereon.wallet.party.WalletBusinessUnitProvisioningRequest
import com.sphereon.wallet.party.WalletPartyDirectory
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletAttestedKeyRef
import com.sphereon.wallet.unit.WalletProviderAttestationSignerRef
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueRequest
import com.sphereon.wallet.unit.attestation.KeyAttestationIssueResult
import com.sphereon.wallet.unit.attestation.Ts03StatusClaim
import com.sphereon.wallet.unit.attestation.Ts03WalletAttestationEncoder
import com.sphereon.wallet.unit.attestation.Ts03WalletInstanceAttestationClaims
import com.sphereon.wallet.unit.attestation.WalletAttestationArtifact
import com.sphereon.wallet.unit.attestation.WalletAttestationArtifactMetadata
import com.sphereon.wallet.unit.attestation.WalletAttestationSigner
import com.sphereon.wallet.unit.attestation.WalletAttestationSignerProfile
import com.sphereon.wallet.unit.attestation.WalletAttestationSigningAlgorithm
import com.sphereon.wallet.unit.attestation.WalletAttestationSigningRequest
import com.sphereon.wallet.unit.attestation.WalletAttestationSigningResult
import com.sphereon.wallet.unit.attestation.WalletInstanceAttestationIssueRequest
import com.sphereon.wallet.unit.attestation.WalletInstanceAttestationIssueResult
import com.sphereon.wallet.unit.attestation.WalletUnitAttestationEvidence
import com.sphereon.wallet.unit.attestation.WalletUnitAttestationFormat
import com.sphereon.wallet.unit.attestation.WalletUnitAttestationKind
import com.sphereon.wallet.unit.attestation.WalletUnitAttestationMaterial
import com.sphereon.wallet.unit.attestation.WalletUnitAttestationProfile
import com.sphereon.wallet.wsca.Wsca
import com.sphereon.wallet.wscd.WscdProfile
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * OSS default [WalletProvider]: provisions wallet units locally and self-signs TS03 WIA/KA
 * attestations using only IDK modules (OSS-complete D4).
 *
 * Every artifact this provider issues is signed by ITS OWN Wsca-held key, resolved deterministically
 * from [LocalWalletProviderConfig.providerId] (never from a caller-supplied `signer` ref - see
 * [providerSignerKey] and [defaultSignerRef]): a request's `signer`/`expectedWalletInstanceId`
 * fields are read for VALIDATION only, never to redirect who actually signs or what `iss`/`sub`
 * claims. This is a deliberate anti-spoofing choice: nothing routed through this port can make the
 * provider sign as a different issuer than itself.
 *
 * [issueKeyAttestation] delegates claim assembly entirely to [Wsca.attestKeys] (it derives honest
 * key_storage/user_authentication claims from the injected Wscd's [WscdProfile]); this class only
 * resolves the provider's own signer ref before delegating. [issueInstanceAttestation] has no
 * Wsca-level equivalent, so it assembles the WIA itself using the SAME encoder EDK's
 * `StoredWalletUnitAttestationService` uses ([Ts03WalletAttestationEncoder], IDK-local in
 * `lib-wallet-unit-public`) via a small [WalletAttestationSigner] adapter
 * ([WscaBackedWalletAttestationSigner]) that signs through [Wsca.sign].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletProvider>())
class LocalWalletProvider(
    private val wsca: Wsca,
    private val config: LocalWalletProviderConfig,
    private val unitStore: WalletUnitRecordStore,
    private val partyDirectory: WalletPartyDirectory,
    private val execution: SessionExecution,
) : WalletProvider {
    override suspend fun provisionUnit(request: UnitProvisioningRequest): IdkResult<WalletUnitDescriptor, IdkError> {
        val profileId =
            request.profileId.takeIf { it.isNotBlank() }
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "profileId must not be blank"))
        // Mirrors the MANAGED convention (WalletUnitProfileProvisioner, EDK enterprise-wallet):
        // wu-/wi- prefixed ids derived from profileId. Both ids are pure functions of profileId, so
        // re-provisioning the same profileId always retraces the same (unit, instance) pair - no
        // conflict case to guard against, unlike the MANAGED saga's retry-suffix scheme (which exists
        // only because ITS ids can also be caller-supplied).
        val walletUnitId = localWalletUnitId(profileId)
        val walletInstanceId = localWalletInstanceId(profileId)
        val now = Clock.System.now()
        val existing = unitStore.get(walletUnitId).getOrElse { return Err(it) }
        // Revocation is terminal for this unit id: re-provisioning must never silently resurrect
        // a revoked unit (a COMPROMISE revocation would be wiped by any redundant install call).
        if (existing?.status == WalletUnitStatus.REVOKED) {
            return Err(
                IdkError.fromString(
                    code = "WALLET_PROVIDER_UNIT_REVOKED",
                    message =
                        "Wallet unit '$walletUnitId' was revoked (${existing.revocationReason}) and cannot be re-provisioned " +
                            "under the same profileId",
                ),
            )
        }
        val businessUnit =
            partyDirectory
                .provisionBusinessUnit(
                    WalletBusinessUnitProvisioningRequest(
                        tenantId = execution.tenantId,
                        walletUnitId = walletUnitId,
                        displayName = request.businessUnitDisplayName,
                        assignedOrganizationUnitRef = existing?.organizationUnitRef,
                    ),
                ).getOrElse { return Err(it) }
        val record =
            WalletUnitRecord(
                walletUnitId = walletUnitId,
                walletInstanceId = walletInstanceId,
                profileId = profileId,
                organizationUnitRef = businessUnit.ref,
                wscdProfile = request.wscdProfile,
                status = WalletUnitStatus.ACTIVE,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                revocationReason = null,
            )
        val saved = unitStore.put(record).getOrElse { return Err(it) }
        return Ok(
            WalletUnitDescriptor(
                walletUnitId = saved.walletUnitId,
                walletInstanceId = saved.walletInstanceId,
                organizationUnitRef = saved.organizationUnitRef,
                wscdProfile = saved.wscdProfile,
                status = saved.status,
            ),
        )
    }

    override suspend fun issueInstanceAttestation(
        request: WalletInstanceAttestationIssueRequest,
    ): IdkResult<WalletInstanceAttestationIssueResult, IdkError> {
        if (request.audience.isBlank()) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WIA audience must not be blank"))
        val unit = requireActiveUnit(request.walletUnitId).getOrElse { return Err(it) }
        val instanceId = resolveAuthoritativeInstanceId(unit, request.expectedWalletInstanceId).getOrElse { return Err(it) }
        val now = Clock.System.now()
        if (request.expiresAt <= now) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WIA expiresAt must be in the future"))
        // Same TS03 technical-TTL ceiling EDK's ts03WiaArtifact enforces - ts03Conformant = true
        // below must stay honest even for a self-signed artifact.
        if (request.expiresAt - now >= 24.hours) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "WIA expiresAt must be under 24 hours from now"))

        val algorithm = config.providerKeyAlgorithm
        val signingAlgorithm = toWalletAttestationSigningAlgorithm(algorithm)
        val keyAlias = providerKeyAlias(config.providerId, signingAlgorithm)
        val signerKey = providerSignerKey(algorithm, keyAlias).getOrElse { return Err(it) }
        val signerRef = defaultSignerRef(keyAlias, signingAlgorithm)

        val certification = request.walletSolution.certification
        val statusSubject = request.statusSubject
        val claims =
            Ts03WalletInstanceAttestationClaims(
                iss = config.providerId,
                sub = instanceId,
                aud = request.audience,
                iat = now.epochSeconds,
                exp = request.expiresAt.epochSeconds,
                jti = "wia-jti:${request.walletUnitId}:${Uuid.v4String()}",
                walletName = request.walletSolution.name.ifBlank { config.walletName },
                walletVersion = request.walletSolution.version.ifBlank { config.walletVersion },
                walletLink = request.walletSolution.informationLink,
                // OSS self-attestation has no certification authority behind it (unlike EDK's TS03
                // path, which fails closed without WalletSolutionCertificationEvidence): a self-
                // describing sentinel is honest here, an invented certification would not be.
                walletSolutionCertificationInformation =
                    certification?.let {
                        mapOf(
                            "certification_id" to it.certificationId,
                            "scheme" to it.scheme,
                            "assurance_level" to it.assuranceLevel,
                            "issuer" to it.issuer,
                        )
                    } ?: mapOf("profile" to "local-self-attested"),
                // D5: no status-list publishing. Absent a caller-supplied statusSubject, this mirrors
                // LocalWsca.attestKeys' own synthetic-URN fallback for keyStorageStatus rather than
                // requiring one (EDK's TS03 path fails closed without one; that requirement is
                // EDK-orchestration policy, not a TS03 wire-format necessity).
                clientStatus =
                    statusSubject?.let { Ts03StatusClaim(status = "${it.statusListUri}#${it.index}", exp = request.expiresAt.epochSeconds) }
                        ?: Ts03StatusClaim(status = "urn:wallet-unit:wia-status:${request.walletUnitId}#0", exp = request.expiresAt.epochSeconds),
                cnf = request.proofBinding,
                evidence = request.evidence,
            )

        val encoded =
            Ts03WalletAttestationEncoder().encodeWalletInstanceAttestation(
                claims = claims,
                signer = WscaBackedWalletAttestationSigner(wsca, config.providerId, signerKey, request.operationBinding),
                signingRequest =
                    WalletAttestationSigningRequest(
                        algorithm = signingAlgorithm,
                        signerProfile = walletAttestationSignerProfile(wsca.wscdProfile),
                        signerId = config.providerId,
                        signingInput = ByteArray(0),
                        // Self-signed artifact: never embed a caller-supplied certificate chain.
                        // The JWS is signed by the provider's own JWK-anchored key; a foreign x5c
                        // in the header would only decorate the artifact with a chain the
                        // signature cannot verify against.
                        x5c = emptyList(),
                        keyId = signerKey.keyId,
                    ),
            ).getOrElse { return Err(it) }

        val artifact =
            WalletAttestationArtifact(
                kind = WalletUnitAttestationKind.WIA,
                profile = WalletUnitAttestationProfile.TS03_JWT,
                material = WalletUnitAttestationMaterial(WalletUnitAttestationFormat.JWT, encoded.compact),
                issuedAt = now,
                expiresAt = request.expiresAt,
                // D5: stays null unless the caller supplied a real statusSubject - the result's
                // statusRef (below) derives from this and is therefore null in the common local case.
                statusSubject = statusSubject,
                evidence =
                    WalletUnitAttestationEvidence(
                        profile = WalletUnitAttestationProfile.TS03_JWT,
                        // TS03-shaped and claim-complete, but JWK-self-anchored (no x5c chain):
                        // validators using Ts03WalletAttestationValidationPolicy.walletProviderTrustList()
                        // (requireX5c = true) will reject it by design; localEvaluation() accepts it.
                        // The flag alone does not distinguish PKI-anchored from JWK-self-anchored.
                        ts03Conformant = true,
                        signer = signerRef,
                        walletSolution = request.walletSolution,
                        secureApplication = request.secureApplication,
                        secureDevice = request.secureDevice,
                        keystore = request.keystore,
                        custom = request.evidence,
                    ),
                metadata =
                    WalletAttestationArtifactMetadata(
                        artifactHash = encoded.artifactHash,
                        statusSubject = statusSubject,
                        signingEvidence = encoded.signingEvidence,
                        claimSummary = mapOf("kind" to "WIA", "alg" to signingAlgorithm.jwtAlg),
                    ),
            )

        return Ok(
            WalletInstanceAttestationIssueResult(
                walletUnitId = request.walletUnitId,
                walletAccountId = request.walletAccountId,
                attestationRef = encoded.artifactHash,
                artifact = artifact,
                issuedAt = now,
                expiresAt = request.expiresAt,
            ),
        )
    }

    override suspend fun issueKeyAttestation(request: KeyAttestationIssueRequest): IdkResult<KeyAttestationIssueResult, IdkError> {
        requireActiveUnit(request.walletUnitId).getOrElse { return Err(it) }
        val algorithm = config.providerKeyAlgorithm
        val signingAlgorithm = toWalletAttestationSigningAlgorithm(algorithm)
        val keyAlias = providerKeyAlias(config.providerId, signingAlgorithm)
        // Always signs as itself (see the class KDoc): any caller-supplied request.signer is
        // replaced, never merely defaulted, so KA and WIA share the exact same anti-spoofing
        // policy. Wsca.attestKeys internally does
        // ensureKey(walletUnitId = request.walletUnitId, keyAlias = signer.signerId); passing the
        // SAME keyAlias used by issueInstanceAttestation's providerSignerKey means both artifact
        // kinds resolve to the identical Wsca-held key (ensureKey is alias-keyed, not
        // walletUnitId-keyed, when a non-blank alias is supplied - see Wsca.ensureKey KDoc) and are
        // therefore both verifiable against the same trustAnchor().
        return wsca.attestKeys(request.copy(signer = defaultSignerRef(keyAlias, signingAlgorithm)))
    }

    override suspend fun unitStatus(walletUnitId: String): IdkResult<WalletUnitStatus, IdkError> {
        val unit =
            unitStore.get(walletUnitId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Wallet unit '$walletUnitId'"))
        return Ok(unit.status)
    }

    override suspend fun revokeUnit(
        walletUnitId: String,
        reason: RevocationReason,
    ): IdkResult<Unit, IdkError> {
        val unit =
            unitStore.get(walletUnitId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Wallet unit '$walletUnitId'"))
        // Idempotent: revoking an already-revoked unit is a no-op success, not an error.
        if (unit.status == WalletUnitStatus.REVOKED) return Ok(Unit)
        unitStore.put(
            unit.copy(status = WalletUnitStatus.REVOKED, revocationReason = reason, updatedAt = Clock.System.now()),
        ).getOrElse { return Err(it) }
        return Ok(Unit)
    }

    /**
     * The provider's own public key, so verifiers/tests can pin it (spec requirement 6). Resolves
     * through the exact same [Wsca.ensureKey] call [issueInstanceAttestation] and (indirectly, via
     * [Wsca.attestKeys]) [issueKeyAttestation] use, so this is always the key that actually signed
     * both artifact kinds for THIS provider identity.
     */
    suspend fun trustAnchor(): IdkResult<Jwk, IdkError> {
        val algorithm = config.providerKeyAlgorithm
        val signingAlgorithm = toWalletAttestationSigningAlgorithm(algorithm)
        val keyAlias = providerKeyAlias(config.providerId, signingAlgorithm)
        val signerKey = providerSignerKey(algorithm, keyAlias).getOrElse { return Err(it) }
        val jwkJson =
            signerKey.publicKeyJwk
                ?: return Err(
                    IdkError.fromString(
                        code = "WALLET_PROVIDER_KEY_MISSING_PUBLIC_JWK",
                        category = ErrorCategory.INTERNAL,
                        message = "Provider key '$keyAlias' does not carry a public JWK",
                    ),
                )
        return try {
            Ok(json.decodeFromString(Jwk.serializer(), jwkJson))
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    code = "WALLET_PROVIDER_KEY_INVALID_PUBLIC_JWK",
                    category = ErrorCategory.INTERNAL,
                    message = "Provider key '$keyAlias' has an invalid public JWK: ${expected.message}",
                ),
            )
        }
    }

    /**
     * Resolves (provisioning it on first use) this provider's OWN signing key: [SecureComponentUsage.WALLET_ATTESTATION]
     * scoped by [LocalWalletProviderConfig.providerId], with the explicit [keyAlias] the caller
     * owns end to end (never left to Wsca's per-walletUnitId default derivation), matching the
     * "provider key is generated on first use through Wsca.ensureKey" spec requirement.
     */
    private suspend fun providerSignerKey(
        algorithm: SignatureAlgorithm,
        keyAlias: String,
    ): IdkResult<WalletAttestedKeyRef, IdkError> =
        wsca.ensureKey(
            walletUnitId = config.providerId,
            usage = SecureComponentUsage.WALLET_ATTESTATION,
            algorithm = algorithm,
            keyAlias = keyAlias,
        )

    private suspend fun requireActiveUnit(walletUnitId: String): IdkResult<WalletUnitRecord, IdkError> {
        val unit =
            unitStore.get(walletUnitId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "Wallet unit '$walletUnitId'"))
        if (unit.status != WalletUnitStatus.ACTIVE) {
            return Err(
                IdkError.fromString(
                    code = "WALLET_PROVIDER_UNIT_NOT_ACTIVE",
                    category = ErrorCategory.PRECONDITION_FAILED,
                    message = "Wallet unit '$walletUnitId' is ${unit.status}; this provider cannot issue attestations for it",
                ),
            )
        }
        return Ok(unit)
    }

    /**
     * D10-equivalent authoritative-resolution semantics (mirroring EDK's
     * `StoredWalletUnitAttestationService.resolveAuthoritativeInstanceId`): the WIA `sub` always
     * comes from the PERSISTED record, never a caller-supplied value, and issuance fails closed
     * when a caller-supplied [expectedWalletInstanceId] disagrees with it. Reuses EDK's own
     * `WALLET_UNIT_INSTANCE_MISMATCH` code verbatim (identical semantics deserve identical codes);
     * EDK's sibling `WALLET_UNIT_INSTANCE_MISSING` (pre-D10 legacy records with no instance yet)
     * has no equivalent here, since every record [provisionUnit] creates already carries one.
     */
    private fun resolveAuthoritativeInstanceId(
        unit: WalletUnitRecord,
        expectedWalletInstanceId: String?,
    ): IdkResult<String, IdkError> {
        if (expectedWalletInstanceId != null && expectedWalletInstanceId != unit.walletInstanceId) {
            return Err(
                IdkError.fromString(
                    code = "WALLET_UNIT_INSTANCE_MISMATCH",
                    category = ErrorCategory.PRECONDITION_FAILED,
                    message =
                        "Wallet unit '${unit.walletUnitId}' is bound to instance '${unit.walletInstanceId}', " +
                            "but caller expected instance '$expectedWalletInstanceId'",
                ),
            )
        }
        return Ok(unit.walletInstanceId)
    }

    /** `wallet-units/{providerId}/provider/{alg}` - the established key-alias convention, scoped to this provider's own identity rather than a customer wallet unit. */
    private fun providerKeyAlias(
        providerId: String,
        algorithm: WalletAttestationSigningAlgorithm,
    ): String = "wallet-units/$providerId/provider/${algorithm.jwtAlg.lowercase()}"

    private fun defaultSignerRef(
        keyAlias: String,
        algorithm: WalletAttestationSigningAlgorithm,
    ): WalletProviderAttestationSignerRef =
        WalletProviderAttestationSignerRef(
            signerId = keyAlias,
            issuer = config.providerId,
            signingAlgorithm = algorithm.jwtAlg,
            signerProfile = walletAttestationSignerProfile(wsca.wscdProfile).name,
            certificateChain = emptyList(),
        )

    private fun walletAttestationSignerProfile(profile: WscdProfile): WalletAttestationSignerProfile =
        when (profile) {
            WscdProfile.Remote -> WalletAttestationSignerProfile.REMOTE_WSCD
            WscdProfile.LocalExternal -> WalletAttestationSignerProfile.EXTERNAL_PROVIDER
            WscdProfile.LocalInternal, WscdProfile.LocalNative, WscdProfile.Software -> WalletAttestationSignerProfile.LOCAL_WSCD
        }

    /**
     * Only the TS03-allowed ECDSA variants matter for this provider's own signing key (see
     * [LocalWalletProviderConfig.providerKeyAlgorithm] KDoc); any other [SignatureAlgorithm] falls
     * back to ES256, mirroring `LocalWsca`'s own private `joseAlgorithm` fallback style.
     */
    private fun toWalletAttestationSigningAlgorithm(algorithm: SignatureAlgorithm): WalletAttestationSigningAlgorithm =
        when (algorithm) {
            SignatureAlgorithm.ECDSA_SHA384 -> WalletAttestationSigningAlgorithm.ES384
            SignatureAlgorithm.ECDSA_SHA512 -> WalletAttestationSigningAlgorithm.ES512
            else -> WalletAttestationSigningAlgorithm.ES256
        }

    private companion object {
        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                explicitNulls = false
            }
    }
}

/**
 * Adapts [Wsca.sign] (the ONLY signing surface this module is allowed to touch - Global
 * Constraints, KMS boundary) to the [WalletAttestationSigner] seam
 * [Ts03WalletAttestationEncoder] was built against, so [LocalWalletProvider] can reuse that
 * encoder's header/payload/compact-JWT assembly rather than re-implementing it.
 */
private class WscaBackedWalletAttestationSigner(
    private val wsca: Wsca,
    private val walletUnitId: String,
    private val signerKey: WalletAttestedKeyRef,
    private val operationBinding: String,
) : WalletAttestationSigner {
    override suspend fun sign(request: WalletAttestationSigningRequest): IdkResult<WalletAttestationSigningResult, IdkError> {
        val signature = wsca.sign(walletUnitId, signerKey, request.signingInput, operationBinding).getOrElse { return Err(it) }
        return Ok(
            WalletAttestationSigningResult(
                signature = signature,
                algorithm = request.algorithm,
                signerProfile = request.signerProfile,
                signerId = request.signerId,
                x5c = request.x5c,
                keyId = request.keyId,
                evidence = mapOf("wscdSecureComponent" to wsca.wscdProfile.secureComponent.name.lowercase()),
            ),
        )
    }
}
