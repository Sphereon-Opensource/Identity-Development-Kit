package com.sphereon.openid.oid4vp.verifier.impl.http

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.verifier.impl.ConfigDrivenRequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.impl.config.RegistryBackedOid4vpVerifierConfigProvider
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default binding for [RequestObjectSigningConfig] in the OID4VP verifier service.
 *
 * Delegates to [RegistryBackedOid4vpVerifierConfigProvider], the instance-aware config-backed
 * provider: it reads signing settings from the active verifier namespace
 * (`oid4vp.verifiers.<id>.request-object.*` when an instance is resolved for the request, else the
 * singular `oid4vp.verifier.request-object.*`). When signing is not configured, it defaults to
 * disabled.
 *
 * Replaces BOTH the singular [ConfigDrivenRequestObjectSigningConfig] and the direct
 * [RegistryBackedOid4vpVerifierConfigProvider] binding so this wrapper is the single concrete
 * `RequestObjectSigningConfig` in a deployed verifier-rest service (all three would otherwise
 * collide as competing bindings of the same type).
 */
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<RequestObjectSigningConfig>(),
    replaces = [ConfigDrivenRequestObjectSigningConfig::class, RegistryBackedOid4vpVerifierConfigProvider::class],
)
class DefaultRequestObjectSigningConfigBinding
    @Inject
    constructor(
        private val delegate: RegistryBackedOid4vpVerifierConfigProvider,
    ) : RequestObjectSigningConfig {
        override val enabled: Boolean get() = delegate.enabled
        override val includeIss: Boolean get() = delegate.includeIss
        override val audience: String get() = delegate.audience
        override val expirationSeconds: Long get() = delegate.expirationSeconds

        override suspend fun resolveSigningKey(): KeyInfoType<*> = delegate.resolveSigningKey()

        override suspend fun resolveSignerBinding(scheme: ClientIdScheme?): VerifierSignerBinding? = delegate.resolveSignerBinding(scheme)
    }
