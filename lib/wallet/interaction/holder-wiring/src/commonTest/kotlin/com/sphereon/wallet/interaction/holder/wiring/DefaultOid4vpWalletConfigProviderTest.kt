/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.wallet.interaction.holder.wiring

import com.sphereon.openid.oid4vp.holder.OID4VP_STATIC_DISCOVERY_REQUEST_OBJECT_AUDIENCE
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultOid4vpWalletConfigProviderTest {
    @Test
    fun `uses the OpenID4VP static-discovery request-object audience`() {
        assertEquals(
            OID4VP_STATIC_DISCOVERY_REQUEST_OBJECT_AUDIENCE,
            defaultOid4vpWalletConfig().audience,
        )
    }
}
