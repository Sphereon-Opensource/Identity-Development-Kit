/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputAuthority
import com.sphereon.wallet.interaction.impl.InMemoryWalletInteractionPrivateSessionStore
import com.sphereon.wallet.interaction.impl.StoreBackedWalletInteractionSensitiveInputAuthority

internal fun integrationSensitiveInputAuthority(): WalletInteractionSensitiveInputAuthority =
    StoreBackedWalletInteractionSensitiveInputAuthority(InMemoryWalletInteractionPrivateSessionStore())
