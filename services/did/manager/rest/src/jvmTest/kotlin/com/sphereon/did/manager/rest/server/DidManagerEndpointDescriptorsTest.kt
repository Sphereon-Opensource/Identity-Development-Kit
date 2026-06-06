/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.rest.server

import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.did.manager.rest.server.command.AddAlsoKnownAsEndpointCommand
import com.sphereon.did.manager.rest.server.command.AddControllerEndpointCommand
import com.sphereon.did.manager.rest.server.command.AddDidServiceEndpointCommand
import com.sphereon.did.manager.rest.server.command.AddEquivalentIdEndpointCommand
import com.sphereon.did.manager.rest.server.command.AddKeyMappingEndpointCommand
import com.sphereon.did.manager.rest.server.command.AddVerificationMethodEndpointCommand
import com.sphereon.did.manager.rest.server.command.AddVerificationRelationshipEndpointCommand
import com.sphereon.did.manager.rest.server.command.CreateDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.DeactivateDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.DeleteDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.GetDidDocumentEndpointCommand
import com.sphereon.did.manager.rest.server.command.GetDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.GetDidServiceEndpointCommand
import com.sphereon.did.manager.rest.server.command.GetMethodCapabilitiesEndpointCommand
import com.sphereon.did.manager.rest.server.command.GetMethodCapabilitySummaryEndpointCommand
import com.sphereon.did.manager.rest.server.command.GetVerificationMethodEndpointCommand
import com.sphereon.did.manager.rest.server.command.InvalidateDidDocumentEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListAlsoKnownAsEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListControllersEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListDidServicesEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListDidsEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListEquivalentIdsEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListKeyMappingsEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListSupportedMethodsEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListVerificationMethodsEndpointCommand
import com.sphereon.did.manager.rest.server.command.ListVerificationRelationshipsEndpointCommand
import com.sphereon.did.manager.rest.server.command.RefreshDidDocumentEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveAlsoKnownAsEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveControllerEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveDidServiceEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveEquivalentIdEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveKeyMappingEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveVerificationMethodEndpointCommand
import com.sphereon.did.manager.rest.server.command.RemoveVerificationRelationshipEndpointCommand
import com.sphereon.did.manager.rest.server.command.ReplaceDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.ResolveDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.TrackExternalDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.UpdateDidEndpointCommand
import com.sphereon.did.manager.rest.server.command.UpdateDidServiceEndpointCommand
import com.sphereon.did.manager.rest.server.command.UpdateVerificationMethodEndpointCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Verifies every endpoint descriptor on the IDK-21 DID Manager surface matches the expected
 * method/path/operationId. Cheap guard against drift when the endpoint command files are
 * hand-edited. Paths are RELATIVE to the adapter mount basePath `/api/dids/v1`.
 */
class DidManagerEndpointDescriptorsTest {
    private fun assertDescriptor(
        descriptor: HttpEndpointDescriptor,
        expectedMethod: HttpMethod,
        expectedPath: String,
        expectedOperationId: String,
    ) {
        assertEquals(expectedMethod, descriptor.method, "method mismatch for $expectedOperationId")
        assertEquals(expectedPath, descriptor.pathPattern, "path mismatch for $expectedOperationId")
        assertEquals(expectedOperationId, descriptor.operationId, "operationId mismatch for $expectedOperationId")
        assertNotNull(descriptor.tags, "tags should be populated for $expectedOperationId")
    }

    // ========== DID lifecycle (9) ==========

    @Test
    fun createDidDescriptor() = assertDescriptor(CreateDidEndpointCommand.ENDPOINT, HttpMethod.POST, "/dids", "createDid")

    @Test
    fun listDidsDescriptor() = assertDescriptor(ListDidsEndpointCommand.ENDPOINT, HttpMethod.GET, "/dids", "listDids")

    @Test
    fun trackExternalDidDescriptor() = assertDescriptor(TrackExternalDidEndpointCommand.ENDPOINT, HttpMethod.POST, "/dids/external", "trackExternalDid")

    @Test
    fun getDidDescriptor() = assertDescriptor(GetDidEndpointCommand.ENDPOINT, HttpMethod.GET, "/dids/{did}", "getDid")

    @Test
    fun updateDidDescriptor() = assertDescriptor(UpdateDidEndpointCommand.ENDPOINT, HttpMethod.PATCH, "/dids/{did}", "updateDid")

    @Test
    fun replaceDidDescriptor() = assertDescriptor(ReplaceDidEndpointCommand.ENDPOINT, HttpMethod.PUT, "/dids/{did}", "replaceDid")

    @Test
    fun deleteDidDescriptor() = assertDescriptor(DeleteDidEndpointCommand.ENDPOINT, HttpMethod.DELETE, "/dids/{did}", "deleteDid")

    @Test
    fun deactivateDidDescriptor() =
        assertDescriptor(
            DeactivateDidEndpointCommand.ENDPOINT,
            HttpMethod.POST,
            "/dids/{did}/actions/deactivate",
            "deactivateDid",
        )

    @Test
    fun resolveDidDescriptor() = assertDescriptor(ResolveDidEndpointCommand.ENDPOINT, HttpMethod.GET, "/dids/{did}/resolve", "resolveDid")

    // ========== Verification methods (5) ==========

    @Test
    fun listVerificationMethodsDescriptor() =
        assertDescriptor(
            ListVerificationMethodsEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/verification-methods",
            "listVerificationMethods",
        )

    @Test
    fun addVerificationMethodDescriptor() =
        assertDescriptor(
            AddVerificationMethodEndpointCommand.ENDPOINT,
            HttpMethod.POST,
            "/dids/{did}/verification-methods",
            "addVerificationMethod",
        )

    @Test
    fun getVerificationMethodDescriptor() =
        assertDescriptor(
            GetVerificationMethodEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/verification-methods/{methodId}",
            "getVerificationMethod",
        )

    @Test
    fun updateVerificationMethodDescriptor() =
        assertDescriptor(
            UpdateVerificationMethodEndpointCommand.ENDPOINT,
            HttpMethod.PATCH,
            "/dids/{did}/verification-methods/{methodId}",
            "updateVerificationMethod",
        )

    @Test
    fun removeVerificationMethodDescriptor() =
        assertDescriptor(
            RemoveVerificationMethodEndpointCommand.ENDPOINT,
            HttpMethod.DELETE,
            "/dids/{did}/verification-methods/{methodId}",
            "removeVerificationMethod",
        )

    // ========== Verification relationships (1 read; mutations covered by separate file) ==========

    @Test
    fun listVerificationRelationshipsDescriptor() =
        assertDescriptor(
            ListVerificationRelationshipsEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/verification-relationships",
            "listVerificationRelationships",
        )

    @Test
    fun addVerificationRelationshipDescriptor() =
        assertDescriptor(
            AddVerificationRelationshipEndpointCommand.ENDPOINT,
            HttpMethod.POST,
            "/dids/{did}/verification-relationships",
            "addVerificationRelationship",
        )

    @Test
    fun removeVerificationRelationshipDescriptor() =
        assertDescriptor(
            RemoveVerificationRelationshipEndpointCommand.ENDPOINT,
            HttpMethod.DELETE,
            "/dids/{did}/verification-relationships/{relationshipId}",
            "removeVerificationRelationship",
        )

    // ========== Services (5) ==========

    @Test
    fun listDidServicesDescriptor() =
        assertDescriptor(
            ListDidServicesEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/services",
            "listDidServices",
        )

    @Test
    fun addDidServiceDescriptor() =
        assertDescriptor(
            AddDidServiceEndpointCommand.ENDPOINT,
            HttpMethod.POST,
            "/dids/{did}/services",
            "addDidService",
        )

    @Test
    fun getDidServiceDescriptor() =
        assertDescriptor(
            GetDidServiceEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/services/{serviceId}",
            "getDidService",
        )

    @Test
    fun updateDidServiceDescriptor() =
        assertDescriptor(
            UpdateDidServiceEndpointCommand.ENDPOINT,
            HttpMethod.PATCH,
            "/dids/{did}/services/{serviceId}",
            "updateDidService",
        )

    @Test
    fun removeDidServiceDescriptor() =
        assertDescriptor(
            RemoveDidServiceEndpointCommand.ENDPOINT,
            HttpMethod.DELETE,
            "/dids/{did}/services/{serviceId}",
            "removeDidService",
        )

    // ========== Key mappings (3) ==========

    @Test
    fun listKeyMappingsDescriptor() =
        assertDescriptor(
            ListKeyMappingsEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/key-mappings",
            "listKeyMappings",
        )

    @Test
    fun addKeyMappingDescriptor() =
        assertDescriptor(
            AddKeyMappingEndpointCommand.ENDPOINT,
            HttpMethod.POST,
            "/dids/{did}/key-mappings",
            "addKeyMapping",
        )

    @Test
    fun removeKeyMappingDescriptor() =
        assertDescriptor(
            RemoveKeyMappingEndpointCommand.ENDPOINT,
            HttpMethod.DELETE,
            "/dids/{did}/key-mappings/{mappingId}",
            "removeKeyMapping",
        )

    // ========== Controllers (3) ==========

    @Test
    fun listControllersDescriptor() =
        assertDescriptor(
            ListControllersEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/controllers",
            "listControllers",
        )

    @Test
    fun addControllerDescriptor() =
        assertDescriptor(
            AddControllerEndpointCommand.ENDPOINT,
            HttpMethod.POST,
            "/dids/{did}/controllers",
            "addController",
        )

    @Test
    fun removeControllerDescriptor() =
        assertDescriptor(
            RemoveControllerEndpointCommand.ENDPOINT,
            HttpMethod.DELETE,
            "/dids/{did}/controllers/{controllerId}",
            "removeController",
        )

    // ========== AlsoKnownAs (3) ==========

    @Test
    fun listAlsoKnownAsDescriptor() =
        assertDescriptor(
            ListAlsoKnownAsEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/also-known-as",
            "listAlsoKnownAs",
        )

    @Test
    fun addAlsoKnownAsDescriptor() =
        assertDescriptor(
            AddAlsoKnownAsEndpointCommand.ENDPOINT,
            HttpMethod.POST,
            "/dids/{did}/also-known-as",
            "addAlsoKnownAs",
        )

    @Test
    fun removeAlsoKnownAsDescriptor() =
        assertDescriptor(
            RemoveAlsoKnownAsEndpointCommand.ENDPOINT,
            HttpMethod.DELETE,
            "/dids/{did}/also-known-as/{akaId}",
            "removeAlsoKnownAs",
        )

    // ========== Equivalent identifiers (3) ==========

    @Test
    fun listEquivalentIdsDescriptor() =
        assertDescriptor(
            ListEquivalentIdsEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/equivalent-ids",
            "listEquivalentIds",
        )

    @Test
    fun addEquivalentIdDescriptor() =
        assertDescriptor(
            AddEquivalentIdEndpointCommand.ENDPOINT,
            HttpMethod.POST,
            "/dids/{did}/equivalent-ids",
            "addEquivalentId",
        )

    @Test
    fun removeEquivalentIdDescriptor() =
        assertDescriptor(
            RemoveEquivalentIdEndpointCommand.ENDPOINT,
            HttpMethod.DELETE,
            "/dids/{did}/equivalent-ids/{equivalentId}",
            "removeEquivalentId",
        )

    // ========== Document cache (3) ==========

    @Test
    fun getDidDocumentDescriptor() =
        assertDescriptor(
            GetDidDocumentEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/dids/{did}/document",
            "getDidDocument",
        )

    @Test
    fun refreshDidDocumentDescriptor() =
        assertDescriptor(
            RefreshDidDocumentEndpointCommand.ENDPOINT,
            HttpMethod.POST,
            "/dids/{did}/document/refresh",
            "refreshDidDocument",
        )

    @Test
    fun invalidateDidDocumentDescriptor() =
        assertDescriptor(
            InvalidateDidDocumentEndpointCommand.ENDPOINT,
            HttpMethod.DELETE,
            "/dids/{did}/document/cache",
            "invalidateDidDocument",
        )

    // ========== Capabilities (3) ==========

    @Test
    fun listSupportedMethodsDescriptor() =
        assertDescriptor(
            ListSupportedMethodsEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/methods",
            "listSupportedMethods",
        )

    @Test
    fun getMethodCapabilitiesDescriptor() =
        assertDescriptor(
            GetMethodCapabilitiesEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/methods/{method}/capabilities",
            "getMethodCapabilities",
        )

    @Test
    fun getMethodCapabilitySummaryDescriptor() =
        assertDescriptor(
            GetMethodCapabilitySummaryEndpointCommand.ENDPOINT,
            HttpMethod.GET,
            "/methods/{method}/capabilities/summary",
            "getMethodCapabilitySummary",
        )
}
