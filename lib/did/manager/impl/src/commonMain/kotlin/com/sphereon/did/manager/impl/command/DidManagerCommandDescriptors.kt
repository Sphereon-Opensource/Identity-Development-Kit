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

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
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
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

/**
 * `@IntoMap` provider catalog for every DID Manager `ServiceCommand`. Mirrors the KMS
 * pattern in `services-kms-rest`'s `KmsCommandDescriptors` so the
 * `SessionScopedCommandRegistry` can dispatch by command id.
 *
 * The per-impl `@ContributesBinding(SessionScope::class, binding = binding<…ServiceCommand>())`
 * annotations on each `*ServiceCommandImpl` make the typed binding available; this file
 * adds the keyed map entry the registry-driven dispatcher consumes. Without it, the
 * REST adapter resolves a route to a command id but the dispatcher fails with
 * `Unknown command: <id>`.
 *
 * Lives next to `DidManagerServiceCommandsImpl.kt` (and the lifecycle/sub-resource
 * split) under `…impl.command` because it's IDK-20 wiring scope. The DID Manager REST
 * adapter (IDK-21) does not need to know this file exists — it talks to the registry
 * via `executionScopedCommandRegistry`.
 */
@ContributesTo(SessionScope::class)
interface DidManagerCommandDescriptors {
    // ── Lifecycle ─────────────────────────────────────────────────────────────────
    @Provides @IntoMap
    @StringKey(CreateDidServiceCommand.COMMAND_ID)
    fun createDidService(impl: CreateDidServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ListDidsServiceCommand.COMMAND_ID)
    fun listDidsService(impl: ListDidsServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(TrackExternalDidServiceCommand.COMMAND_ID)
    fun trackExternalDidService(impl: TrackExternalDidServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetDidServiceCommand.COMMAND_ID)
    fun getDidService(impl: GetDidServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateDidServiceCommand.COMMAND_ID)
    fun updateDidService(impl: UpdateDidServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ReplaceDidServiceCommand.COMMAND_ID)
    fun replaceDidService(impl: ReplaceDidServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteDidServiceCommand.COMMAND_ID)
    fun deleteDidService(impl: DeleteDidServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(DeactivateDidServiceCommand.COMMAND_ID)
    fun deactivateDidService(impl: DeactivateDidServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveDidServiceCommand.COMMAND_ID)
    fun resolveDidService(impl: ResolveDidServiceCommand): ServiceCommand<*, *, *> = impl

    // ── Verification methods ─────────────────────────────────────────────────────
    @Provides @IntoMap
    @StringKey(ListVerificationMethodsServiceCommand.COMMAND_ID)
    fun listVerificationMethodsService(impl: ListVerificationMethodsServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(AddVerificationMethodServiceCommand.COMMAND_ID)
    fun addVerificationMethodService(impl: AddVerificationMethodServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetVerificationMethodServiceCommand.COMMAND_ID)
    fun getVerificationMethodService(impl: GetVerificationMethodServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateVerificationMethodServiceCommand.COMMAND_ID)
    fun updateVerificationMethodService(impl: UpdateVerificationMethodServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RemoveVerificationMethodServiceCommand.COMMAND_ID)
    fun removeVerificationMethodService(impl: RemoveVerificationMethodServiceCommand): ServiceCommand<*, *, *> = impl

    // ── Verification relationships ───────────────────────────────────────────────
    @Provides @IntoMap
    @StringKey(ListVerificationRelationshipsServiceCommand.COMMAND_ID)
    fun listVerificationRelationshipsService(impl: ListVerificationRelationshipsServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(AddVerificationRelationshipServiceCommand.COMMAND_ID)
    fun addVerificationRelationshipService(impl: AddVerificationRelationshipServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RemoveVerificationRelationshipServiceCommand.COMMAND_ID)
    fun removeVerificationRelationshipService(impl: RemoveVerificationRelationshipServiceCommand): ServiceCommand<*, *, *> = impl

    // ── Services (DID document services, not microservices) ─────────────────────
    @Provides @IntoMap
    @StringKey(ListDidServicesServiceCommand.COMMAND_ID)
    fun listDidServicesService(impl: ListDidServicesServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(AddDidServiceServiceCommand.COMMAND_ID)
    fun addDidServiceService(impl: AddDidServiceServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetDidServiceServiceCommand.COMMAND_ID)
    fun getDidServiceService(impl: GetDidServiceServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateDidServiceServiceCommand.COMMAND_ID)
    fun updateDidServiceService(impl: UpdateDidServiceServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RemoveDidServiceServiceCommand.COMMAND_ID)
    fun removeDidServiceService(impl: RemoveDidServiceServiceCommand): ServiceCommand<*, *, *> = impl

    // ── Key mappings ─────────────────────────────────────────────────────────────
    @Provides @IntoMap
    @StringKey(ListKeyMappingsServiceCommand.COMMAND_ID)
    fun listKeyMappingsService(impl: ListKeyMappingsServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(AddKeyMappingServiceCommand.COMMAND_ID)
    fun addKeyMappingService(impl: AddKeyMappingServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RemoveKeyMappingServiceCommand.COMMAND_ID)
    fun removeKeyMappingService(impl: RemoveKeyMappingServiceCommand): ServiceCommand<*, *, *> = impl

    // ── Controllers ──────────────────────────────────────────────────────────────
    @Provides @IntoMap
    @StringKey(ListControllersServiceCommand.COMMAND_ID)
    fun listControllersService(impl: ListControllersServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(AddControllerServiceCommand.COMMAND_ID)
    fun addControllerService(impl: AddControllerServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RemoveControllerServiceCommand.COMMAND_ID)
    fun removeControllerService(impl: RemoveControllerServiceCommand): ServiceCommand<*, *, *> = impl

    // ── alsoKnownAs ──────────────────────────────────────────────────────────────
    @Provides @IntoMap
    @StringKey(ListAlsoKnownAsServiceCommand.COMMAND_ID)
    fun listAlsoKnownAsService(impl: ListAlsoKnownAsServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(AddAlsoKnownAsServiceCommand.COMMAND_ID)
    fun addAlsoKnownAsService(impl: AddAlsoKnownAsServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RemoveAlsoKnownAsServiceCommand.COMMAND_ID)
    fun removeAlsoKnownAsService(impl: RemoveAlsoKnownAsServiceCommand): ServiceCommand<*, *, *> = impl

    // ── equivalentIds ────────────────────────────────────────────────────────────
    @Provides @IntoMap
    @StringKey(ListEquivalentIdsServiceCommand.COMMAND_ID)
    fun listEquivalentIdsService(impl: ListEquivalentIdsServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(AddEquivalentIdServiceCommand.COMMAND_ID)
    fun addEquivalentIdService(impl: AddEquivalentIdServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RemoveEquivalentIdServiceCommand.COMMAND_ID)
    fun removeEquivalentIdService(impl: RemoveEquivalentIdServiceCommand): ServiceCommand<*, *, *> = impl

    // ── Document cache ───────────────────────────────────────────────────────────
    @Provides @IntoMap
    @StringKey(GetCachedDidDocumentServiceCommand.COMMAND_ID)
    fun getCachedDidDocumentService(impl: GetCachedDidDocumentServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveAndCacheDidServiceCommand.COMMAND_ID)
    fun resolveAndCacheDidService(impl: ResolveAndCacheDidServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(InvalidateDidDocumentServiceCommand.COMMAND_ID)
    fun invalidateDidDocumentService(impl: InvalidateDidDocumentServiceCommand): ServiceCommand<*, *, *> = impl

    // ── Method capabilities ──────────────────────────────────────────────────────
    @Provides @IntoMap
    @StringKey(ListSupportedMethodsServiceCommand.COMMAND_ID)
    fun listSupportedMethodsService(impl: ListSupportedMethodsServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetMethodCapabilitiesServiceCommand.COMMAND_ID)
    fun getMethodCapabilitiesService(impl: GetMethodCapabilitiesServiceCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetMethodCapabilitySummaryServiceCommand.COMMAND_ID)
    fun getMethodCapabilitySummaryService(impl: GetMethodCapabilitySummaryServiceCommand): ServiceCommand<*, *, *> = impl
}
