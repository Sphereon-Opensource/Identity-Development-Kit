/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.catalog.eu.impl.resolution

import com.sphereon.catalog.eu.error.CatalogError
import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.model.AttributeDataType
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.AttributeInformation
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.EaaType
import com.sphereon.catalog.eu.model.InternationalNames
import com.sphereon.catalog.eu.model.MultiLangString
import com.sphereon.catalog.eu.model.ReferenceBody
import com.sphereon.catalog.eu.model.SchemeFormatBinding
import com.sphereon.catalog.eu.model.SchemeOwner
import com.sphereon.catalog.eu.model.SemanticDescription
import com.sphereon.catalog.eu.model.VersionStatus
import com.sphereon.catalog.eu.model.VersionedAttribute
import com.sphereon.catalog.eu.model.VersionedEaaScheme
import com.sphereon.catalog.eu.spi.CatalogAttributeMatch
import com.sphereon.catalog.eu.spi.CatalogEaaTypeMatch
import com.sphereon.catalog.eu.spi.CatalogIndexReader
import com.sphereon.catalog.eu.spi.CatalogOrigin
import com.sphereon.catalog.eu.spi.CatalogSchemeMatch
import com.sphereon.catalog.eu.spi.CatalogScope
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.extern.ExternalIdentifierDidOpts
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.context.createAnonymousSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogIdentifierResolutionServiceTest {
    private val signerBase64 =
        "MIIBlzCCAT2gAwIBAgIUV3Z+PCChgYdSskza4yidfuknWPwwCgYIKoZIzj0EAwIwIDEeMBwGA1UEAwwVVGVzdCBDYXRhbG9ndWUgU2lnbmVyMCAXDTI2MDkyOTA0MjUzMFoYDzIxMjYwOTA1MDQyNTMwWjAgMR4wHAYDVQQDDBVUZXN0IENhdGFsb2d1ZSBTaWduZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASGGcCS/XqbraVxwVRRpafDp8S9GMBv/kWRal9F9+50l+6Z3MUoO+St2URIBf1aGFHvuFqwJkOFHnaWuix/tkMpo1MwUTAdBgNVHQ4EFgQUCDEwRFiZdytsOsG9TH8+exvcK/QwHwYDVR0jBBgwFoAUCDEwRFiZdytsOsG9TH8+exvcK/QwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiB/BTDoQmEB0j6691/xkGbNnKg+shyfHl1Ncovt11ivVAIhAJeEYBbz4bEXX4jLDpTCSCqIt+pQWJ334lg0ik2pCoLS"
    private val signer = Base64.Default.decode(signerBase64)
    private val eu = "urn:eu:coa"
    private val custom = "urn:tenant:coa"

    private fun names(value: String) = InternationalNames(listOf(MultiLangString("en", value)))

    private fun attribute(
        id: String,
        registration: String? = null,
        vararg versions: String,
    ) = AttributeEntry(
        attributeIdentifier = id,
        registrationIdentifier = registration,
        referenceBody = ReferenceBody(names("body"), "https://example.org"),
        versions =
            versions.map {
                VersionedAttribute(
                    version = it,
                    status = VersionStatus("urn:status:active"),
                    information =
                        AttributeInformation(
                            name = names(id),
                            semanticDescription = SemanticDescription(names("d")),
                            dataType = AttributeDataType("string"),
                        ),
                )
            },
    )

    private val pid = EaaType(
        identifier = "pid-type",
        name = names("PID"),
        schemeDefinition = names("def"),
        dataModelReference = "https://example.org/dm",
        formatBindings =
            listOf(
                SchemeFormatBinding("dc+sd-jwt", eaaTypeIdentifier = "urn:eudi:pid:1"),
                SchemeFormatBinding("mso_mdoc", eaaTypeIdentifier = "eu.europa.ec.eudi.pid.1"),
            ),
    )

    private val pidScheme =
        EaaSchemeEntry(
            name = "PID",
            owner = SchemeOwner(names("EC"), "urn:owner"),
            registrationIdentifier = "reg-pid",
            versions =
                listOf(
                    VersionedEaaScheme(
                        version = "1.0",
                        status = VersionStatus("urn:status:active"),
                        documentUri = "https://example.org/pid",
                        eaaTypes = listOf(pid),
                    ),
                ),
        )

    /** In-memory index holding both origins; records every scope it is asked for. */
    private class MemoryReader(
        val attributes: List<CatalogAttributeMatch>,
        val schemes: List<CatalogSchemeMatch>,
        val types: List<CatalogEaaTypeMatch>,
    ) : CatalogIndexReader {
        val scopes = mutableListOf<CatalogScope>()
        val catalogueQueries = mutableListOf<String?>()

        override suspend fun findAttribute(
            scope: CatalogScope,
            namespace: String,
            attributeIdentifier: String,
            catalogueIdentifier: String?,
        ): IdkResult<CatalogAttributeMatch?, CatalogError> {
            scopes += scope
            catalogueQueries += catalogueIdentifier
            return Ok(
                attributes.firstOrNull {
                    it.namespace == namespace && it.entry.attributeIdentifier == attributeIdentifier &&
                        (catalogueIdentifier == null || it.catalogueIdentifier == catalogueIdentifier)
                },
            )
        }

        override suspend fun findAttributeByRegistrationIdentifier(
            scope: CatalogScope,
            registrationIdentifier: String,
        ): IdkResult<CatalogAttributeMatch?, CatalogError> {
            scopes += scope
            return Ok(attributes.firstOrNull { it.entry.registrationIdentifier == registrationIdentifier })
        }

        override suspend fun findScheme(
            scope: CatalogScope,
            schemeName: String,
        ): IdkResult<CatalogSchemeMatch?, CatalogError> {
            scopes += scope
            return Ok(schemes.firstOrNull { it.entry.name == schemeName })
        }

        override suspend fun findSchemeByRegistrationIdentifier(
            scope: CatalogScope,
            registrationIdentifier: String,
        ): IdkResult<CatalogSchemeMatch?, CatalogError> {
            scopes += scope
            return Ok(schemes.firstOrNull { it.entry.registrationIdentifier == registrationIdentifier })
        }

        override suspend fun findEaaTypesByFormatIdentifier(
            scope: CatalogScope,
            formatSpecificIdentifier: String,
            mediaType: String?,
        ): IdkResult<List<CatalogEaaTypeMatch>, CatalogError> {
            scopes += scope
            return Ok(
                types.filter { m ->
                    m.eaaType.formatBindings.any {
                        it.eaaTypeIdentifier == formatSpecificIdentifier && (mediaType == null || it.mediaType == mediaType)
                    }
                },
            )
        }
    }

    private class FailingReader : CatalogIndexReader {
        private fun <T> fail(): IdkResult<T, CatalogError> = Err(CatalogError(CatalogErrorCode.INDEX_UNAVAILABLE, "down"))

        override suspend fun findAttribute(scope: CatalogScope, namespace: String, attributeIdentifier: String, catalogueIdentifier: String?) = fail<CatalogAttributeMatch?>()

        override suspend fun findAttributeByRegistrationIdentifier(scope: CatalogScope, registrationIdentifier: String) = fail<CatalogAttributeMatch?>()

        override suspend fun findScheme(scope: CatalogScope, schemeName: String) = fail<CatalogSchemeMatch?>()

        override suspend fun findSchemeByRegistrationIdentifier(scope: CatalogScope, registrationIdentifier: String) = fail<CatalogSchemeMatch?>()

        override suspend fun findEaaTypesByFormatIdentifier(scope: CatalogScope, formatSpecificIdentifier: String, mediaType: String?) =
            fail<List<CatalogEaaTypeMatch>>()
    }

    private fun reader() =
        MemoryReader(
            attributes =
                listOf(
                    CatalogAttributeMatch(CatalogOrigin.SYNCED, eu, "eu.pid", attribute("family_name", "reg-fn", "1.0", "2.0"), listOf(signer)),
                    CatalogAttributeMatch(CatalogOrigin.AUTHORED, custom, "tenant.hr", attribute("badge", null, "1"), listOf(signer)),
                ),
            schemes = listOf(CatalogSchemeMatch(CatalogOrigin.SYNCED, "urn:eu:cos", pidScheme, listOf(signer))),
            types =
                listOf(
                    CatalogEaaTypeMatch(CatalogOrigin.SYNCED, "urn:eu:cos", "PID", "1.0", pid, listOf(signer)),
                    CatalogEaaTypeMatch(CatalogOrigin.AUTHORED, "urn:tenant:cos", "PID", "1.0", pid, listOf(signer)),
                ),
        )

    private fun execution(sessionId: String = "wp6") = TestSessionExecution(createAnonymousSessionContext(sessionId, "$sessionId-corr"))

    private fun opts(
        method: IIdentifierMethod,
        identifier: String,
        mediaType: String? = null,
    ) = ExternalIdentifierCatalogOpts(method, identifier, domainId = "d1", mediaType = mediaType)

    private fun attributeService(r: CatalogIndexReader?) = CatalogAttributeIdentifierResolutionService(execution(), r?.let { Provider { it } })

    private fun schemeService(r: CatalogIndexReader?) = CatalogSchemeIdentifierResolutionService(execution(), r?.let { Provider { it } })

    private fun typeService(r: CatalogIndexReader?) = CatalogCredentialTypeIdentifierResolutionService(execution(), r?.let { Provider { it } })

    @Test
    fun attributeResolvesWithOriginAndCatalogue() =
        runTest {
            val r = reader()
            val result = attributeService(r).resolve(opts(CatalogIdentifierMethod.ATTRIBUTE, "$eu#eu.pid/family_name@2.0"))
            assertTrue(result.isOk)
            val attr = assertIs<ExternalIdentifierCatalogResult.Attribute>(result.value)
            assertEquals(CatalogOrigin.SYNCED, attr.origin)
            assertEquals(eu, attr.catalogueIdentifier)
            assertEquals("2.0", attr.version)
            assertEquals(CatalogScope(execution().tenantId, "d1", null), r.scopes.single())
        }

    @Test
    fun resultsCarryTheCatalogueSignerAsX5c() =
        runTest {
            val attr = attributeService(reader()).resolve(opts(CatalogIdentifierMethod.ATTRIBUTE, "$eu#eu.pid/family_name")).value
            assertEquals(listOf(signerBase64), attr.keyInfo.x5c?.toList())
            assertEquals(1, attr.jwks.size)
            val types = typeService(reader()).resolve(opts(CatalogIdentifierMethod.CREDENTIAL_TYPE, "urn:eudi:pid:1")).value
            assertEquals(1, types.jwks.size)
        }

    @Test
    fun entryWithoutSignerEvidenceFailsClearlyInsteadOfFakingAKey() =
        runTest {
            val unsigned =
                MemoryReader(
                    attributes = listOf(CatalogAttributeMatch(CatalogOrigin.AUTHORED, custom, "tenant.hr", attribute("badge", null, "1"))),
                    schemes = emptyList(),
                    types = emptyList(),
                )
            val result = attributeService(unsigned).resolve(opts(CatalogIdentifierMethod.ATTRIBUTE, "$custom#tenant.hr/badge"))
            assertTrue(result.isErr)
            assertTrue(result.error.message.defaultMessage.contains("No signer certificate"))
        }

    @Test
    fun authoredAttributeStatesAuthoredOrigin() =
        runTest {
            val result = attributeService(reader()).resolve(opts(CatalogIdentifierMethod.ATTRIBUTE, "$custom#tenant.hr/badge"))
            assertEquals(CatalogOrigin.AUTHORED, assertIs<ExternalIdentifierCatalogResult.Attribute>(result.value).origin)
        }

    @Test
    fun attributeWithWrongCatalogueOrUnknownVersionIsNotFound() =
        runTest {
            val service = attributeService(reader())
            assertTrue(service.resolve(opts(CatalogIdentifierMethod.ATTRIBUTE, "urn:other#eu.pid/family_name")).isErr)
            assertTrue(service.resolve(opts(CatalogIdentifierMethod.ATTRIBUTE, "$eu#eu.pid/family_name@9.9")).isErr)
            assertTrue(service.resolve(opts(CatalogIdentifierMethod.ATTRIBUTE, "malformed")).isErr)
        }

    @Test
    fun attributeByRegistrationIdentifier() =
        runTest {
            val result = attributeService(reader()).resolve(opts(CatalogIdentifierMethod.ATTRIBUTE_REGISTRATION, "reg-fn"))
            assertEquals("family_name", assertIs<ExternalIdentifierCatalogResult.Attribute>(result.value).match.entry.attributeIdentifier)
        }

    @Test
    fun schemeByNameVersionAndRegistration() =
        runTest {
            val service = schemeService(reader())
            val plain = service.resolve(opts(CatalogIdentifierMethod.SCHEME, "PID"))
            assertEquals("urn:eu:cos", assertIs<ExternalIdentifierCatalogResult.Scheme>(plain.value).catalogueIdentifier)
            val versioned = service.resolve(opts(CatalogIdentifierMethod.SCHEME, "PID@1.0"))
            assertEquals("1.0", assertIs<ExternalIdentifierCatalogResult.Scheme>(versioned.value).version)
            assertTrue(service.resolve(opts(CatalogIdentifierMethod.SCHEME, "PID@7")).isErr)
            val reg = service.resolve(opts(CatalogIdentifierMethod.SCHEME_REGISTRATION, "reg-pid"))
            assertEquals(CatalogOrigin.SYNCED, assertIs<ExternalIdentifierCatalogResult.Scheme>(reg.value).origin)
        }

    @Test
    fun eaaTypeResolvesThroughSchemeVersion() =
        runTest {
            val service = schemeService(reader())
            val result = service.resolve(opts(CatalogIdentifierMethod.EAA_TYPE, "PID@1.0#pid-type"))
            val type = assertIs<ExternalIdentifierCatalogResult.EaaType>(result.value)
            assertEquals("pid-type", type.match.eaaType.identifier)
            assertEquals("urn:eu:cos", type.catalogueIdentifier)
            assertTrue(service.resolve(opts(CatalogIdentifierMethod.EAA_TYPE, "PID@2.0#pid-type")).isErr)
            assertTrue(service.resolve(opts(CatalogIdentifierMethod.EAA_TYPE, "PID#pid-type")).isErr)
        }

    @Test
    fun credentialTypeReturnsEveryOriginAndHonoursMediaType() =
        runTest {
            val service = typeService(reader())
            val all = service.resolve(opts(CatalogIdentifierMethod.CREDENTIAL_TYPE, "urn:eudi:pid:1"))
            val matches = assertIs<ExternalIdentifierCatalogResult.CredentialType>(all.value).matches
            assertEquals(setOf(CatalogOrigin.SYNCED, CatalogOrigin.AUTHORED), matches.map { it.origin }.toSet())
            assertTrue(service.resolve(opts(CatalogIdentifierMethod.CREDENTIAL_TYPE, "urn:eudi:pid:1", mediaType = "dc+sd-jwt")).isOk)
            assertTrue(service.resolve(opts(CatalogIdentifierMethod.CREDENTIAL_TYPE, "urn:eudi:pid:1", mediaType = "mso_mdoc")).isErr)
            assertTrue(service.resolve(opts(CatalogIdentifierMethod.CREDENTIAL_TYPE, "eu.europa.ec.eudi.pid.1", mediaType = "mso_mdoc")).isOk)
        }

    @Test
    fun tenantIsAlwaysTheSessionTenant() =
        runTest {
            val r = reader()
            val exec = execution("session-tenant")
            val service = CatalogAttributeIdentifierResolutionService(exec, Provider { r })
            service.resolve(opts(CatalogIdentifierMethod.ATTRIBUTE_REGISTRATION, "reg-fn"))
            service.resolve(opts(CatalogIdentifierMethod.ATTRIBUTE, "$eu#eu.pid/family_name"))
            assertEquals(listOf(exec.tenantId, exec.tenantId), r.scopes.map { it.tenantId })
            // The options carry no tenant at all, so a caller cannot redirect the lookup to another tenant.
        }

    @Test
    fun theCatalogueIdentifierIsPartOfTheIndexQuery() =
        runTest {
            val other = attribute("family_name", null, "1.0")
            val r =
                MemoryReader(
                    attributes =
                        listOf(
                            CatalogAttributeMatch(CatalogOrigin.SYNCED, "urn:other:coa", "eu.pid", other, listOf(signer)),
                            CatalogAttributeMatch(CatalogOrigin.SYNCED, eu, "eu.pid", attribute("family_name", "reg-fn", "1.0"), listOf(signer)),
                        ),
                    schemes = emptyList(),
                    types = emptyList(),
                )
            val result = attributeService(r).resolve(opts(CatalogIdentifierMethod.ATTRIBUTE, "$eu#eu.pid/family_name@1.0"))
            assertTrue(result.isOk, "an entry of another catalogue must not hide the addressed one")
            assertEquals(eu, assertIs<ExternalIdentifierCatalogResult.Attribute>(result.value).catalogueIdentifier)
            assertEquals(listOf<String?>(eu), r.catalogueQueries)
        }

    @Test
    fun absentReaderMakesEverythingUnsupported() =
        runTest {
            val o = opts(CatalogIdentifierMethod.ATTRIBUTE, "$eu#eu.pid/family_name")
            val service = attributeService(null)
            assertFalse(service.supports(o))
            assertTrue(service.resolve(o).isErr)
            assertFalse(schemeService(null).supports(opts(CatalogIdentifierMethod.SCHEME, "PID")))
            assertFalse(typeService(null).supports(opts(CatalogIdentifierMethod.CREDENTIAL_TYPE, "x")))
        }

    @Test
    fun servicesOnlySupportTheirOwnMethodsAndIgnoreForeignOpts() =
        runTest {
            val r = reader()
            assertTrue(attributeService(r).supports(opts(CatalogIdentifierMethod.ATTRIBUTE, "x")))
            assertFalse(attributeService(r).supports(opts(CatalogIdentifierMethod.SCHEME, "x")))
            assertFalse(schemeService(r).supports(opts(CatalogIdentifierMethod.CREDENTIAL_TYPE, "x")))
            val did = ExternalIdentifierDidOpts("did:key:z6Mk")
            assertFalse(typeService(r).supports(did))
            assertTrue(typeService(r).resolve(did).isErr)
            assertEquals(IdentifierMethodDefaults.DID.methodName, did.method?.methodName)
        }

    @Test
    fun indexFailureSurfacesAsError() =
        runTest {
            assertTrue(attributeService(FailingReader()).resolve(opts(CatalogIdentifierMethod.ATTRIBUTE_REGISTRATION, "reg")).isErr)
        }

    @Test
    fun blankDomainIsRejected() =
        runTest {
            val bad = ExternalIdentifierCatalogOpts(CatalogIdentifierMethod.SCHEME, "PID", domainId = " ")
            assertTrue(schemeService(reader()).resolve(bad).isErr)
        }

    @Test
    fun parserHandlesNamespacesWithSlashesAndVersions() {
        val ref = assertNotNull(CatalogIdentifierParser.parseAttribute("urn:c#https://ns.example/a/b/attr@1.2"))
        assertEquals("https://ns.example/a/b", ref.namespace)
        assertEquals("attr", ref.attributeIdentifier)
        assertEquals("1.2", ref.version)
        assertNull(CatalogIdentifierParser.parseAttribute("no-hash/attr"))
    }

    private class TestSessionExecution(
        override val sessionContext: SessionContext,
    ) : SessionExecution {
        override val sessionContextManager: SessionContextManager
            get() = throw NotImplementedError("Not needed for this test")
        override val log: SessionLogService = MockSessionLogService(sessionContext)
        override val conf: ContextConfig = NoOpContextConfig()
    }

    private class NoOpContextConfig : ContextConfig {
        override val app: AppConfigService
            get() = throw NotImplementedError("Not needed for this test")
        override val tenant: TenantConfigService
            get() = throw NotImplementedError("Not needed for this test")
        override val principal: PrincipalConfigService
            get() = throw NotImplementedError("Not needed for this test")

        override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for this test")
    }

    private class MockSessionLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : SessionLogService {
        override val logManager: SessionLogManager = MockSessionLogManager(sessionContext)
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "mock-session-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): SessionLogService = this

        override fun executeAsync(message: LogMessage) = IdkOkResult(Unit)

        override fun toAsync(): AsyncLogService = MockAsyncLogService(sessionContext)
    }

    private class MockSessionLogManager(
        private val sessionContext: SessionContext,
    ) : SessionLogManager {
        override suspend fun setGlobalConfig(config: LoggerConfig): SessionLogManager = this

        override suspend fun getGlobalConfig(): LoggerConfig = LoggerConfig.Default

        override fun withTagAsync(tag: String, config: LoggerConfig?): AsyncLogService = MockAsyncLogService(sessionContext)

        override fun withTag(tag: String, config: LoggerConfig?): SessionLogService = MockSessionLogService(sessionContext)
    }

    private class MockAsyncLogService(
        override val sessionContext: SessionContext = NoOpSessionContext,
    ) : AsyncLogService {
        override val scope: IdkScope = IdkScope.SESSION
        override val id: String = "mock-async-log"
        override val isEnabled: Boolean = false

        override suspend fun setConfig(config: LoggerConfig): AsyncLogService = this

        override suspend fun execute(args: LogMessage) = IdkOkResult(Unit)

        override fun toSync(): SessionLogService = MockSessionLogService(sessionContext)
    }
}
