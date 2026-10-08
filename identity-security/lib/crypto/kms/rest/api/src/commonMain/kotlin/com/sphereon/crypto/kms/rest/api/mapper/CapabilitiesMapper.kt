/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.api.mapper

import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.crypto.core.kms.KmsProviderQuery
import com.sphereon.crypto.core.kms.ProviderMatch
import com.sphereon.crypto.kms.rest.api.generated.models.ListCapabilitiesResponse
import com.sphereon.crypto.kms.rest.api.generated.models.OperationCapability
import com.sphereon.crypto.kms.rest.api.generated.models.ProviderCapabilities
import com.sphereon.crypto.kms.rest.api.generated.models.ProviderCapabilitiesResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ProviderMatch as ProviderMatchRest
import com.sphereon.crypto.kms.rest.api.generated.models.ProviderQuery as ProviderQueryRest
import com.sphereon.crypto.kms.rest.api.generated.models.QueryBestProviderResponse
import com.sphereon.crypto.kms.rest.api.generated.models.QueryProvidersResponse
import com.sphereon.crypto.kms.rest.api.generated.models.ContentEncryptionAlgorithm as ContentEncryptionAlgorithmRest
import com.sphereon.crypto.kms.rest.api.generated.models.CryptoAlg as CryptoAlgRest
import com.sphereon.crypto.kms.rest.api.generated.models.Curve as CurveRest
import com.sphereon.crypto.kms.rest.api.generated.models.DigestAlg as DigestAlgRest
import com.sphereon.crypto.kms.rest.api.generated.models.IdentifierMethod as IdentifierMethodRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyStorageType as KeyStorageTypeRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyType as KeyTypeRest
import com.sphereon.crypto.kms.rest.api.generated.models.KmsProviderOperation as KmsProviderOperationRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyAgreementAlgorithm as KeyAgreementAlgorithmRest
import com.sphereon.crypto.kms.rest.api.generated.models.KeyWrapAlgorithm as KeyWrapAlgorithmRest

fun KmsProviderCapabilities.toRest(): ProviderCapabilities =
    ProviderCapabilities(
        providerId = providerId,
        providerType = providerType,
        storageTypes = storageTypes.map { KeyStorageTypeRest.valueOf(it.name) }.toTypedArray(),
        supportsKeyImport = supportsKeyImport,
        supportsKeyExport = supportsKeyExport,
        exposePrivateKeys = exposePrivateKeys,
        operations = operations.map { it.toRest() }.toTypedArray(),
        supportedKeyTypes = supportedKeyTypes.map { it.toRestKeyType() }.toTypedArray(),
        supportedCurves = supportedCurves.mapNotNull { CurveRest.decode(it.jose.value) }.toTypedArray(),
        supportedCryptoAlgorithms = supportedCryptoAlgorithms.map { CryptoAlgRest.valueOf(it.name) }.toTypedArray(),
        supportedDigestAlgorithms = supportedDigestAlgorithms.map { DigestAlgRest.valueOf(it.name) }.toTypedArray(),
        signatureAlgorithms = signatureAlgorithms.mapNotNull { it.toRest() }.toTypedArray(),
        contentEncryptionAlgorithms = contentEncryptionAlgorithms.map { ContentEncryptionAlgorithmRest.valueOf(it.name) }.toTypedArray(),
        supportsX509 = supportsX509,
        supportsAttestation = supportsAttestation,
        supportsHardwareBacking = supportsHardwareBacking,
        supportsPublicKeyResolution = supportsPublicKeyResolution,
        resolutionMethods = resolutionMethods.map { it.methodName }.toTypedArray(),
    )

fun Array<ProviderCapabilitiesResponse>.toRestCapabilitiesResponse(): ListCapabilitiesResponse =
    ListCapabilitiesResponse(providers = this)

fun KmsProviderCapabilities.toRestResponse(): ProviderCapabilitiesResponse =
    ProviderCapabilitiesResponse(providerId = providerId, capabilities = toRest())

fun ProviderMatch.toRest(): ProviderMatchRest =
    ProviderMatchRest(
        providerId = providerId,
        capabilities = capabilities?.toRest(),
        matchScore = matchScore,
    )

fun Array<ProviderMatch>.toRestQueryResponse(totalProviders: Int): QueryProvidersResponse =
    QueryProvidersResponse(
        matches = map { it.toRest() }.toTypedArray(),
        totalProviders = totalProviders,
        matchCount = size,
    )

fun ProviderMatch?.toRestBestQueryResponse(): QueryBestProviderResponse =
    QueryBestProviderResponse(match = this?.toRest())

fun ProviderQueryRest.toSdk(): KmsProviderQuery =
    KmsProviderQuery(
        operation = operation?.let { com.sphereon.crypto.core.kms.KmsProviderOperation.valueOf(it.name) },
        cryptoAlgorithm = cryptoAlgorithm?.let { com.sphereon.crypto.core.generic.CryptoAlg.valueOf(it.name) },
        digestAlgorithm = digestAlgorithm?.let { com.sphereon.crypto.core.generic.DigestAlg.valueOf(it.name) },
        signatureAlgorithm = signatureAlgorithm?.let { com.sphereon.crypto.core.generic.SignatureAlgorithm.fromValue(it.value) },
        contentEncryptionAlgorithm = contentEncryptionAlgorithm?.let { com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm.valueOf(it.name) },
        curve = curve?.let { restCurve -> com.sphereon.crypto.core.generic.Curve.asList.firstOrNull { it.jose.value == restCurve.value } },
        keyType = keyType?.let { KeyTypeMapping.fromValue(it.value) },
        storageType = storageType?.let { com.sphereon.crypto.core.kms.KeyStorageType.valueOf(it.name) },
        requiresHardwareBacking = requiresHardwareBacking == true,
        requiresAttestation = requiresAttestation == true,
        requiresKeyExport = requiresKeyExport == true,
        requiresKeyImport = requiresKeyImport == true,
        identifierMethod = identifierMethod?.toSdkIdentifierMethod(),
        providerType = providerType,
    )

private fun com.sphereon.crypto.core.kms.OperationCapability.toRest(): OperationCapability =
    OperationCapability(
        operation = KmsProviderOperationRest.valueOf(operation.name),
        supported = supported,
        signatureAlgorithms = signatureAlgorithms.mapNotNull { it.toRest() }.toTypedArray(),
        contentEncryptionAlgorithms = contentEncryptionAlgorithms.map { ContentEncryptionAlgorithmRest.valueOf(it.name) }.toTypedArray(),
        keyWrapAlgorithms = keyWrapAlgorithms.map { KeyWrapAlgorithmRest.valueOf(it.name) }.toTypedArray(),
        keyAgreementAlgorithms = keyAgreementAlgorithms.map { KeyAgreementAlgorithmRest.valueOf(it.name) }.toTypedArray(),
        notes = notes,
    )

private fun KeyTypeMapping.toRestKeyType(): KeyTypeRest = KeyTypeRest.valueOf(jose.value.uppercase())

private fun IdentifierMethodRest.toSdkIdentifierMethod(): com.sphereon.crypto.resolution.IIdentifierMethod =
    com.sphereon.crypto.resolution.IdentifierMethodDefaults.entries.firstOrNull { it.methodName == value }
        ?: throw IllegalArgumentException("Unsupported identifier method: $value")
