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

package com.sphereon.statuslist.impl.config

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.statuslist.spi.StatusListSigningKeyNameResolver
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import com.sphereon.statuslist.spi.StatusListSigner
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.impl.driver.InMemoryStatusListDriver
import com.sphereon.statuslist.impl.driver.InMemoryStatusListStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where a configured status list's signing key may come from.
 *
 * A deployment that binds a [StatusListSigningKeyNameResolver] owns the signing key of every list.
 * The raw `signingKeyAlias` property must then not even be read, so a value planted in tenant
 * configuration cannot reach a definition, the store, or a signer.
 */
class ConfigDrivenStatusListDefinitionsProviderTest {
    private val baseProperties =
        mapOf<String, Any>(
            "statuslists.ids" to "revocation",
            "statuslists.[revocation].uri" to "https://issuer.example/statuslists/revocation",
            "statuslists.[revocation].spec" to "token_status_list",
            "statuslists.[revocation].signingKeyAlias" to "attacker-chosen-alias",
        )

    @Test
    fun withoutAResolverTheConfiguredSigningKeyIsTheOnlySource() {
        val config = RecordingPrincipalConfigService(baseProperties)
        val provider = ConfigDrivenStatusListDefinitionsProvider(
            execution = TestSessionExecution(config),
            statusListDriver = { error("status-list driver is not used by this test") },
        )

        val definition = assertNotNull(provider.byId("revocation"))

        assertEquals("attacker-chosen-alias", definition.signingKeyAlias)
    }

    @Test
    fun aConfiguredSigningKeyAliasIsIgnoredWhileAResolverIsBound() {
        val config = RecordingPrincipalConfigService(baseProperties)
        val provider =
            ConfigDrivenStatusListDefinitionsProvider(
                execution = TestSessionExecution(config),
                signingKeyNameResolver = { RefusingSigningKeyNameResolver },
                statusListDriver = { error("status-list driver is not used by this test") },
            )

        val definition = assertNotNull(provider.byId("revocation"))

        assertNull(definition.signingKeyAlias, "the deployment owns the signing key; configuration must not supply one")
        assertTrue(
            config.requestedKeys.none { it.endsWith(".signingKeyAlias") },
            "the raw alias property must not even be read: ${config.requestedKeys}",
        )
    }

    @Test
    fun aDefinitionNeverCarriesTheCorrelationIdAsItsSigningKey() {
        val config =
            RecordingPrincipalConfigService(
                mapOf(
                    "statuslists.ids" to "revocation",
                    "statuslists.[revocation].uri" to "https://issuer.example/statuslists/revocation",
                ),
            )
        val provider = ConfigDrivenStatusListDefinitionsProvider(
            execution = TestSessionExecution(config),
            statusListDriver = { error("status-list driver is not used by this test") },
        )

        val definition = assertNotNull(provider.byId("revocation"))

        assertNull(definition.signingKeyAlias)
    }

    @Test
    fun resolveFallsBackToTenantPersistedDriverDefinition() = runTest {
        val config = RecordingPrincipalConfigService(emptyMap())
        val execution = TestSessionExecution(config)
        val driver =
            InMemoryStatusListDriver(
                InMemoryStatusListStore(),
                object : StatusListSigner {
                    override suspend fun signStatusListToken(args: SignStatusListTokenArgs) =
                        Ok(StatusListToken("signed", args.proofFormat.contentType))
                },
                execution,
            )
        val args =
            CreateStatusListArgs(
                correlationId = "rest-created",
                spec = StatusListSpec.TOKEN_STATUS_LIST,
                purposes = listOf(StatusPurpose.REVOCATION),
                proofFormat = StatusProofFormat.CWT,
                issuer = "https://issuer.example",
                statusListUri = "https://issuer.example/public/statuslists/rest-created",
                length = 8,
                validUntil = Instant.parse("2030-01-01T00:00:00Z"),
                mdocProfile = com.sphereon.statuslist.MdocStatusListProfile.STATUS_LIST,
            )
        assertTrue(driver.createStatusList(args).isOk)

        val provider =
            ConfigDrivenStatusListDefinitionsProvider(
                execution = execution,
                statusListDriver = { driver },
            )
        val resolved = provider.resolve("rest-created").getOrElse { error("unexpected resolution failure: $it") }

        assertEquals("rest-created", resolved?.correlationId)
        assertEquals(StatusListSpec.TOKEN_STATUS_LIST, resolved?.spec)
        assertEquals(args.validUntil, resolved?.validUntil)
        assertEquals(args.mdocProfile, resolved?.mdocProfile)
    }
}

private object RefusingSigningKeyNameResolver : StatusListSigningKeyNameResolver {
    override suspend fun resolveSigningKeyName(
        tenantId: String,
        statusListId: String,
    ): String? = null
}

/** In-memory principal config that records which keys the provider asked for. */
private class RecordingPrincipalConfigService(
    private val properties: Map<String, Any>,
) : PrincipalConfigService {
    val requestedKeys = mutableListOf<String>()

    override val parent: TenantConfigService
        get() = error("parent not used in this test")

    override val configLevel: ConfigLevel = ConfigLevel.PRINCIPAL

    override fun addPropertySource(source: PropertySource<*>): Nothing = error("not used")

    override fun removePropertySource(source: PropertySource<*>): Nothing = error("not used")

    override fun getActiveProfile(): String = "test"

    override fun getAppName(): String = "test-app"

    override fun getConfigLocation() = error("not used")

    override fun getPropertySources(includeParents: Boolean) = error("not used")

    @Suppress("DEPRECATION")
    override fun getNamespace(): String = ""

    override fun containsProperty(key: String): Boolean {
        requestedKeys += key
        return properties.containsKey(key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? {
        requestedKeys += key
        val raw = properties[key] ?: return defaultValue
        if (targetType.isInstance(raw)) return raw as T
        return if (targetType == String::class) raw.toString() as T else defaultValue
    }

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String? {
        requestedKeys += key
        return properties[key]?.toString() ?: defaultValue
    }

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T = getProperty(key, targetType, defaultValue) ?: error("Missing required property $key")

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String = getPropertyAsString(key, defaultValue) ?: error("Missing required property $key")

    override fun getAllProperties(): Map<String, Any> = properties.toMap()

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = properties.mapValues { it.value.toString() }

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> {
        val matched = mutableMapOf<String, Any>()
        for (prefix in prefixes) {
            for ((key, value) in properties) {
                if (!(key.startsWith("$prefix.") || key == prefix)) continue
                matched[if (stripPrefix) key.removePrefix("$prefix.") else key] = value
            }
        }
        return matched
    }

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> = getSubProperties(prefixes, stripPrefix).mapValues { it.value.toString() }
}

/** Minimal [SessionExecution]: the definitions provider consults only [conf]. */
private class TestSessionExecution(
    principal: PrincipalConfigService,
) : SessionExecution {
    override val sessionContext: SessionContext = NoOpSessionContext
    override val sessionContextManager: SessionContextManager
        get() = error("sessionContextManager not used in this test")
    override val log: SessionLogService
        get() = error("log not used in this test")
    override val conf: ContextConfig = TestContextConfig(principal)
}

private class TestContextConfig(
    override val principal: PrincipalConfigService,
) : ContextConfig {
    override val app: AppConfigService get() = error("app config not used in this test")
    override val tenant: TenantConfigService get() = error("tenant config not used in this test")

    override fun conf(level: ConfigLevel): ConfigService =
        when (level) {
            ConfigLevel.PRINCIPAL -> principal
            else -> error("only PRINCIPAL config is exercised by the status-list definitions provider")
        }
}
