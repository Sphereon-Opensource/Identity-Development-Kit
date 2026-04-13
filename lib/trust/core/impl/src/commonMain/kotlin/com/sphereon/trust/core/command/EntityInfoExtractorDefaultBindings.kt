/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.di.session.SessionScope
import com.sphereon.trust.core.EntityInfoExtractor
import com.sphereon.trust.core.model.DiscoveredEntityInfo
import com.sphereon.trust.core.model.EntityDiscoveryOptions
import com.sphereon.trust.core.model.TrustContext
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

/**
 * Provides a no-op EntityInfoExtractor to ensure the multibinding Set is always available,
 * even when no actual extractor modules are on the classpath.
 */
@ContributesTo(SessionScope::class)
interface EntityInfoExtractorDefaultBindings {
    @Provides @IntoSet
    fun noOpExtractor(): EntityInfoExtractor = NoOpEntityInfoExtractor
}

private object NoOpEntityInfoExtractor : EntityInfoExtractor {
    override val supportedContextTypes: Set<String> = emptySet()

    override fun supports(context: TrustContext): Boolean = false

    override suspend fun extractEntityInfo(
        context: TrustContext,
        validationPath: List<String>,
        options: EntityDiscoveryOptions,
    ): List<DiscoveredEntityInfo> = emptyList()
}
