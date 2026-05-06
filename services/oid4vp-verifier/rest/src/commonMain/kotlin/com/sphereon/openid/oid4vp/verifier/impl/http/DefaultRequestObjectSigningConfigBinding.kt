package com.sphereon.openid.oid4vp.verifier.impl.http

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.verifier.impl.ConfigDrivenRequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default binding for [RequestObjectSigningConfig] in the OID4VP verifier service.
 *
 * Delegates to [ConfigDrivenRequestObjectSigningConfig] which reads signing settings
 * from the config service. When signing is not configured, it defaults to disabled.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestObjectSigningConfig>(), replaces = [ConfigDrivenRequestObjectSigningConfig::class])
class DefaultRequestObjectSigningConfigBinding
    @Inject
    constructor(
        private val configDriven: ConfigDrivenRequestObjectSigningConfig,
    ) : RequestObjectSigningConfig {
        override val enabled: Boolean get() = configDriven.enabled
        override val includeIss: Boolean get() = configDriven.includeIss
        override val audience: String get() = configDriven.audience
        override val expirationSeconds: Long get() = configDriven.expirationSeconds

        override suspend fun resolveSigningKey(): KeyInfoType<*> = configDriven.resolveSigningKey()

        override suspend fun resolveSignerBinding(scheme: ClientIdScheme?): VerifierSignerBinding? = configDriven.resolveSignerBinding(scheme)
    }
