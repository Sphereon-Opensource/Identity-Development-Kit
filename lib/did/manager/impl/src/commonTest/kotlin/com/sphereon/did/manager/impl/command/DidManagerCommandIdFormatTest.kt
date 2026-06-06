/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.did.manager.impl.command

import com.sphereon.core.api.session.isValidCommandId
import com.sphereon.did.manager.command.AddAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.AddControllerServiceCommand
import com.sphereon.did.manager.command.AddDidServiceServiceCommand
import com.sphereon.did.manager.command.AddEquivalentIdServiceCommand
import com.sphereon.did.manager.command.AddKeyMappingServiceCommand
import com.sphereon.did.manager.command.AddVerificationMethodServiceCommand
import com.sphereon.did.manager.command.AddVerificationRelationshipServiceCommand
import com.sphereon.did.manager.command.CreateDidServiceCommand
import com.sphereon.did.manager.command.DeactivateDidServiceCommand
import com.sphereon.did.manager.command.DeleteDidServiceCommand
import com.sphereon.did.manager.command.GetCachedDidDocumentServiceCommand
import com.sphereon.did.manager.command.GetDidServiceCommand
import com.sphereon.did.manager.command.GetDidServiceServiceCommand
import com.sphereon.did.manager.command.GetMethodCapabilitiesServiceCommand
import com.sphereon.did.manager.command.GetMethodCapabilitySummaryServiceCommand
import com.sphereon.did.manager.command.GetVerificationMethodServiceCommand
import com.sphereon.did.manager.command.InvalidateDidDocumentServiceCommand
import com.sphereon.did.manager.command.ListAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.ListControllersServiceCommand
import com.sphereon.did.manager.command.ListDidServicesServiceCommand
import com.sphereon.did.manager.command.ListDidsServiceCommand
import com.sphereon.did.manager.command.ListEquivalentIdsServiceCommand
import com.sphereon.did.manager.command.ListKeyMappingsServiceCommand
import com.sphereon.did.manager.command.ListSupportedMethodsServiceCommand
import com.sphereon.did.manager.command.ListVerificationMethodsServiceCommand
import com.sphereon.did.manager.command.ListVerificationRelationshipsServiceCommand
import com.sphereon.did.manager.command.RemoveAlsoKnownAsServiceCommand
import com.sphereon.did.manager.command.RemoveControllerServiceCommand
import com.sphereon.did.manager.command.RemoveDidServiceServiceCommand
import com.sphereon.did.manager.command.RemoveEquivalentIdServiceCommand
import com.sphereon.did.manager.command.RemoveKeyMappingServiceCommand
import com.sphereon.did.manager.command.RemoveVerificationMethodServiceCommand
import com.sphereon.did.manager.command.RemoveVerificationRelationshipServiceCommand
import com.sphereon.did.manager.command.ReplaceDidServiceCommand
import com.sphereon.did.manager.command.ResolveAndCacheDidServiceCommand
import com.sphereon.did.manager.command.ResolveDidServiceCommand
import com.sphereon.did.manager.command.TrackExternalDidServiceCommand
import com.sphereon.did.manager.command.UpdateDidServiceCommand
import com.sphereon.did.manager.command.UpdateDidServiceServiceCommand
import com.sphereon.did.manager.command.UpdateVerificationMethodServiceCommand
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guardrail: every DID-manager `COMMAND_ID` constant must validate against the platform
 * 3-segment regex (`{module}.{service}.{command}` — see `CommandIdValidation.kt`).
 *
 * Background: the descriptors module wires commands via Metro's `@IntoMap @StringKey(...)`
 * multibinding, which keys directly into a `Map<String, ServiceCommand<*, *, *>>` and bypasses
 * `CommandRegistry.register()` (where the `require(isValidCommandId(...))` check lives).
 * Without this test, malformed IDs (4- or 5-segment) routed silently — e.g. `did.manager.did.create`
 * and `did.manager.method.capabilities.get` shipped on the IDK-21 surface and would have stayed
 * undetected if a reviewer hadn't spotted them by eye.
 *
 * Update both this list and `DidManagerCommandDescriptors` together when adding a new command.
 */
class DidManagerCommandIdFormatTest {
    @Test
    fun all_DidManager_command_ids_have_three_dotted_segments() {
        val ids =
            listOf(
                CreateDidServiceCommand.COMMAND_ID,
                ListDidsServiceCommand.COMMAND_ID,
                TrackExternalDidServiceCommand.COMMAND_ID,
                GetDidServiceCommand.COMMAND_ID,
                UpdateDidServiceCommand.COMMAND_ID,
                ReplaceDidServiceCommand.COMMAND_ID,
                DeleteDidServiceCommand.COMMAND_ID,
                DeactivateDidServiceCommand.COMMAND_ID,
                ResolveDidServiceCommand.COMMAND_ID,
                ListVerificationMethodsServiceCommand.COMMAND_ID,
                AddVerificationMethodServiceCommand.COMMAND_ID,
                GetVerificationMethodServiceCommand.COMMAND_ID,
                UpdateVerificationMethodServiceCommand.COMMAND_ID,
                RemoveVerificationMethodServiceCommand.COMMAND_ID,
                ListVerificationRelationshipsServiceCommand.COMMAND_ID,
                AddVerificationRelationshipServiceCommand.COMMAND_ID,
                RemoveVerificationRelationshipServiceCommand.COMMAND_ID,
                ListDidServicesServiceCommand.COMMAND_ID,
                AddDidServiceServiceCommand.COMMAND_ID,
                GetDidServiceServiceCommand.COMMAND_ID,
                UpdateDidServiceServiceCommand.COMMAND_ID,
                RemoveDidServiceServiceCommand.COMMAND_ID,
                ListKeyMappingsServiceCommand.COMMAND_ID,
                AddKeyMappingServiceCommand.COMMAND_ID,
                RemoveKeyMappingServiceCommand.COMMAND_ID,
                ListControllersServiceCommand.COMMAND_ID,
                AddControllerServiceCommand.COMMAND_ID,
                RemoveControllerServiceCommand.COMMAND_ID,
                ListAlsoKnownAsServiceCommand.COMMAND_ID,
                AddAlsoKnownAsServiceCommand.COMMAND_ID,
                RemoveAlsoKnownAsServiceCommand.COMMAND_ID,
                ListEquivalentIdsServiceCommand.COMMAND_ID,
                AddEquivalentIdServiceCommand.COMMAND_ID,
                RemoveEquivalentIdServiceCommand.COMMAND_ID,
                GetCachedDidDocumentServiceCommand.COMMAND_ID,
                ResolveAndCacheDidServiceCommand.COMMAND_ID,
                InvalidateDidDocumentServiceCommand.COMMAND_ID,
                ListSupportedMethodsServiceCommand.COMMAND_ID,
                GetMethodCapabilitiesServiceCommand.COMMAND_ID,
                GetMethodCapabilitySummaryServiceCommand.COMMAND_ID,
            )

        val invalid = ids.filterNot { isValidCommandId(it) }
        assertTrue(
            invalid.isEmpty(),
            "Command IDs do not match {module}.{service}.{command} format: $invalid",
        )

        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(
            duplicates.isEmpty(),
            "Duplicate command IDs across DID-manager surface: $duplicates",
        )
    }
}
