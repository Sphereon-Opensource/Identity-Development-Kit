package com.sphereon.identity.idv.oidc

import java.util.Base64

internal actual fun platformBase64Decode(value: String): String =
    String(Base64.getDecoder().decode(value))
