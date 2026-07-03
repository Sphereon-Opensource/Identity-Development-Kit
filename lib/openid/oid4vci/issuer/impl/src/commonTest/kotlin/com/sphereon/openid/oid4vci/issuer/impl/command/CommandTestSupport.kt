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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.PropertySource
import com.sphereon.core.api.conf.PropertySources
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.IdkScope
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.core.events.Event
import com.sphereon.core.events.EventBuilder
import com.sphereon.core.events.EventHub
import com.sphereon.core.events.SessionEventService
import com.sphereon.core.events.UserEventService
import com.sphereon.core.events.impl.DefaultEventBuilder
import com.sphereon.core.events.impl.EventHubImpl
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.issuer.bridge.AugmentAsMetadataArgs
import com.sphereon.openid.oid4vci.issuer.bridge.AuthorizationContextRef
import com.sphereon.openid.oid4vci.issuer.bridge.ConsumePreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ConsumedPreAuthCode
import com.sphereon.openid.oid4vci.issuer.bridge.CreateAuthContextArgs
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.RegisterPreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.RegisteredPreAuthCode
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.CredentialOfferStore
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialEntry
import com.sphereon.openid.oid4vci.issuer.store.DeferredCredentialStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

internal class NoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "test-log"
    override val isEnabled: Boolean = false
    override val scope: IdkScope = IdkScope.SESSION
    override val logManager: SessionLogManager get() = throw NotImplementedError("not needed for pipeline tests")

    override suspend fun setConfig(config: LoggerConfig): LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("not needed for pipeline tests")
}

internal class TestAppConfigService(
    private val properties: Map<String, String> = emptyMap(),
) : AppConfigService {
    override val configLevel: ConfigLevel = ConfigLevel.APP
    override val level: ConfigLevel = ConfigLevel.APP
    override val parent: ConfigService? = null

    override fun addPropertySource(source: PropertySource<*>): ConfigService = this

    override fun removePropertySource(source: PropertySource<*>): ConfigService = this

    override fun getActiveProfile(): String = "test"

    override fun getAppName(): String = "oid4vci-issuer-test"

    override fun getConfigLocation(): Path = Path(".")

    override fun getPropertySources(includeParents: Boolean): PropertySources = throw NotImplementedError("not needed for pipeline tests")

    override fun containsProperty(key: String): Boolean = properties.containsKey(key)

    override fun <T : Any> getProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T? = defaultValue

    override fun getPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String? = properties[key] ?: defaultValue

    override fun <T : Any> getRequiredProperty(
        key: String,
        targetType: KClass<T>,
        defaultValue: T?,
    ): T = defaultValue ?: throw IllegalStateException("No value for $key")

    override fun getRequiredPropertyAsString(
        key: String,
        defaultValue: String?,
    ): String = properties[key] ?: defaultValue ?: throw IllegalStateException("No value for $key")

    override fun getAllProperties(): Map<String, Any> = properties

    override fun getAllPropertiesAsString(redact: Boolean): Map<String, String> = properties

    override fun getSubProperties(
        prefixes: Set<String>,
        stripPrefix: Boolean,
    ): Map<String, Any> = emptyMap()

    override fun getSubPropertiesAsString(
        prefixes: Set<String>,
        stripPrefix: Boolean,
        redact: Boolean,
    ): Map<String, String> = emptyMap()

    override fun getNamespace(): String = "test"
}

internal class NoOpContextConfig(
    override val app: AppConfigService = TestAppConfigService(),
) : ContextConfig {
    override val tenant: TenantConfigService get() = throw NotImplementedError("not needed for pipeline tests")
    override val principal: PrincipalConfigService get() = throw NotImplementedError("not needed for pipeline tests")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("not needed for pipeline tests")
}

internal class TestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
    appConfig: AppConfigService = TestAppConfigService(),
) : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = throw NotImplementedError("not needed for pipeline tests")
    override val log: SessionLogService = NoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = NoOpContextConfig(appConfig)
}

/** Records all sessions passed to create/update for post-execute inspection. */
internal class RecordingSessionStore : CredentialIssuanceSessionStore {
    val created = mutableListOf<IssuanceSession>()
    val updated = mutableListOf<IssuanceSession>()

    override suspend fun create(session: IssuanceSession): IdkResult<IssuanceSession, IdkError> {
        created += session
        return Ok(session)
    }

    override suspend fun get(sessionId: String): IdkResult<IssuanceSession?, IdkError> = Ok(created.firstOrNull { it.sessionId == sessionId })

    override suspend fun getByIssuerState(state: String): IdkResult<IssuanceSession?, IdkError> = Ok(created.firstOrNull { it.issuerState == state })

    override suspend fun findByCredentialConfigurationId(configId: String): IdkResult<IssuanceSession?, IdkError> = Ok(created.firstOrNull { configId in it.credentialConfigurationIds })

    override suspend fun update(session: IssuanceSession): IdkResult<IssuanceSession, IdkError> {
        updated += session
        return Ok(session)
    }
}

/** Records all entries passed to create/update for post-execute inspection. */
internal class RecordingDeferredStore : DeferredCredentialStore {
    val created = mutableListOf<DeferredCredentialEntry>()
    val updated = mutableListOf<DeferredCredentialEntry>()

    override suspend fun create(entry: DeferredCredentialEntry): IdkResult<DeferredCredentialEntry, IdkError> {
        created += entry
        return Ok(entry)
    }

    override suspend fun get(transactionId: String): IdkResult<DeferredCredentialEntry?, IdkError> = Ok(created.firstOrNull { it.transactionId == transactionId })

    override suspend fun update(entry: DeferredCredentialEntry): IdkResult<DeferredCredentialEntry, IdkError> {
        updated += entry
        return Ok(entry)
    }
}

/** Records every event passed to [emit] so tests can assert on type and count. */
internal class RecordingSessionEventService : SessionEventService {
    val emitted = mutableListOf<Event>()
    private val hub = EventHubImpl()

    override val scope: IdkScope = IdkScope.SESSION
    override val eventHub: EventHub = hub
    override val parent: UserEventService get() = throw NotImplementedError("not needed for recording tests")
    override val sessionContext: SessionContext = NoOpSessionContext

    override suspend fun emit(event: Event) {
        emitted += event
        hub.publish(event)
    }

    override suspend fun emit(
        event: Event,
        sign: Boolean,
        encrypt: Boolean,
        keyAlias: String?,
        encryptionKeyAlias: String?,
        encryptParts: Set<com.sphereon.core.events.EncryptedPart>,
    ) {
        emitted += event
        hub.publish(event)
    }

    override fun eventBuilder(): EventBuilder = DefaultEventBuilder(IdkScope.SESSION)
}

internal class NoOpOfferStore : CredentialOfferStore {
    override suspend fun store(
        offerId: String,
        offer: CredentialOffer,
        ttlSeconds: Long,
        sessionId: String?,
    ): IdkResult<Unit, IdkError> = Ok(Unit)

    override suspend fun get(offerId: String): IdkResult<CredentialOffer?, IdkError> = Ok(null)

    override suspend fun getSessionId(offerId: String): IdkResult<String?, IdkError> = Ok(null)

    override suspend fun delete(offerId: String): IdkResult<Boolean, IdkError> = Ok(false)
}

/** Minimal AS bridge for tests that do not exercise the bridge layer. */
internal class NoOpAsBridge : Oid4vciAuthorizationServerBridge {
    override suspend fun registerPreAuthorizedCode(args: RegisterPreAuthCodeArgs): IdkResult<RegisteredPreAuthCode, IdkError> = Ok(RegisteredPreAuthCode(code = "pre-auth-code", txCode = null))

    override suspend fun createAuthorizationContext(args: CreateAuthContextArgs): IdkResult<AuthorizationContextRef, IdkError> =
        Ok(AuthorizationContextRef(issuerState = args.issuerState, sessionId = args.issuerState))

    override suspend fun consumePreAuthorizedCode(args: ConsumePreAuthCodeArgs): IdkResult<ConsumedPreAuthCode, IdkError> = throw UnsupportedOperationException("not used in support fakes")

    override suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError> = throw UnsupportedOperationException("not used in support fakes")

    override suspend fun augmentAsMetadata(args: AugmentAsMetadataArgs): IdkResult<JsonObject, IdkError> = throw UnsupportedOperationException("not used in support fakes")
}
