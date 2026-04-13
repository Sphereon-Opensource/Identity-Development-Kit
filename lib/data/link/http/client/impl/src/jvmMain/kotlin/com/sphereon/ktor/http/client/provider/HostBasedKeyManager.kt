package com.sphereon.ktor.http.client.provider

import java.net.Socket
import java.security.Principal
import javax.net.ssl.X509KeyManager

class HostBasedKeyManager(
    private val delegate: X509KeyManager,
    private val hostNameToAlias: Map<String, String>,
    private val defaultAlias: String? = null
) : X509KeyManager by delegate {


    override fun chooseClientAlias(
        keyType: Array<String>?,
        issuers: Array<Principal>?,
        socket: Socket?
    ): String? {
        val host = socket?.inetAddress?.hostName ?: return defaultAlias
        return hostNameToAlias[host] ?: defaultAlias
    }
}