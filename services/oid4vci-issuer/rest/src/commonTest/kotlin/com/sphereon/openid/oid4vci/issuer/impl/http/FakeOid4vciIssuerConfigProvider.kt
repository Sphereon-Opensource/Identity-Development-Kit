package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider

internal class FakeOid4vciIssuerConfigProvider(
    override val issuerIdentifier: String = "https://issuer.example.com",
    override val credentialConfigurations: Map<String, CredentialConfigurationSupported> =
        mapOf(
            "TestCred" to CredentialConfigurationSupported(format = "jwt_vc_json"),
        ),
    override val authorizationServers: List<String>? = listOf("https://auth.example.com"),
    override val display: List<DisplayProperties>? =
        listOf(
            DisplayProperties(name = "Test Issuer"),
        ),
    override val signingKey: ManagedIdentifierOptsOrResult? = null,
) : Oid4vciIssuerConfigProvider
