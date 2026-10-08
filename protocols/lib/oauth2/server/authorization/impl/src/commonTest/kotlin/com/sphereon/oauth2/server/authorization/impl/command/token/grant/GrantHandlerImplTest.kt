/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.token.GrantHandlerKeys
import kotlin.test.Test
import kotlin.test.assertEquals

class GrantHandlerImplTest {
    @Test
    fun keyedHandlerBindingsUseTheCanonicalGrantWireValues() {
        assertEquals(
            mapOf(
                GrantType.AUTHORIZATION_CODE to GrantHandlerKeys.AUTHORIZATION_CODE,
                GrantType.REFRESH_TOKEN to GrantHandlerKeys.REFRESH_TOKEN,
                GrantType.CLIENT_CREDENTIALS to GrantHandlerKeys.CLIENT_CREDENTIALS,
                GrantType.PASSWORD to GrantHandlerKeys.PASSWORD,
                GrantType.PRE_AUTHORIZED_CODE to GrantHandlerKeys.PRE_AUTHORIZED_CODE,
                GrantType.TOKEN_EXCHANGE to GrantHandlerKeys.TOKEN_EXCHANGE,
                GrantType.DEVICE_CODE to GrantHandlerKeys.DEVICE_CODE,
            ),
            GrantType.entries.associateWith { it.value },
        )
    }
}
