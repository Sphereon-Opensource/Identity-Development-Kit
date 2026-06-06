/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.di.context.MutableResolvedTenantIdProvider

internal fun testTenantIdProvider(): MutableResolvedTenantIdProvider = TestMutableResolvedTenantIdProvider()

private class TestMutableResolvedTenantIdProvider : MutableResolvedTenantIdProvider {
    private var currentTenantId: String? = null

    override fun currentTenantId(): String? = currentTenantId

    override fun setCurrentTenantId(tenantId: String) {
        currentTenantId = tenantId
    }

    override fun clearCurrentTenantId() {
        currentTenantId = null
    }
}
