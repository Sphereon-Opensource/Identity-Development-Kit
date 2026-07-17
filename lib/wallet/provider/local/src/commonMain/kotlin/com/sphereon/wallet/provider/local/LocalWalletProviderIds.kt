/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.provider.local

fun localWalletUnitId(profileId: String): String {
    require(profileId.isNotBlank()) { "wallet_local_profile_id_blank" }
    return "wu-$profileId"
}

fun localWalletInstanceId(profileId: String): String {
    require(profileId.isNotBlank()) { "wallet_local_profile_id_blank" }
    return "wi-$profileId"
}
