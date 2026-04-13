package com.sphereon.oauth2.client.command

// Re-export from common for backward compatibility
// This command is now in oauth2-common since it's used by both clients and servers
@Deprecated(
    "Use com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand instead",
    ReplaceWith("com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand")
)
typealias ApplyClientAuthenticationCommand = com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
