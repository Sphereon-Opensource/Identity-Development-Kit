/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:Suppress("ReturnCount")

package com.sphereon.crypto.kms.rest.server.service

import at.asitplus.awesn1.serialization.DER
import at.asitplus.awesn1.crypto.SubjectPublicKeyInfo
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.certificate.persistence.CertificateReferenceRecord
import com.sphereon.crypto.certificate.persistence.CertificateReferenceSource
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStore
import com.sphereon.crypto.certificate.persistence.CertificateReferenceStoreErrorCodes
import com.sphereon.crypto.certificate.persistence.CertificateReferenceHistoryCapability
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.interop.getPublicKeyBytes
import com.sphereon.crypto.core.interop.toSubjectPublicKeyInfo
import com.sphereon.crypto.core.interop.toX509Certificate
import com.sphereon.crypto.core.kms.ProviderCertificateReference
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateChainFromDer
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.kms.rest.api.command.RegisterCertificateReferenceInput
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

class CertificateReferenceResolutionException(
    val code: String,
    message: String,
) : Exception(message)

/** Coordinates certificate reference ownership, provider reinspection, and public material. */
@Inject
@SingleIn(SessionScope::class)
class CertificateReferenceRegistrar(
    private val certificateReferenceStore: CertificateReferenceStore,
    private val keyReferenceStore: KeyReferenceStore,
    private val keyInspector: ProviderKeyReferenceInspector,
    private val providerInspector: ProviderCertificateReferenceInspector,
    private val execution: SessionExecution,
) {
    private val tenantId: String
        get() = execution.sessionContext.context.tenant.tenantId

    suspend fun register(input: RegisterCertificateReferenceInput): IdkResult<CertificateReferenceRecord, IdkError> {
        val availability = requireDurableStore()
        if (availability != null) return Err(availability)
        if (input.providerId.isBlank() || input.alias.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "providerId and alias must not be blank"))
        }

        val material = when (input.source) {
            CertificateReferenceSource.STORED_PUBLIC_MATERIAL -> storedMaterial(input)
            CertificateReferenceSource.PROVIDER_NATIVE -> providerMaterial(input)
        }.getOrElse { return Err(it) }

        var linkedKeyReferenceId: String? = null
        if (input.kind == CertificateReferenceKind.KEY_CERTIFICATE_CHAIN) {
            val linkedKeyResult: IdkResult<KeyReferenceRecord, IdkError> = resolveLinkedKey(input)
            if (linkedKeyResult.isErr) return Err(linkedKeyResult.error)
            val linkedKey = linkedKeyResult.value
            val inspectedKey =
                keyInspector.inspect(
                    providerId = input.providerId,
                    alias = input.linkedKeyAlias ?: linkedKey.alias,
                    kid = input.linkedKeyKid,
                ).getOrElse { return Err(it) }
            val keySpki = canonicalKeySpki(inspectedKey)
                ?: return Err(IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
                    message = "The linked provider key has no supported public key material",
                ))
            if (!keySpki.contentEquals(material.canonicalSpki)) {
                return Err(
                    IdkError.fromString(
                        code = CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
                        message = "The certificate leaf does not match the linked provider key",
                    ),
                )
            }
            linkedKeyReferenceId = linkedKey.id
        }

        val existing = certificateReferenceStore
            .findByAlias(tenantId, input.alias, input.providerId, input.kind)
            .getOrElse { return Err(it) }
        val providerCertificateId = material.providerCertificateId ?: input.providerCertificateId
        if (providerCertificateId != null) {
            val identityMatches = certificateReferenceStore
                .findByProviderCertificateId(tenantId, input.providerId, providerCertificateId)
                .getOrElse { return Err(it) }
            if (identityMatches.any { it.alias != input.alias || it.kind != input.kind }) {
                return Err(
                    IdkError.fromString(
                        code = CertificateReferenceStoreErrorCodes.REGISTRATION_CONFLICT,
                        message = "The provider certificate is already registered under another reference",
                    ),
                )
            }
        }

        val now = Clock.System.now()
        val record = try {
            CertificateReferenceRecord(
                id = existing?.id ?: Uuid.random().toString(),
                tenantId = tenantId,
                alias = input.alias,
                providerId = input.providerId,
                providerCertificateId = providerCertificateId,
                kind = input.kind,
                source = input.source,
                controlMode = ResourceControlMode.EXTERNALLY_MANAGED,
                linkedKeyReferenceId = linkedKeyReferenceId,
                certificateChainDer = if (input.source == CertificateReferenceSource.STORED_PUBLIC_MATERIAL) {
                    CertificateReferenceRecord.encodeCertificateChain(material.chainDer)
                } else {
                    null
                },
                certificateFingerprint = CertificateReferenceRecord.certificateFingerprintOf(material.leafDer),
                publicKeyFingerprint = CertificateReferenceRecord.publicKeyFingerprintOfCanonicalSpki(material.canonicalSpki),
                createdAt = existing?.createdAt ?: now,
                createdById = existing?.createdById,
                updatedAt = now,
                updatedById = null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "The certificate reference material is invalid"))
        }
        return certificateReferenceStore.upsert(record)
    }

    suspend fun findLatest(
        alias: String,
        providerId: String?,
        kind: CertificateReferenceKind,
    ): IdkResult<CertificateReferenceRecord?, IdkError> {
        val availability = requireDurableStore()
        if (availability != null) return Err(availability)
        if (alias.isBlank()) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "alias must not be blank"))

        if (providerId != null) {
            return certificateReferenceStore.findLatestByAliasIncludingDeleted(tenantId, alias, providerId, kind)
        }
        val matches = certificateReferenceStore
            .findAllByAliasIncludingDeleted(tenantId, alias, null, kind)
            .getOrElse { return Err(it) }
        val providers = matches.map { it.providerId }.distinct()
        if (providers.size > 1) {
            return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.AMBIGUOUS_REFERENCE,
                    message = "More than one provider owns this certificate reference",
                ),
            )
        }
        return Ok(matches.firstOrNull())
    }

    suspend fun list(
        providerId: String?,
        kind: CertificateReferenceKind?,
        source: CertificateReferenceSource? = null,
    ): IdkResult<List<CertificateReferenceRecord>, IdkError> {
        val availability = requireDurableStore()
        if (availability != null) return Err(availability)
        return certificateReferenceStore.findAll(tenantId, providerId, kind, source)
    }

    suspend fun findById(id: String): IdkResult<CertificateReferenceRecord?, IdkError> {
        val availability = requireDurableStore()
        if (availability != null) return Err(availability)
        if (id.isBlank()) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "id must not be blank"))
        return certificateReferenceStore.findById(tenantId, id)
    }

    suspend fun findLinkedKeyReference(
        record: CertificateReferenceRecord,
    ): IdkResult<KeyReferenceRecord?, IdkError> =
        record.linkedKeyReferenceId?.let { keyReferenceStore.findById(tenantId, it) } ?: Ok(null)

    suspend fun softDelete(record: CertificateReferenceRecord): IdkResult<Boolean, IdkError> =
        certificateReferenceStore.deleteById(tenantId, record.id)

    suspend fun inspectProvider(record: CertificateReferenceRecord): IdkResult<ProviderCertificateReference, IdkError> =
        providerInspector.inspect(record.providerId, record.alias, record.providerCertificateId, record.kind)

    suspend fun verifyProviderRead(
        record: CertificateReferenceRecord,
        fresh: ProviderCertificateReference,
    ): IdkResult<Unit, IdkError> {
        val leaf = fresh.certificate
        val certificateFingerprint = CertificateReferenceRecord.certificateFingerprintOf(leaf.der)
        val publicKeyFingerprint = CertificateReferenceRecord.publicKeyFingerprintOfCanonicalSpki(canonicalCertificateSpki(leaf.der))
        if (fresh.providerId != record.providerId || fresh.alias != record.alias ||
            (record.providerCertificateId != null && fresh.id != record.providerCertificateId) ||
            !certificateFingerprint.contentEquals(record.certificateFingerprint) ||
            !publicKeyFingerprint.contentEquals(record.publicKeyFingerprint)
        ) {
            return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.PROVIDER_DRIFT,
                    message = "The provider certificate no longer matches its registered reference",
                ),
            )
        }
        if (record.kind == CertificateReferenceKind.KEY_CERTIFICATE_CHAIN) {
            val binding = verifyLinkedKeyBinding(record, canonicalCertificateSpki(leaf.der))
            if (binding.isErr) return Err(binding.error)
        }
        return Ok(Unit)
    }

    /**
     * Re-establishes what a stored chain claims. The bytes are tenant supplied, so the only thing
     * tying them to the provider is the linked key, and that link is proven once at registration.
     * A read can happen long after, by which point the provider key may have been rotated, so the
     * proof is repeated here instead of being inherited from the record.
     */
    suspend fun verifyStoredRead(
        record: CertificateReferenceRecord,
        chainDer: List<ByteArray>,
    ): IdkResult<Unit, IdkError> {
        val leafDer = chainDer.firstOrNull()
            ?: return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.PROVIDER_DRIFT,
                    message = "The stored certificate reference has no leaf certificate",
                ),
            )
        if (!CertificateReferenceRecord.certificateFingerprintOf(leafDer).contentEquals(record.certificateFingerprint)) {
            return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.PROVIDER_DRIFT,
                    message = "The stored certificate no longer matches its registered fingerprint",
                ),
            )
        }
        if (record.kind != CertificateReferenceKind.KEY_CERTIFICATE_CHAIN) return Ok(Unit)
        return verifyLinkedKeyBinding(record, canonicalCertificateSpki(leafDer))
    }

    private suspend fun verifyLinkedKeyBinding(
        record: CertificateReferenceRecord,
        leafSpki: ByteArray,
    ): IdkResult<Unit, IdkError> {
        val linkedKey = findLinkedKeyReference(record).getOrElse { return Err(it) }
            ?: return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
                    message = "The certificate reference has no linked tenant key",
                ),
            )
        if (linkedKey.providerId != record.providerId) {
            return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
                    message = "The linked key belongs to a different provider",
                ),
            )
        }
        val inspectedKey = inspectLinkedKey(record.providerId, linkedKey).getOrElse { return Err(it) }
        val keySpki = canonicalKeySpki(inspectedKey)
            ?: return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
                    message = "The linked provider key has no supported public key material",
                ),
            )
        if (!keySpki.contentEquals(leafSpki)) {
            return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
                    message = "The certificate leaf does not match the linked provider key",
                ),
            )
        }
        return Ok(Unit)
    }

    fun storedChain(record: CertificateReferenceRecord): IdkResult<List<ByteArray>, IdkError> =
        try {
            val encodedChain = record.certificateChainDer
            if (record.source != CertificateReferenceSource.STORED_PUBLIC_MATERIAL || encodedChain == null) {
                Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "The certificate reference does not contain stored public material"))
            } else {
                Ok(CertificateReferenceRecord.decodeCertificateChain(encodedChain))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Stored certificate reference material is invalid"))
        }

    private suspend fun storedMaterial(input: RegisterCertificateReferenceInput): IdkResult<CertificateMaterial, IdkError> {
        if (input.certificateChain.isNullOrEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "certificateChain is required for stored public material"))
        }
        if (input.providerCertificateId?.isBlank() == true) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "providerCertificateId must not be blank"))
        }
        return try {
            val certificateChain = input.certificateChain
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "certificateChain is required for stored public material"))
            val chainDer = certificateChain.map { it.value }.toTypedArray()
            val chain = certificateChainFromDer(chainDer).toList()
            val leaf = chain.firstOrNull()
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "certificateChain must not be empty"))
            chainLinkageError(chain)?.let { return Err(it) }
            Ok(CertificateMaterial(
                leafDer = leaf.der,
                chainDer = chain.map { it.der },
                canonicalSpki = canonicalCertificateSpki(leaf.der),
                providerCertificateId = input.providerCertificateId,
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "certificateChain must contain valid DER certificates"))
        }
    }

    private suspend fun providerMaterial(input: RegisterCertificateReferenceInput): IdkResult<CertificateMaterial, IdkError> {
        val suppliedChain = input.certificateChain
        if (suppliedChain != null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "certificateChain is not accepted for provider-native material"))
        }
        val inspected = providerInspector.inspect(
            providerId = input.providerId,
            alias = input.alias,
            providerCertificateId = input.providerCertificateId,
            kind = input.kind,
        ).getOrElse { return Err(it) }
        return Ok(CertificateMaterial(
            leafDer = inspected.certificate.der,
            chainDer = listOf(inspected.certificate.der),
            canonicalSpki = canonicalCertificateSpki(inspected.certificate.der),
            providerCertificateId = inspected.id,
        ))
    }

    private suspend fun resolveLinkedKey(
        input: RegisterCertificateReferenceInput,
    ): IdkResult<KeyReferenceRecord, IdkError> {
        val linkedKeyAlias = input.linkedKeyAlias
        val linkedKeyKid = input.linkedKeyKid
        val result: IdkResult<KeyReferenceRecord?, IdkError> = when {
            linkedKeyAlias != null -> keyReferenceStore.findByAlias(tenantId, linkedKeyAlias, input.providerId)
            linkedKeyKid != null -> keyReferenceStore.findByKid(tenantId, linkedKeyKid, input.providerId)
            else -> Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "KEY_CERTIFICATE_CHAIN requires linkedKeyAlias or linkedKeyKid"))
        }
        if (result.isErr) return Err(result.error)
        return result.value?.let { Ok(it) }
            ?: Err(IdkError.NOT_FOUND_ERROR(message = "The linked tenant key reference was not found"))
    }

    /**
     * Completes a provider-native leaf upward using the tenant's registered CA certificates.
     *
     * Key Vault holds the leaf and nothing above it, so the intermediates come from the trusted
     * certificates the tenant has already registered. The leaf is read live on every request, which
     * is what keeps an Azure-side renewal visible here; only the issuers above it are resolved from
     * storage. Resolution matches issuer DN to subject DN and stops at a self-signed certificate,
     * or earlier when the next issuer is not registered, since many deployments deliberately omit
     * the root from a published chain.
     */
    suspend fun completeChainFromTrustStore(
        leafDer: ByteArray,
        providerId: String,
    ): IdkResult<List<ByteArray>, IdkError> {
        val leaf = try {
            certificateFromDer(leafDer)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(message = "The provider leaf certificate is invalid"))
        }
        if (leaf.issuerDN == leaf.subjectDN) return Ok(listOf(leaf.der))

        val anchors = trustedCertificatesBySubject(providerId).getOrElse { return Err(it) }
        val chain = mutableListOf(leaf)
        val seen = mutableSetOf(leaf.subjectDN)
        var current = leaf
        while (current.issuerDN != current.subjectDN) {
            val issuer = anchors[current.issuerDN] ?: break
            if (!seen.add(issuer.subjectDN)) break
            chain += issuer
            current = issuer
        }
        if (chain.size == 1) {
            return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.PROVIDER_DRIFT,
                    message = "No registered trusted certificate issues this provider certificate",
                ),
            )
        }
        return Ok(chain.map { it.der })
    }

    private suspend fun trustedCertificatesBySubject(
        providerId: String,
    ): IdkResult<Map<String, Certificate>, IdkError> {
        val records = certificateReferenceStore
            .findAll(tenantId, providerId, CertificateReferenceKind.TRUSTED_CERTIFICATE, CertificateReferenceSource.STORED_PUBLIC_MATERIAL)
            .getOrElse { return Err(it) }
        val bySubject = mutableMapOf<String, Certificate>()
        records.forEach { record ->
            if (record.deletedAt != null) return@forEach
            val stored = storedChain(record).getOrElse { return@forEach }
            stored.forEach { der ->
                val certificate = runCatching { certificateFromDer(der) }.getOrNull() ?: return@forEach
                if (!bySubject.containsKey(certificate.subjectDN)) bySubject[certificate.subjectDN] = certificate
            }
        }
        return Ok(bySubject)
    }

    /**
     * Resolves the linked key the same way registration did: by alias, falling back to the stored
     * kid only when the alias does not resolve. Registration inspects with the kid the caller
     * supplied, which is absent for an alias-only registration, so a read that insisted on the
     * stored kid rejected keys the provider can only find by alias.
     */
    private suspend fun inspectLinkedKey(
        providerId: String,
        linkedKey: KeyReferenceRecord,
    ): IdkResult<ManagedKeyInfoType<*>, IdkError> {
        val byAlias = keyInspector.inspect(providerId = providerId, alias = linkedKey.alias, kid = null)
        if (byAlias.isOk) return byAlias
        val kid = linkedKey.kid ?: return byAlias
        return keyInspector.inspect(providerId = providerId, alias = linkedKey.alias, kid = kid)
    }

    /**
     * A submitted chain must actually be a chain. Without this an unrelated bag of certificates is
     * accepted at registration and only fails at whatever verifier later consumes the x5chain.
     */
    private fun chainLinkageError(chain: List<Certificate>): IdkError? {
        chain.zipWithNext().forEach { (subject, issuer) ->
            if (subject.issuerDN != issuer.subjectDN) {
                return IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "certificateChain must be ordered leaf to root with each issuer directly above its subject",
                )
            }
        }
        return null
    }

    private fun canonicalCertificateSpki(der: ByteArray): ByteArray =
        certificateFromDer(der).toX509Certificate().getPublicKeyBytes()

    private fun canonicalKeySpki(key: ManagedKeyInfoType<*>): ByteArray? =
        runCatching {
            val jwk = CoseJoseKeyMappingService.toJwkKeyInfo(key).key ?: return null
            if (jwk.kty == JwaKeyType.oct) return null
            DER.encodeToByteArray(SubjectPublicKeyInfo.serializer(), jwk.toPublicKey().toSubjectPublicKeyInfo())
        }.getOrNull()

    private fun requireDurableStore(): IdkError? = when {
        !certificateReferenceStore.isAvailable ->
            IdkError.fromString(
                code = CertificateReferenceStoreErrorCodes.STORE_UNAVAILABLE,
                message = "Certificate reference persistence is required for this operation",
            )
        certificateReferenceStore.ownershipHistoryCapability != CertificateReferenceHistoryCapability.DURABLE ->
            IdkError.fromString(
                code = CertificateReferenceStoreErrorCodes.DURABLE_HISTORY_UNSUPPORTED,
                message = "Certificate reference persistence must retain durable ownership history",
            )
        else -> null
    }

    private data class CertificateMaterial(
        val leafDer: ByteArray,
        val chainDer: List<ByteArray>,
        val canonicalSpki: ByteArray,
        val providerCertificateId: String?,
    )
}
