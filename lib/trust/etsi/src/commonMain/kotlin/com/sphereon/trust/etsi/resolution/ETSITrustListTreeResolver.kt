/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.trust.etsi.resolution

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import com.sphereon.trust.core.resolver.ResolutionOptions
import com.sphereon.trust.core.resolver.TrustListData
import com.sphereon.trust.core.resolver.TrustListResolutionException
import com.sphereon.trust.etsi.lote.model.EidasRole
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSIOtherLoTEPointer
import com.sphereon.trust.etsi.parser.ETSITrustListParser
import io.ktor.http.Url

/** The document seam used by the tree resolver after transport and signature policy are configured. */
fun interface ETSITrustListDocumentResolver {
    suspend fun resolve(uri: String, options: ResolutionOptions): TrustListData
}

/** Egress policy for one LoTL navigation operation. Hosts are canonical DNS names. */
data class ETSITrustListTreePolicy(
    val allowedHosts: Set<String>,
    val allowUnlistedChildHosts: Boolean = false,
    val territory: String? = null,
)

data class ETSIResolvedTrustList(
    val uri: String,
    val data: TrustListData,
    val list: ETSILoTE,
    val signerRoots: List<ByteArray>,
)

data class ETSITrustListTree(val lists: List<ETSIResolvedTrustList>)

/**
 * Resolves a verified LoTL and its QEAA member-state children.
 *
 * The root is verified before any pointer is followed. A child is verified only
 * with the non-empty certificate identities carried by that verified pointer,
 * never with the root signer roots. TS 119 602-qualified pointers are not part
 * of this QEAA tree.
 */
class ETSITrustListTreeResolver(
    private val documentResolver: ETSITrustListDocumentResolver,
    private val parser: ETSITrustListParser,
) {
    suspend fun resolve(
        rootUri: String,
        rootOptions: ResolutionOptions,
        policy: ETSITrustListTreePolicy,
    ): ETSITrustListTree {
        requireVerifiedRoot(rootOptions)
        validateUrl(rootUri, policy, isRoot = true)

        val rootData = retainResolvedDocument(documentResolver.resolve(rootUri, rootOptions), rootUri)
        val root = parse(rootData, rootData.sourceUri)
        val resolved = mutableListOf(
            ETSIResolvedTrustList(
                uri = rootData.sourceUri,
                data = rootData,
                list = root,
                signerRoots = rootOptions.trustedSignerRoots.orEmpty(),
            ),
        )

        val pointers = LoTERoleTrustListRouting.findPointersForRole(root, EidasRole.QEAA_PROVIDER, policy.territory)
        if (pointers.size > MAX_POINTERS) {
            fail(TrustDiagnosticReasonCodes.TRUST_LIST_POINTER_MISSING, "Too many LoTL pointers")
        }

        for (pointer in pointers) {
            val pointerRoots = pointerRoots(pointer)
            validateUrl(pointer.location, policy, isRoot = false)
            val childOptions = rootOptions.copy(trustedSignerRoots = pointerRoots)
            val childData = retainResolvedDocument(documentResolver.resolve(pointer.location, childOptions), pointer.location)
            val child = parse(childData, childData.sourceUri)
            resolved += ETSIResolvedTrustList(childData.sourceUri, childData, child, pointerRoots)
        }
        return ETSITrustListTree(resolved)
    }

    private fun retainResolvedDocument(data: TrustListData, requestedUri: String): TrustListData = data.copy(
        data = data.data.copyOf(),
        sourceUri = data.sourceUri.ifBlank { requestedUri },
    )

    private fun parse(data: TrustListData, expectedUri: String): ETSILoTE = try {
        parser.parseFromBytes(data.data)
    } catch (failure: Exception) {
        fail(
            TrustDiagnosticReasonCodes.TRUST_LIST_RESOLUTION_FAILED,
            "Unable to parse verified trust list at $expectedUri",
            failure,
        )
    }

    private fun pointerRoots(pointer: ETSIOtherLoTEPointer): List<ByteArray> {
        val certificates = pointer.serviceDigitalIdentities.flatMap { it.x509Certificates }
        if (certificates.isEmpty()) {
            fail(TrustDiagnosticReasonCodes.SIGNER_ROOT_NOT_CONFIGURED, "LoTL pointer has no certificate identities")
        }
        return certificates.map { encoded ->
            if (encoded.isBlank()) {
                fail(TrustDiagnosticReasonCodes.SIGNER_ROOT_NOT_CONFIGURED, "LoTL pointer has a blank certificate identity")
            }
            try {
                encoded.decodeFrom(Encoding.BASE64).also {
                    if (it.isEmpty()) fail(TrustDiagnosticReasonCodes.SIGNER_ROOT_NOT_CONFIGURED, "LoTL pointer has an empty certificate identity")
                }
            } catch (failure: Exception) {
                fail(TrustDiagnosticReasonCodes.SIGNATURE_INVALID, "LoTL pointer certificate identity is malformed", failure)
            }
        }
    }

    private fun requireVerifiedRoot(options: ResolutionOptions) {
        if (!options.verifySignature || options.trustedSignerRoots.isNullOrEmpty()) {
            fail(TrustDiagnosticReasonCodes.SIGNER_ROOT_NOT_CONFIGURED, "A verified LoTL root requires explicit signer roots")
        }
    }

    private fun validateUrl(uri: String, policy: ETSITrustListTreePolicy, isRoot: Boolean) {
        val url = try {
            Url(uri)
        } catch (failure: Exception) {
            fail(TrustDiagnosticReasonCodes.TRUST_LIST_URL_MALFORMED, "LoTL URL is malformed", failure)
        }
        if (url.protocol.name.lowercase() != "https" || url.host.isBlank()) {
            fail(TrustDiagnosticReasonCodes.TRUST_LIST_URL_REJECTED, "LoTL URL must use HTTPS and include a host")
        }
        val host = url.host.lowercase()
        val allowed = policy.allowedHosts.map { it.trim().lowercase() }.toSet()
        if (isRoot || !policy.allowUnlistedChildHosts) {
            if (host !in allowed) {
                fail(TrustDiagnosticReasonCodes.TRUST_LIST_URL_REJECTED, "LoTL host is not explicitly allowlisted: $host")
            }
        }
    }

    private fun fail(reasonCode: String, message: String, cause: Throwable? = null): Nothing =
        throw TrustListResolutionException(message, cause, reasonCode)

    private companion object {
        const val MAX_POINTERS = 64
    }
}

