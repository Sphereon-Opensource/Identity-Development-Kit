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

internal const val DIFFERENT_KIND =
    "The alias is already registered as a different certificate reference kind"
internal const val DIFFERENT_SOURCE =
    "The alias is already registered with a different certificate source"
internal const val DIFFERENT_LINKED_KEY =
    "The alias is already registered with a different linked key"
internal const val DIFFERENT_PROVIDER_CERTIFICATE_ID =
    "The alias is already registered for a different provider certificate id"
internal const val DIFFERENT_CERTIFICATE_MATERIAL =
    "The alias is already registered with different certificate material"

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
    private val keyReferenceProviderIds: KeyReferenceProviderIds,
    private val execution: SessionExecution,
) {
    private val tenantId: String
        get() = execution.sessionContext.context.tenant.tenantId

    suspend fun register(input: RegisterCertificateReferenceInput): IdkResult<CertificateReferenceRecord, IdkError> {
        return register(input, ResourceControlMode.EXTERNALLY_MANAGED, reserveManaged = false)
    }

    /**
     * Stores an invisible tombstone reservation for material that this REST operation is about to
     * write. The caller must first prove absence through the exact destination certificate store.
     * Insert-only persistence prevents taking over an existing reference; activation happens only
     * after the provider accepts the write.
     */
    suspend fun reservePlatformManaged(
        input: RegisterCertificateReferenceInput,
    ): IdkResult<CertificateReferenceRecord, IdkError> {
        val availability = requireDurableStore()
        if (availability != null) return Err(availability)
        if (input.providerId.isBlank() || input.alias.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "providerId and alias must not be blank"))
        }
        if (input.source != CertificateReferenceSource.STORED_PUBLIC_MATERIAL) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Platform-managed certificate stores require submitted public material"))
        }
        val existing = certificateReferenceStore
            .findLatestByAliasIncludingDeleted(tenantId, input.alias, input.providerId, input.kind)
            .getOrElse { return Err(it) }
        if (existing?.deletedAt == null && existing != null) {
            return Err(managedStoreConflict())
        }
        val reservation = register(
            input,
            ResourceControlMode.PLATFORM_MANAGED,
            reserveManaged = true,
            recordId = Uuid.random().toString(),
        )
        return reservation
    }

    /**
     * Reserves a platform-managed chain for a key whose provider entry already carries one: the
     * self-signed certificate the provider issued when it generated the key, or a chain stored
     * through this platform before. Only a key this platform generated and manages qualifies, and
     * only when the certificate the provider holds is for that same key. Anything registered as
     * externally managed under the alias stays untouched.
     *
     * A previously stored chain for the key is soft-deleted before the new reservation claims the
     * alias, because a claim cannot be taken while an active reference holds it.
     */
    suspend fun reservePlatformManagedChainReplacement(
        input: RegisterCertificateReferenceInput,
        providerLeafDer: ByteArray,
    ): IdkResult<CertificateReferenceRecord, IdkError> {
        val availability = requireDurableStore()
        if (availability != null) return Err(availability)
        if (input.providerId.isBlank() || input.alias.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "providerId and alias must not be blank"))
        }
        if (input.kind != CertificateReferenceKind.KEY_CERTIFICATE_CHAIN ||
            input.source != CertificateReferenceSource.STORED_PUBLIC_MATERIAL
        ) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Only a submitted key certificate chain can replace a provider chain"))
        }

        var superseded: CertificateReferenceRecord? = null
        for (kind in CertificateReferenceKind.entries) {
            val latest = certificateReferenceStore
                .findLatestByAliasIncludingDeleted(tenantId, input.alias, input.providerId, kind)
                .getOrElse { return Err(it) }
                ?: continue
            val active = latest.deletedAt == null
            if (latest.controlMode == ResourceControlMode.EXTERNALLY_MANAGED &&
                (active || latest.source == CertificateReferenceSource.PROVIDER_NATIVE)
            ) {
                return Err(managedStoreConflict("The certificate alias is registered as externally managed"))
            }
            if (active && kind != CertificateReferenceKind.KEY_CERTIFICATE_CHAIN) return Err(managedStoreConflict())
            if (active) superseded = latest
        }

        val linkedKey = resolveLinkedKey(input).getOrElse { return Err(it) }
        if (linkedKey.controlMode != ResourceControlMode.PLATFORM_MANAGED) {
            return Err(managedStoreConflict("The provider certificate of an externally managed key is not replaced"))
        }
        val inspectedKey = keyInspector
            .inspect(providerId = input.providerId, alias = input.linkedKeyAlias ?: linkedKey.alias, kid = input.linkedKeyKid)
            .getOrElse { return Err(it) }
        val keySpki = canonicalKeySpki(inspectedKey)
            ?: return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
                    message = "The linked provider key has no supported public key material",
                ),
            )
        val providerLeafSpki = try {
            canonicalCertificateSpki(providerLeafDer)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Err(managedStoreConflict("The certificate the provider holds for this alias cannot be read"))
        }
        if (!providerLeafSpki.contentEquals(keySpki)) {
            return Err(managedStoreConflict("The provider holds a certificate for a different key under this alias"))
        }
        val material = storedMaterial(input).getOrElse { return Err(it) }
        if (!material.canonicalSpki.contentEquals(keySpki)) {
            return Err(
                IdkError.fromString(
                    code = CertificateReferenceStoreErrorCodes.KEY_IDENTITY_MISMATCH,
                    message = "The certificate leaf does not match the linked provider key",
                ),
            )
        }
        val currentChain = superseded
        if (currentChain != null) {
            if (currentChain.linkedKeyReferenceId != linkedKey.id) {
                return Err(managedStoreConflict("The certificate alias is linked to a different key"))
            }
            val deleted = softDelete(currentChain).getOrElse { return Err(it) }
            if (!deleted) return Err(managedStoreConflict())
        }
        return register(
            input,
            ResourceControlMode.PLATFORM_MANAGED,
            reserveManaged = true,
            recordId = Uuid.random().toString(),
        )
    }

    /** Makes a pre-store tombstone visible only after the provider accepted the object. */
    suspend fun activatePlatformManagedReservation(
        reservation: CertificateReferenceRecord,
    ): IdkResult<CertificateReferenceRecord, IdkError> {
        if (reservation.controlMode != ResourceControlMode.PLATFORM_MANAGED || reservation.deletedAt == null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "The certificate ownership reservation is invalid"))
        }
        val activated = certificateReferenceStore
            .activateAliasReservation(tenantId, reservation.id, Clock.System.now())
            .getOrElse { return Err(it) }
            ?: return Err(IdkError.UNKNOWN_ERROR(message = "The certificate alias reservation could not be activated"))
        certificateReferenceStore
            .releaseAliasClaim(tenantId, reservation.providerId, reservation.alias, reservation.id)
            .getOrElse { return Err(it) }
        return Ok(activated)
    }

    private suspend fun register(
        input: RegisterCertificateReferenceInput,
        controlMode: ResourceControlMode,
        reserveManaged: Boolean,
        recordId: String? = null,
    ): IdkResult<CertificateReferenceRecord, IdkError> {
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

        val existing = findActiveByAlias(input.alias, input.providerId).getOrElse { return Err(it) }
        if (existing != null && reserveManaged) return Err(managedStoreConflict())
        if (existing != null && existing.controlMode != controlMode) {
            return Err(registrationConflict("The certificate reference ownership cannot be changed in place"))
        }
        val providerCertificateId = material.providerCertificateId ?: input.providerCertificateId
        // A registration never changes a known certificate reference. Registering the same
        // reference again returns it unchanged; any difference is a conflict.
        if (existing != null) {
            val difference = reregistrationDifference(existing, input, material, linkedKeyReferenceId, providerCertificateId)
            return if (difference == null) Ok(existing) else Err(registrationConflict(difference))
        }
        if (providerCertificateId != null) {
            val identityMatches = certificateReferenceStore
                .findByProviderCertificateId(tenantId, input.providerId, providerCertificateId)
                .getOrElse { return Err(it) }
            if (identityMatches.any { it.alias != input.alias || it.kind != input.kind }) {
                return Err(registrationConflict("The provider certificate is already registered under another reference"))
            }
        }

        val now = Clock.System.now()
        val record = try {
            CertificateReferenceRecord(
                id = recordId ?: Uuid.random().toString(),
                tenantId = tenantId,
                alias = input.alias,
                providerId = input.providerId,
                providerCertificateId = providerCertificateId,
                kind = input.kind,
                source = input.source,
                controlMode = controlMode,
                linkedKeyReferenceId = linkedKeyReferenceId,
                certificateChainDer = if (input.source == CertificateReferenceSource.STORED_PUBLIC_MATERIAL) {
                    CertificateReferenceRecord.encodeCertificateChain(material.chainDer)
                } else {
                    null
                },
                certificateFingerprint = CertificateReferenceRecord.certificateFingerprintOf(material.leafDer),
                publicKeyFingerprint = CertificateReferenceRecord.publicKeyFingerprintOfCanonicalSpki(material.canonicalSpki),
                createdAt = now,
                createdById = null,
                updatedAt = now,
                updatedById = null,
                deletedAt = now.takeIf { reserveManaged },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "The certificate reference material is invalid"))
        }
        val acquired = certificateReferenceStore
            .tryAcquireAliasClaim(tenantId, record.providerId, record.alias, record.id)
            .getOrElse { return Err(it) }
        if (!acquired) return Err(managedStoreConflict())
        val saved = certificateReferenceStore.save(record)
        if (!reserveManaged) {
            certificateReferenceStore.releaseAliasClaim(tenantId, record.providerId, record.alias, record.id)
                .getOrElse { return Err(it) }
        }
        return saved
    }

    private suspend fun findActiveByAlias(
        alias: String,
        providerId: String,
    ): IdkResult<CertificateReferenceRecord?, IdkError> {
        for (kind in CertificateReferenceKind.entries) {
            val found = certificateReferenceStore.findByAlias(tenantId, alias, providerId, kind).getOrElse { return Err(it) }
            if (found != null) return Ok(found)
        }
        return Ok(null)
    }

    private fun reregistrationDifference(
        existing: CertificateReferenceRecord,
        input: RegisterCertificateReferenceInput,
        material: CertificateMaterial,
        linkedKeyReferenceId: String?,
        providerCertificateId: String?,
    ): String? = when {
        existing.kind != input.kind -> DIFFERENT_KIND
        existing.source != input.source -> DIFFERENT_SOURCE
        existing.linkedKeyReferenceId != linkedKeyReferenceId -> DIFFERENT_LINKED_KEY
        existing.providerCertificateId != providerCertificateId -> DIFFERENT_PROVIDER_CERTIFICATE_ID
        !sameMaterial(existing, input.source, material) -> DIFFERENT_CERTIFICATE_MATERIAL
        else -> null
    }

    private fun sameMaterial(
        existing: CertificateReferenceRecord,
        source: CertificateReferenceSource,
        material: CertificateMaterial,
    ): Boolean {
        if (!CertificateReferenceRecord.certificateFingerprintOf(material.leafDer).contentEquals(existing.certificateFingerprint)) {
            return false
        }
        if (source != CertificateReferenceSource.STORED_PUBLIC_MATERIAL) return true
        val stored = storedChain(existing).getOrElse { return false }
        return stored.size == material.chainDer.size &&
            stored.zip(material.chainDer).all { (left, right) -> left.contentEquals(right) }
    }

    private fun registrationConflict(message: String) =
        IdkError.fromString(code = CertificateReferenceStoreErrorCodes.REGISTRATION_CONFLICT, message = message)

    private fun managedStoreConflict(
        message: String = "The certificate alias is already owned or exists at the provider",
    ) = IdkError.fromString(code = "KMS_CERTIFICATE_REFERENCE_MANAGED_STORE_CONFLICT", message = message)

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

    suspend fun softDelete(record: CertificateReferenceRecord): IdkResult<Boolean, IdkError> {
        val deleted = certificateReferenceStore.deleteById(tenantId, record.id).getOrElse { return Err(it) }
        if (deleted) {
            certificateReferenceStore.releaseAliasClaim(tenantId, record.providerId, record.alias, record.id)
                .getOrElse { return Err(it) }
        }
        return Ok(deleted)
    }

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
        if (linkedKey.providerId !in keyReferenceProviderIds.recordedUnder(record.providerId)) {
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

    /**
     * Finds the tenant's registered key under every id its provider records key references with.
     * An external registration records the provider's runtime id, which can differ from the id
     * the certificate request names.
     */
    private suspend fun resolveLinkedKey(
        input: RegisterCertificateReferenceInput,
    ): IdkResult<KeyReferenceRecord, IdkError> {
        val linkedKeyAlias = input.linkedKeyAlias
        val linkedKeyKid = input.linkedKeyKid
        if (linkedKeyAlias == null && linkedKeyKid == null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "KEY_CERTIFICATE_CHAIN requires linkedKeyAlias or linkedKeyKid"))
        }
        for (providerId in keyReferenceProviderIds.recordedUnder(input.providerId).distinct()) {
            val found =
                if (linkedKeyAlias != null) {
                    keyReferenceStore.findByAlias(tenantId, linkedKeyAlias, providerId)
                } else {
                    keyReferenceStore.findByKid(tenantId, requireNotNull(linkedKeyKid), providerId)
                }.getOrElse { return Err(it) }
            if (found != null) return Ok(found)
        }
        return Err(
            IdkError.NOT_FOUND_ERROR(
                message = "The linked key is not registered for this tenant and provider; register it with POST /keys/register first",
            ),
        )
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
