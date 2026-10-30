package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.data.store.credential.design.PublishedVctReader
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.config.CredentialSigningConfig
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.VctTypeMetadataProvider
import com.sphereon.openid.oid4vci.issuer.impl.http.TestSessionExecution
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfig
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import com.sphereon.sdjwt.vc.SdJwtVcTypeMetadata
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A tenant that only hosts metadata has no issuer configuration; its published types must be served without touching it. */
class GetVctTypeMetadataPublishedFallbackTest {
    private val published = """{"vct":"https://host.example/public/schema/vct/adl-achievement","name":"ADL"}"""

    private val noIssuerTypes = object : VctTypeMetadataProvider {
        override suspend fun resolve(vct: String): SdJwtVcTypeMetadata? = error("Issuer configuration is missing for this tenant")
        override suspend fun listVcts(): List<String> = error("Issuer configuration is missing for this tenant")
    }

    private val noIssuer = object : Oid4vciIssuerConfigProvider {
        override val issuerIdentifier: String get() = error("no issuer")
        override val credentialConfigurations: Map<String, CredentialConfigurationSupported> get() = error("no issuer")
        override val authorizationServers: List<String>? get() = error("no issuer")
        override val display: List<DisplayProperties>? get() = error("no issuer")
        override suspend fun credentialSigningConfigs(): Map<String, CredentialSigningConfig> = error("no issuer")
        override suspend fun prepare() = error("no issuer")
    }

    private val noRest = object : Oid4vciRestConfigProvider {
        override fun getConfig(): Oid4vciRestConfig = error("no issuer")
    }

    private val noUrls = object : Oid4vciIssuerPublicUrlResolver {
        override suspend fun resolve(request: GenericHttpRequest, issuerConfigProvider: Oid4vciIssuerConfigProvider,
            restConfigProvider: Oid4vciRestConfigProvider): IdkResult<Oid4vciIssuerPublicUrls, IdkError> = error("no issuer")
    }

    private fun command(reader: PublishedVctReader?) =
        GetVctTypeMetadataEndpointCommandImpl(TestSessionExecution(), noIssuerTypes, noIssuer, noRest, noUrls, reader)

    private fun get(id: String) = GenericHttpRequest(method = "GET", path = "/$id", pathParameters = mapOf("vctId" to id))

    @Test
    fun servesThePublishedProjectionAsStoredWithoutIssuerConfiguration() = runTest {
        val reader = object : PublishedVctReader {
            override suspend fun read(vctId: String): String? = published.takeIf { vctId == "adl-achievement" }
        }
        val response = command(reader).execute(get("adl-achievement")).getOrElse { error(it.toString()) }

        assertEquals(200, response.statusCode)
        assertEquals(published, response.body)
    }
}
