package com.sphereon.oauth2.client.command

// Re-export from common for backward compatibility
// These commands are now in oauth2-common since they're used by both clients and servers (especially Resource Servers)
@Deprecated(
    "Use com.sphereon.oauth2.common.command.IntrospectTokenCommand instead",
    ReplaceWith("com.sphereon.oauth2.common.command.IntrospectTokenCommand")
)
typealias IntrospectTokenCommand = com.sphereon.oauth2.common.command.IntrospectTokenCommand
