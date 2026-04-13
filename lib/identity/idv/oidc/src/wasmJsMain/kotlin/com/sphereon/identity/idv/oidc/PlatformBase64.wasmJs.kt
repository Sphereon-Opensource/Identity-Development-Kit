package com.sphereon.identity.idv.oidc

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal actual fun platformBase64Decode(value: String): String {
    return Base64.decode(value).decodeToString()
}
