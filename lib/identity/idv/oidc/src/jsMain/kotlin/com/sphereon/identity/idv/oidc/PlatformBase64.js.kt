@file:Suppress("UnsafeCastFromDynamic")

package com.sphereon.identity.idv.oidc

internal actual fun platformBase64Decode(value: String): String {
    val decoded = js("atob(value)") as String
    val bytes = decoded.encodeToByteArray()
    return bytes.decodeToString()
}
