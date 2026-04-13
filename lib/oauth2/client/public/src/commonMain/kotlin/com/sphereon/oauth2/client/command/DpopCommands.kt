package com.sphereon.oauth2.client.command

// Re-export from common for backward compatibility
// These commands are now in oauth2-common since they're used by both clients and servers
@Deprecated(
    "Use com.sphereon.oauth2.common.command.CreateDpopProofCommand instead",
    ReplaceWith("com.sphereon.oauth2.common.command.CreateDpopProofCommand")
)
typealias CreateDpopProofCommand = com.sphereon.oauth2.common.command.CreateDpopProofCommand

@Deprecated(
    "Use com.sphereon.oauth2.common.command.VerifyDpopProofCommand instead",
    ReplaceWith("com.sphereon.oauth2.common.command.VerifyDpopProofCommand")
)
typealias VerifyDpopProofCommand = com.sphereon.oauth2.common.command.VerifyDpopProofCommand
