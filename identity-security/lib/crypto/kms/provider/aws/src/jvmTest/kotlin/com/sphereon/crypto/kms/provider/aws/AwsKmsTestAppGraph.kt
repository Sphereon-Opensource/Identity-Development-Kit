/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.provider.aws

import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.core.api.conf.DefaultSyncConfigSnapshotCache
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.crypto.core.kms.KmsProvider
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import com.sphereon.crypto.core.kms.KmsProviderConfigBinder
import com.sphereon.crypto.core.kms.KmsProviderManager
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.RootScopeProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

/**
 * App graph for this module's tests. Declaring it here lets Metro merge the AWS KMS provider
 * factory together with the core config, binder and provider-manager contributions, so tests
 * resolve AWS providers from `kms.providers.<id>.*` exactly like a running application does.
 */
@DependencyGraph(AppScope::class)
abstract class AwsKmsTestAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides @Named("version") version: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): AwsKmsTestAppGraph
    }
}

/**
 * Resolves AWS KMS providers from runtime configuration through the composed graph.
 *
 * Properties are added to the app-level map source; anything already supplied by another source
 * (environment, properties files) is left untouched, so runtime configuration always wins over
 * the defaults a test supplies.
 */
class AwsKmsTestConfig(
    application: Any,
) {
    val app: AwsKmsTestAppGraph =
        createGraphFactory<AwsKmsTestAppGraph.Factory>()
            .create(
                application = application,
                appId = application::class.simpleName ?: "aws-kms-test",
                profile = "test",
                version = "1.0.0",
                rootScopeProvider = DefaultRootScopeProvider(),
            ).also {
                it.initRootScopeProvider()
                // Creating the factory registers the aws_kms config serializer. The KMS config binder captures
                // the serializers registered when it is created, so this must happen before it is resolved.
                (it as AwsKmsProviderFactoryImpl.Graph).awsKmsProvider
            }

    val configService: ConfigService = (app as AppConfigService.Graph).appConfigService
    val execution: SessionExecution =
        app.userContextManager
            .getAnonymous()
            .sessionContextManager
            .getAnonymous()
            .asCoreApiServiceGraph()
            .serviceExecution

    private val binder = (app as KmsProviderConfigBinder.Graph).kmsProviderConfigBinder
    private val manager = (app as KmsProviderManager.Graph).kmsProviderManager
    private val addedKeys = mutableListOf<String>()

    fun isConfigured(key: String): Boolean = configService.containsProperty(key)

    fun putIfAbsent(vararg properties: Pair<String, Any>) {
        properties
            .filterNot { (key, _) -> isConfigured(key) }
            .forEach { (key, value) ->
                DefaultAppMapPropertySource.addProperty(key, value)
                addedKeys += key
            }
        clearSnapshotCache()
    }

    fun providerConfig(providerId: String): KmsProviderConfigBase = binder.getKmsProviderConfig(configService, providerId)

    fun createProvider(providerId: String): KmsProvider = manager.createFromProviderConfig(providerConfig(providerId), execution)

    fun reset() {
        addedKeys.forEach(DefaultAppMapPropertySource::deleteProperty)
        addedKeys.clear()
        clearSnapshotCache()
    }

    private fun clearSnapshotCache() {
        (app as DefaultSyncConfigSnapshotCache.Graph).syncConfigSnapshotCache.clear()
    }
}
