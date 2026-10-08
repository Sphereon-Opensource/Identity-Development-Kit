/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.openid.oid4vp.holder.VerifierInfo
import com.sphereon.wallet.interaction.WalletCounterpartyClientAuthentication

/**
 * The client identity that OID4VP request resolution authenticated, split into its client
 * identifier prefix and bare identifier for trust resolution. Null when the holder could not
 * validate the client_id, so an unauthenticated identifier never reaches a trust decision. The
 * certificate chain is the Request Object `x5c` the holder validated for an X.509 prefix.
 */
fun VerifierInfo.toWalletCounterpartyClientAuthentication(): WalletCounterpartyClientAuthentication? {
    if (!clientIdValid) return null
    val prefix = clientIdScheme.prefix
    return WalletCounterpartyClientAuthentication(
        scheme = prefix,
        identifier = prefix?.let { clientId.removePrefix("$it:") } ?: clientId,
        certificateChain = requestObjectCertificateChain,
    )
}
