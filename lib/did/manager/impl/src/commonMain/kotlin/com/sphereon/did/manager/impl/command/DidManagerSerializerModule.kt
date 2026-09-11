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

import com.sphereon.core.api.codec.CommandSerializerEntry
import com.sphereon.did.capabilities.DidMethodCapabilities
import com.sphereon.did.manager.DidFilter
import com.sphereon.did.manager.ManagedDid
import com.sphereon.did.manager.MethodCapabilitySummary
import com.sphereon.did.manager.VerificationRelationship
import com.sphereon.did.manager.command.AddVerificationRelationshipInput
import com.sphereon.did.manager.command.AlsoKnownAsListResponse
import com.sphereon.did.manager.command.ControllerListResponse
import com.sphereon.did.manager.command.CreateAlsoKnownAsInput
import com.sphereon.did.manager.command.CreateControllerInput
import com.sphereon.did.manager.command.CreateDidInput
import com.sphereon.did.manager.command.CreateDidServiceInput
import com.sphereon.did.manager.command.CreateEquivalentIdInput
import com.sphereon.did.manager.command.CreateKeyMappingInput
import com.sphereon.did.manager.command.CreateVerificationMethodInput
import com.sphereon.did.manager.command.DeactivateDidInput
import com.sphereon.did.manager.command.DeleteAlsoKnownAsInput
import com.sphereon.did.manager.command.DeleteAlsoKnownAsOutput
import com.sphereon.did.manager.command.DeleteControllerInput
import com.sphereon.did.manager.command.DeleteControllerOutput
import com.sphereon.did.manager.command.DeleteDidOutput
import com.sphereon.did.manager.command.DeleteDidServiceOutput
import com.sphereon.did.manager.command.DeleteEquivalentIdInput
import com.sphereon.did.manager.command.DeleteEquivalentIdOutput
import com.sphereon.did.manager.command.DeleteKeyMappingInput
import com.sphereon.did.manager.command.DeleteKeyMappingOutput
import com.sphereon.did.manager.command.DeleteVerificationMethodOutput
import com.sphereon.did.manager.command.Did
import com.sphereon.did.manager.command.DidAlsoKnownAsView
import com.sphereon.did.manager.command.DidControllerView
import com.sphereon.did.manager.command.DidEquivalentIdView
import com.sphereon.did.manager.command.DidIdInput
import com.sphereon.did.manager.command.DidServiceListResponse
import com.sphereon.did.manager.command.EquivalentIdListResponse
import com.sphereon.did.manager.command.GetDidInput
import com.sphereon.did.manager.command.GetDidServiceInput
import com.sphereon.did.manager.command.GetVerificationMethodInput
import com.sphereon.did.manager.command.KeyMappingListResponse
import com.sphereon.did.manager.command.KeyMappingResponse
import com.sphereon.did.manager.command.ListDidsOutput
import com.sphereon.did.manager.command.ListVerificationRelationshipsInput
import com.sphereon.did.manager.command.MethodCapabilityListResponse
import com.sphereon.did.manager.command.MethodInput
import com.sphereon.did.manager.command.RemoveDidServiceInput
import com.sphereon.did.manager.command.RemoveVerificationMethodInput
import com.sphereon.did.manager.command.RemoveVerificationRelationshipInput
import com.sphereon.did.manager.command.RemoveVerificationRelationshipOutput
import com.sphereon.did.manager.command.ReplaceDidInput
import com.sphereon.did.manager.command.ResolveDidInput
import com.sphereon.did.manager.command.TrackExternalDidInput
import com.sphereon.did.manager.command.UpdateDidInput
import com.sphereon.did.manager.command.UpdateDidServiceInput
import com.sphereon.did.manager.command.UpdateVerificationMethodInput
import com.sphereon.did.manager.command.VerificationMethodListResponse
import com.sphereon.did.manager.command.VerificationMethodResponse
import com.sphereon.did.manager.command.VerificationRelationshipListResponse
import com.sphereon.did.models.DidDocument
import com.sphereon.did.models.DidService
import com.sphereon.did.resolver.DidResolutionResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

/**
 * Serializer entries for DID manager service commands served through the binary transport.
 *
 * The JSON streaming codec resolves command input/output serializers from this compile-time
 * registry. The root command payload types all need explicit entries; nested DTOs are covered
 * by their containing root serializers.
 */
@ContributesTo(AppScope::class)
interface DidManagerSerializerModule {
    // DID lifecycle command roots
    @Provides
    @IntoSet
    fun provideDidIdInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidIdInput::class, DidIdInput.serializer())

    @Provides
    @IntoSet
    fun provideCreateDidInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CreateDidInput::class, CreateDidInput.serializer())

    @Provides
    @IntoSet
    fun provideDidFilterSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidFilter::class, DidFilter.serializer())

    @Provides
    @IntoSet
    fun provideTrackExternalDidInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(TrackExternalDidInput::class, TrackExternalDidInput.serializer())

    @Provides
    @IntoSet
    fun provideGetDidInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(GetDidInput::class, GetDidInput.serializer())

    @Provides
    @IntoSet
    fun provideUpdateDidInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(UpdateDidInput::class, UpdateDidInput.serializer())

    @Provides
    @IntoSet
    fun provideReplaceDidInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(ReplaceDidInput::class, ReplaceDidInput.serializer())

    @Provides
    @IntoSet
    fun provideDeactivateDidInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeactivateDidInput::class, DeactivateDidInput.serializer())

    @Provides
    @IntoSet
    fun provideResolveDidInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(ResolveDidInput::class, ResolveDidInput.serializer())

    @Provides
    @IntoSet
    fun provideManagedDidSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(ManagedDid::class, ManagedDid.serializer())

    @Provides
    @IntoSet
    fun provideDidSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(Did::class, Did.serializer())

    @Provides
    @IntoSet
    fun provideListDidsOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(ListDidsOutput::class, ListDidsOutput.serializer())

    @Provides
    @IntoSet
    fun provideDeleteDidOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteDidOutput::class, DeleteDidOutput.serializer())

    @Provides
    @IntoSet
    fun provideDidResolutionResultSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidResolutionResult::class, DidResolutionResult.serializer())

    // Verification-method command roots
    @Provides
    @IntoSet
    fun provideCreateVerificationMethodInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CreateVerificationMethodInput::class, CreateVerificationMethodInput.serializer())

    @Provides
    @IntoSet
    fun provideGetVerificationMethodInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(GetVerificationMethodInput::class, GetVerificationMethodInput.serializer())

    @Provides
    @IntoSet
    fun provideRemoveVerificationMethodInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(RemoveVerificationMethodInput::class, RemoveVerificationMethodInput.serializer())

    @Provides
    @IntoSet
    fun provideUpdateVerificationMethodInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(UpdateVerificationMethodInput::class, UpdateVerificationMethodInput.serializer())

    @Provides
    @IntoSet
    fun provideVerificationMethodResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(VerificationMethodResponse::class, VerificationMethodResponse.serializer())

    @Provides
    @IntoSet
    fun provideVerificationMethodListResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(VerificationMethodListResponse::class, VerificationMethodListResponse.serializer())

    @Provides
    @IntoSet
    fun provideDeleteVerificationMethodOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteVerificationMethodOutput::class, DeleteVerificationMethodOutput.serializer())

    // DID service command roots
    @Provides
    @IntoSet
    fun provideCreateDidServiceInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CreateDidServiceInput::class, CreateDidServiceInput.serializer())

    @Provides
    @IntoSet
    fun provideGetDidServiceInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(GetDidServiceInput::class, GetDidServiceInput.serializer())

    @Provides
    @IntoSet
    fun provideRemoveDidServiceInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(RemoveDidServiceInput::class, RemoveDidServiceInput.serializer())

    @Provides
    @IntoSet
    fun provideUpdateDidServiceInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(UpdateDidServiceInput::class, UpdateDidServiceInput.serializer())

    @Provides
    @IntoSet
    fun provideDidServiceSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidService::class, DidService.serializer())

    @Provides
    @IntoSet
    fun provideDidServiceListResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidServiceListResponse::class, DidServiceListResponse.serializer())

    @Provides
    @IntoSet
    fun provideDeleteDidServiceOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteDidServiceOutput::class, DeleteDidServiceOutput.serializer())

    // Key mapping command roots
    @Provides
    @IntoSet
    fun provideCreateKeyMappingInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CreateKeyMappingInput::class, CreateKeyMappingInput.serializer())

    @Provides
    @IntoSet
    fun provideDeleteKeyMappingInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteKeyMappingInput::class, DeleteKeyMappingInput.serializer())

    @Provides
    @IntoSet
    fun provideKeyMappingResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(KeyMappingResponse::class, KeyMappingResponse.serializer())

    @Provides
    @IntoSet
    fun provideKeyMappingListResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(KeyMappingListResponse::class, KeyMappingListResponse.serializer())

    @Provides
    @IntoSet
    fun provideDeleteKeyMappingOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteKeyMappingOutput::class, DeleteKeyMappingOutput.serializer())

    // Controller command roots
    @Provides
    @IntoSet
    fun provideCreateControllerInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CreateControllerInput::class, CreateControllerInput.serializer())

    @Provides
    @IntoSet
    fun provideDeleteControllerInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteControllerInput::class, DeleteControllerInput.serializer())

    @Provides
    @IntoSet
    fun provideDidControllerViewSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidControllerView::class, DidControllerView.serializer())

    @Provides
    @IntoSet
    fun provideControllerListResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(ControllerListResponse::class, ControllerListResponse.serializer())

    @Provides
    @IntoSet
    fun provideDeleteControllerOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteControllerOutput::class, DeleteControllerOutput.serializer())

    // Also-known-as command roots
    @Provides
    @IntoSet
    fun provideCreateAlsoKnownAsInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CreateAlsoKnownAsInput::class, CreateAlsoKnownAsInput.serializer())

    @Provides
    @IntoSet
    fun provideDeleteAlsoKnownAsInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteAlsoKnownAsInput::class, DeleteAlsoKnownAsInput.serializer())

    @Provides
    @IntoSet
    fun provideDidAlsoKnownAsViewSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidAlsoKnownAsView::class, DidAlsoKnownAsView.serializer())

    @Provides
    @IntoSet
    fun provideAlsoKnownAsListResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(AlsoKnownAsListResponse::class, AlsoKnownAsListResponse.serializer())

    @Provides
    @IntoSet
    fun provideDeleteAlsoKnownAsOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteAlsoKnownAsOutput::class, DeleteAlsoKnownAsOutput.serializer())

    // Equivalent-id command roots
    @Provides
    @IntoSet
    fun provideCreateEquivalentIdInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(CreateEquivalentIdInput::class, CreateEquivalentIdInput.serializer())

    @Provides
    @IntoSet
    fun provideDeleteEquivalentIdInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteEquivalentIdInput::class, DeleteEquivalentIdInput.serializer())

    @Provides
    @IntoSet
    fun provideDidEquivalentIdViewSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidEquivalentIdView::class, DidEquivalentIdView.serializer())

    @Provides
    @IntoSet
    fun provideEquivalentIdListResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(EquivalentIdListResponse::class, EquivalentIdListResponse.serializer())

    @Provides
    @IntoSet
    fun provideDeleteEquivalentIdOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DeleteEquivalentIdOutput::class, DeleteEquivalentIdOutput.serializer())

    // Verification-relationship command roots
    @Provides
    @IntoSet
    fun provideListVerificationRelationshipsInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(ListVerificationRelationshipsInput::class, ListVerificationRelationshipsInput.serializer())

    @Provides
    @IntoSet
    fun provideAddVerificationRelationshipInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(AddVerificationRelationshipInput::class, AddVerificationRelationshipInput.serializer())

    @Provides
    @IntoSet
    fun provideRemoveVerificationRelationshipInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(RemoveVerificationRelationshipInput::class, RemoveVerificationRelationshipInput.serializer())

    @Provides
    @IntoSet
    fun provideVerificationRelationshipSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(VerificationRelationship::class, VerificationRelationship.serializer())

    @Provides
    @IntoSet
    fun provideVerificationRelationshipListResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(VerificationRelationshipListResponse::class, VerificationRelationshipListResponse.serializer())

    @Provides
    @IntoSet
    fun provideRemoveVerificationRelationshipOutputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(RemoveVerificationRelationshipOutput::class, RemoveVerificationRelationshipOutput.serializer())

    // Document and capability command roots
    @Provides
    @IntoSet
    fun provideDidDocumentSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidDocument::class, DidDocument.serializer())

    @Provides
    @IntoSet
    fun provideMethodInputSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(MethodInput::class, MethodInput.serializer())

    @Provides
    @IntoSet
    fun provideMethodCapabilityListResponseSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(MethodCapabilityListResponse::class, MethodCapabilityListResponse.serializer())

    @Provides
    @IntoSet
    fun provideDidMethodCapabilitiesSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(DidMethodCapabilities::class, DidMethodCapabilities.serializer())

    @Provides
    @IntoSet
    fun provideMethodCapabilitySummarySerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(MethodCapabilitySummary::class, MethodCapabilitySummary.serializer())
}
