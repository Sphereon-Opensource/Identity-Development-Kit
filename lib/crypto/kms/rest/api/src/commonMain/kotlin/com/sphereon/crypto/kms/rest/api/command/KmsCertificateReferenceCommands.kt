/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.api.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.model.Origin
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.PublicApiCommand
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.certificate.persistence.CertificateReferenceKind
import com.sphereon.crypto.certificate.persistence.CertificateReferenceSource
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.kms.rest.api.generated.infrastructure.Base64ByteArray
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("RegisterCertificateReferenceInput", exact = true)
@Serializable
data class RegisterCertificateReferenceInput(
    val providerId: String,
    val alias: String,
    val providerCertificateId: String? = null,
    val kind: CertificateReferenceKind,
    val source: CertificateReferenceSource,
    val linkedKeyAlias: String? = null,
    val linkedKeyKid: String? = null,
    /** DER certificates, each encoded as a Base64 JSON string. Required for stored material. */
    val certificateChain: List<Base64ByteArray>? = null,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CertificateReferenceResponse", exact = true)
@Serializable
data class CertificateReferenceResponse(
    val id: String,
    val alias: String,
    val providerId: String,
    val providerCertificateId: String? = null,
    val kind: CertificateReferenceKind,
    val source: CertificateReferenceSource,
    val controlMode: ResourceControlMode,
    val origin: Origin,
    val linkedKeyReferenceId: String? = null,
    val certificateChain: List<Base64ByteArray>? = null,
    val certificateFingerprint: Base64ByteArray,
    val publicKeyFingerprint: Base64ByteArray,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CertificateReferenceMetadataResponse", exact = true)
@Serializable
data class CertificateReferenceMetadataResponse(
    val id: String,
    val alias: String,
    val providerId: String,
    val providerCertificateId: String? = null,
    val kind: CertificateReferenceKind,
    val source: CertificateReferenceSource,
    val controlMode: ResourceControlMode,
    val origin: Origin,
    val linkedKeyReferenceId: String? = null,
    val linkedKeyAlias: String? = null,
    val linkedKeyKid: String? = null,
    val certificateFingerprint: Base64ByteArray,
    val publicKeyFingerprint: Base64ByteArray,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("CertificateReferencesResponse", exact = true)
@Serializable
data class CertificateReferencesResponse(
    val references: List<CertificateReferenceMetadataResponse>,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ListCertificateReferencesInput", exact = true)
@Serializable
data class ListCertificateReferencesInput(
    val providerId: String? = null,
    val kind: CertificateReferenceKind? = null,
    val source: CertificateReferenceSource? = null,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("GetCertificateReferenceInput", exact = true)
@Serializable
data class GetCertificateReferenceInput(
    val id: String,
)

interface RegisterCertificateReferenceServiceCommand :
    ServiceCommand<RegisterCertificateReferenceInput, CertificateReferenceResponse, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.certificates.register"
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.POST,
            pathPattern = "/certificates/register",
            consumes = setOf(MediaType.ApplicationJson),
            produces = setOf(MediaType.ApplicationJson),
            commandId = COMMAND_ID,
            handlerCommandId = COMMAND_ID,
            tags = setOf("Certificates"),
            summary = "Register a tenant-owned certificate reference",
        )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.CREATE
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}

interface ListCertificateReferencesServiceCommand :
    ServiceCommand<ListCertificateReferencesInput, CertificateReferencesResponse, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.certificate-references.list"
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/certificate-references",
            produces = setOf(MediaType.ApplicationJson),
            commandId = COMMAND_ID,
            handlerCommandId = COMMAND_ID,
            tags = setOf("Certificates"),
            summary = "List active tenant-owned certificate references",
        )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.LIST
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}

interface GetCertificateReferenceServiceCommand :
    ServiceCommand<GetCertificateReferenceInput, CertificateReferenceMetadataResponse, IdkError>,
    PublicApiCommand {
    companion object {
        const val COMMAND_ID = "kms.certificate-references.get"
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/certificate-references/{id}",
            produces = setOf(MediaType.ApplicationJson),
            commandId = COMMAND_ID,
            handlerCommandId = COMMAND_ID,
            tags = setOf("Certificates"),
            summary = "Get an active tenant-owned certificate reference",
        )
    }

    override val commandId: String get() = COMMAND_ID
    override val actionType: ActionType get() = ActionType.READ
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT
}
