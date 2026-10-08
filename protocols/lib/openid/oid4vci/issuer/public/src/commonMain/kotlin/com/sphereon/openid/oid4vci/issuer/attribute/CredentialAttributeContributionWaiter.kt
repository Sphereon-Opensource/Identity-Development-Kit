/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vci.issuer.attribute

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding

/**
 * Neutral issuer-side seam for waiting until one contributor source has delivered its
 * contribution. The issuer command owns timeout and re-contribution policy; implementations own
 * only notification/wakeup mechanics. EDK extends this seam with its existing callback
 * coordinator without making IDK depend on EDK.
 */
@JsExportCompat
fun interface CredentialAttributeContributionWaiter {
    @JsExportIgnoreCompat
    suspend fun awaitContribution(
        correlationId: String,
        contributorId: String,
    )
}

/** Pure-IDK default for the optional EDK-backed contribution waiter. */
@ContributesTo(AppScope::class)
interface CredentialAttributeContributionWaiterOptionalProvider {
    @OptionalBinding
    val optionalCredentialAttributeContributionWaiter: CredentialAttributeContributionWaiter? get() = null
}
