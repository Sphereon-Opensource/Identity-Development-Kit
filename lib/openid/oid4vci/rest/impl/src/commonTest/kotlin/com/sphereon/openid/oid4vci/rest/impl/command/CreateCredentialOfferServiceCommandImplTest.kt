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

package com.sphereon.openid.oid4vci.rest.impl.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
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
import com.sphereon.openid.oid4vc.common.DisplayProperties
import com.sphereon.openid.oid4vc.common.QrCodeOptions
import com.sphereon.openid.oid4vc.common.QrCodeService
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferCommand
import com.sphereon.openid.oid4vci.issuer.command.CreatedCredentialOffer
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.rest.CreateCredentialOfferInput
import com.sphereon.openid.oid4vci.rest.CredentialOfferSession
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStore
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfig
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Wiring test for [CreateCredentialOfferServiceCommandImpl.doExecute] that exercises the *real*
 * command with its collaborators faked, rather than a hand-typed output model. Its purpose is to
 * catch a `sessionId = correlationId` copy-paste bug at the output-construction line: the fake
 * [CreateCredentialOfferCommand] returns a protocol session id that is deliberately distinct from
 * the caller-supplied correlation id, so the two would only match if the wiring collapsed them.
 */
class CreateCredentialOfferServiceCommandImplTest {
    private val protocolSessionId = "protocol-session-distinct-from-correlation"
    private val callerCorrelationId = "caller-supplied-correlation-id"

    @Test
    fun executeReturnsCreatedSessionsProtocolSessionIdDistinctFromCorrelationId() =
        runTest {
            val createdOffer =
                CreatedCredentialOffer(
                    instanceId = "issuer-instance-wiring-test",
                    offerId = "offer-wiring-test",
                    sessionId = protocolSessionId,
                    offer =
                        CredentialOffer(
                            credentialIssuer = "https://issuer.example.com/oid4vci",
                            credentialConfigurationIds = listOf("PID"),
                        ),
                    offerUri = "openid-credential-offer://?credential_offer_uri=https://issuer.example.com/offer",
                )
            val sessionStore = RecordingCredentialOfferSessionStore()
            val sessionEventService = RecordingSessionEventService()

            val command =
                CreateCredentialOfferServiceCommandImpl(
                    execution = TestSessionExecution(),
                    createCredentialOfferCommand = FakeCreateCredentialOfferCommand(Ok(createdOffer)),
                    credentialOfferSessionStore = sessionStore,
                    qrCodeService = NoOpQrCodeService(),
                    configProvider = FixedOid4vciRestConfigProvider(),
                    issuerConfigProvider = FixedOid4vciIssuerConfigProvider(),
                    instanceIdProvider = FixedOid4vciIssuerInstanceIdProvider(),
                    sessionEventService = sessionEventService,
                )

            val result =
                command.execute(
                    CreateCredentialOfferInput(
                        credentialConfigurationIds = listOf("PID"),
                        correlationId = callerCorrelationId,
                        templateId = "issuance-template-id",
                    ),
                )

            assertTrue(result.isOk, "execute should succeed but was: $result")
            val output = result.value

            assertEquals(
                protocolSessionId,
                output.sessionId,
                "output.sessionId must be the created session's protocol session id",
            )
            assertEquals(callerCorrelationId, output.correlationId)
            assertNotEquals(
                output.correlationId,
                output.sessionId,
                "sessionId must not collapse onto correlationId",
            )

            assertEquals(1, sessionStore.created.size)
            assertEquals(protocolSessionId, sessionStore.created.first().issuanceSessionId)
            assertEquals(1, sessionEventService.emitted.size, "the real doExecute path should emit exactly one session-created event")
            val eventPayload = sessionEventService.emitted.single().payload
            assertEquals("issuance-template-id", eventPayload["templateId"]?.jsonPrimitive?.content)
            assertEquals(
                "issuance-template-id",
                eventPayload["creationSnapshot"]?.jsonObject?.get("templateId")?.jsonPrimitive?.content,
            )
        }
}

/** Fake [CreateCredentialOfferCommand] that returns a canned [CreatedCredentialOffer]. */
private class FakeCreateCredentialOfferCommand(
    private val result: IdkResult<CreatedCredentialOffer, IdkError>,
) : CreateCredentialOfferCommand {
    var lastArgs: CreateCredentialOfferArgs? = null
    override val isEnabled: Boolean = true
    override val inputTypeToken: TypeToken<CreateCredentialOfferArgs> = typeToken()
    override val outputTypeToken: TypeToken<CreatedCredentialOffer> = typeToken()

    override suspend fun execute(args: CreateCredentialOfferArgs): IdkResult<CreatedCredentialOffer, IdkError> {
        lastArgs = args
        return result
    }
}

/** Records every session passed to [create] for post-execute inspection. */
private class RecordingCredentialOfferSessionStore : CredentialOfferSessionStore {
    val created = mutableListOf<CredentialOfferSession>()

    override suspend fun create(session: CredentialOfferSession): IdkResult<CredentialOfferSession, IdkError> {
        created += session
        return Ok(session)
    }

    override suspend fun get(correlationId: String): IdkResult<CredentialOfferSession?, IdkError> = Ok(created.firstOrNull { it.correlationId == correlationId })

    override suspend fun getByOfferId(offerId: String): IdkResult<CredentialOfferSession?, IdkError> = Ok(created.firstOrNull { it.offerId == offerId })

    override suspend fun update(session: CredentialOfferSession): IdkResult<CredentialOfferSession, IdkError> = Ok(session)

    override suspend fun delete(correlationId: String): IdkResult<Boolean, IdkError> = Ok(true)
}

private class NoOpQrCodeService : QrCodeService {
    override fun generateDataUri(
        content: String,
        options: QrCodeOptions,
    ): String = "data:image/png;base64,test"
}

private class FixedOid4vciRestConfigProvider(
    private val config: Oid4vciRestConfig = Oid4vciRestConfig(externalBaseUrl = "https://issuer.example.com"),
) : Oid4vciRestConfigProvider {
    override fun getConfig(): Oid4vciRestConfig = config
}

private class FixedOid4vciIssuerConfigProvider(
    override val issuerIdentifier: String = "https://issuer.example.com/oid4vci",
) : Oid4vciIssuerConfigProvider {
    override val credentialConfigurations: Map<String, CredentialConfigurationSupported> = emptyMap()
    override val authorizationServers: List<String>? = null
    override val display: List<DisplayProperties>? = null
}

private class FixedOid4vciIssuerInstanceIdProvider(
    private val instanceId: String? = "issuer-instance-wiring-test",
) : Oid4vciIssuerInstanceIdProvider {
    override fun currentInstanceId(): String? = instanceId
}

private class NoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "wiring-test-log"
    override val isEnabled: Boolean = false
    override val scope: IdkScope = IdkScope.SESSION
    override val logManager: SessionLogManager get() = throw NotImplementedError("not needed for wiring test")

    override suspend fun setConfig(config: LoggerConfig): LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("not needed for wiring test")
}

private class NoOpContextConfig : ContextConfig {
    override val app: AppConfigService get() = throw NotImplementedError("not needed for wiring test")
    override val tenant: TenantConfigService get() = throw NotImplementedError("not needed for wiring test")
    override val principal: PrincipalConfigService get() = throw NotImplementedError("not needed for wiring test")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("not needed for wiring test")
}

private class TestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = throw NotImplementedError("not needed for wiring test")
    override val log: SessionLogService = NoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = NoOpContextConfig()
}

/** Records every event passed to [emit] so tests can assert on emission without a real event hub backend. */
private class RecordingSessionEventService : SessionEventService {
    val emitted = mutableListOf<Event>()
    private val hub = EventHubImpl()

    override val scope: IdkScope = IdkScope.SESSION
    override val eventHub: EventHub = hub
    override val parent: UserEventService get() = throw NotImplementedError("not needed for wiring test")
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
