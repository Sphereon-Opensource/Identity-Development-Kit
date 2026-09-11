/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vci.issuer.ktor

import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.impl.http.Oid4vciIssuerMetadataHttpAdapter
import com.sphereon.openid.oid4vci.issuer.impl.http.Oid4vciIssuerMetadataLegacyPrefixHttpAdapter
import com.sphereon.openid.oid4vci.issuer.impl.http.Oid4vciIssuerProtocolHttpAdapter
import com.sphereon.openid.oid4vci.issuer.impl.http.VctHostingHttpAdapter
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferEndpointCommand
import com.sphereon.openid.oid4vci.rest.impl.Oid4vciRestHttpAdapter
import com.sphereon.openid.oid4vci.rest.impl.command.CreateCredentialOfferEndpointCommandImpl
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

/**
 * Default HTTP ownership for the standalone IDK issuer application.
 *
 * The reusable OID4VCI REST artifact intentionally contributes none of these keys. Enterprise
 * assemblies can therefore own the same stable route identities with tenant-aware implementations
 * without replacement or exclusion topology.
 */
@ContributesTo(SessionScope::class)
interface StandaloneOid4vciHttpBindings {
    @Provides
    @IntoMap
    @StringKey(Oid4vciRestHttpAdapter.ID)
    fun backendAdapter(value: Oid4vciRestHttpAdapter): HttpAdapter = value

    @Provides
    @IntoMap
    @StringKey(VctHostingHttpAdapter.ID)
    fun vctHostingAdapter(value: VctHostingHttpAdapter): HttpAdapter = value

    @Provides
    @IntoMap
    @StringKey(Oid4vciIssuerProtocolHttpAdapter.ID)
    fun protocolAdapter(value: Oid4vciIssuerProtocolHttpAdapter): HttpAdapter = value

    @Provides
    @IntoMap
    @StringKey(Oid4vciIssuerMetadataHttpAdapter.ID)
    fun metadataAdapter(value: Oid4vciIssuerMetadataHttpAdapter): HttpAdapter = value

    @Provides
    @IntoMap
    @StringKey(Oid4vciIssuerMetadataLegacyPrefixHttpAdapter.ID)
    fun legacyMetadataAdapter(value: Oid4vciIssuerMetadataLegacyPrefixHttpAdapter): HttpAdapter = value

    @Provides
    @IntoMap
    @StringKey(CreateCredentialOfferEndpointCommand.COMMAND_ID)
    fun createCredentialOfferEndpoint(value: CreateCredentialOfferEndpointCommandImpl): HttpEndpointCommand = value
}
